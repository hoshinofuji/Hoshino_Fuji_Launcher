import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;

import java.awt.Desktop;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * 「当前实例」下拉列表：鼠标停在某一项上时，亮条末尾出现垃圾桶，点一下删除该实例。
 *
 * 删除内容：
 *   .minecraft/versions/<id>     版本文件（必删）
 *   instances/<id>               该实例的存档、模组等（勾选后才删）
 * 能放进废纸篓 / 回收站时优先放进去，误删还能找回来。
 * 被其他实例依赖的原版（例如 Fabric 26.2 依赖原版 26.2）不允许直接删除。
 */
final class InstanceDeleter {
    private final Scene scene;
    private final ComboBox<Minecraft.Version> versions;
    private final ListView<Minecraft.Version> instanceList;   // 隐藏的实例页列表，保持同步
    private final Method readable;

    @SuppressWarnings("unchecked")
    private InstanceDeleter(Scene scene, ComboBox<?> versions, ListView<?> list) throws ReflectiveOperationException {
        this.scene = scene;
        this.versions = (ComboBox<Minecraft.Version>) versions;
        this.instanceList = (ListView<Minecraft.Version>) list;
        this.readable = Launcher.class.getDeclaredMethod("readableVersion", Minecraft.Version.class);
        readable.setAccessible(true);
    }

    static InstanceDeleter install(Scene scene) {
        Node selector = scene.lookup("#instance-selector");
        Node list = scene.lookup("#instance-list");
        if (!(selector instanceof ComboBox<?> combo)) return null;
        try {
            InstanceDeleter d = new InstanceDeleter(scene, combo, list instanceof ListView<?> l ? l : null);
            d.versions.setCellFactory(v -> d.new Cell());
            return d;
        } catch (ReflectiveOperationException e) {
            System.err.println("InstanceDeleter: " + e);
            return null;
        }
    }

    javafx.collections.ObservableList<Minecraft.Version> versions() { return versions.getItems(); }

    String name(Minecraft.Version v) {
        try { return (String) readable.invoke(null, v); } catch (ReflectiveOperationException e) { return v.id(); }
    }

    // ---------------------------------------------------------------- 列表项

    private final class Cell extends ListCell<Minecraft.Version> {
        private final Label text = new Label();
        private final StackPane trash = trashIcon();
        private final HBox row = new HBox(text, new Region(), trash);

        Cell() {
            getStyleClass().add("instance-cell");
            HBox.setHgrow(row.getChildren().get(1), Priority.ALWAYS);
            row.setAlignment(Pos.CENTER_LEFT);
            text.getStyleClass().add("instance-cell-text");
            trash.visibleProperty().bind(hoverProperty().and(emptyProperty().not()));
            Tooltip.install(trash, new Tooltip("删除这个实例"));
            // 自己消费鼠标事件：点垃圾桶不会顺带把这一项选成当前实例
            trash.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
                e.consume();
                Minecraft.Version v = getItem();
                if (v != null) {
                    versions.hide();
                    Platform.runLater(() -> delete(List.of(v), null));
                }
            });
            trash.addEventHandler(MouseEvent.MOUSE_RELEASED, MouseEvent::consume);
            trash.addEventHandler(MouseEvent.MOUSE_CLICKED, MouseEvent::consume);
        }

        @Override protected void updateItem(Minecraft.Version v, boolean empty) {
            super.updateItem(v, empty);
            setText(null);
            if (empty || v == null) { setGraphic(null); return; }
            text.setText(name(v));
            setGraphic(row);
        }
    }

    static StackPane trashIcon() {
        SVGPath svg = new SVGPath();
        svg.setContent("M4 7 H20 M9 7 V4.5 H15 V7 M6.5 7 L7.5 20 H16.5 L17.5 7 M10 10.5 V16.5 M14 10.5 V16.5");
        svg.getStyleClass().addAll("line-icon", "trash-icon");
        StackPane box = new StackPane(svg);
        box.getStyleClass().add("trash-button");
        box.setMinSize(30, 30);
        box.setPrefSize(30, 30);
        box.setMaxSize(30, 30);
        box.setCursor(Cursor.HAND);
        svg.setScaleX(0.85);
        svg.setScaleY(0.85);
        return box;
    }

    // ---------------------------------------------------------------- 删除

    /**
     * 删除一个或多个实例：检查依赖 → 确认 → 后台移到废纸篓 → 更新列表。
     * done 在成功删除后（FX 线程）调用，可为 null。
     */
    void delete(List<Minecraft.Version> targets, Runnable done) {
        if (targets.isEmpty()) return;
        var chosen = new java.util.LinkedHashSet<>(targets);
        // 被未选中的实例依赖（inheritsFrom）就不能删
        List<String> blocked = new java.util.ArrayList<>();
        for (Minecraft.Version v : chosen) {
            List<String> users = versions.getItems().stream()
                    .filter(o -> !chosen.contains(o) && v.id().equals(inherits(o)))
                    .map(this::name).toList();
            if (!users.isEmpty()) blocked.add("「" + name(v) + "」被 " + String.join("、", users) + " 使用");
        }
        if (!blocked.isEmpty()) {
            alert(Alert.AlertType.INFORMATION, "有实例暂时不能删除",
                    String.join("\n", blocked) + "\n\n请先删除（或一起勾选）依赖它的实例。");
            return;
        }

        record Job(Minecraft.Version v, Path versionDir, Path gameDir) {}
        List<Job> jobs = new java.util.ArrayList<>();
        List<String> missing = new java.util.ArrayList<>();
        Path instances = Path.of(System.getProperty("launcher.instances", "instances")).toAbsolutePath();
        for (Minecraft.Version v : chosen) {
            Path dir;
            try { dir = versionDir(v); }
            catch (IOException e) { alert(Alert.AlertType.ERROR,"版本路径无效",e.getMessage()); return; }
            if (dir == null) missing.add(name(v));
            else {
                try { jobs.add(new Job(v,dir,SafeFiles.child(instances,v.id()))); }
                catch (IOException e) { alert(Alert.AlertType.ERROR,"实例路径无效",e.getMessage()); return; }
            }
        }
        if (jobs.isEmpty()) {
            alert(Alert.AlertType.ERROR, "找不到版本文件夹", String.join("、", missing) + "\n没有删除任何文件。");
            return;
        }
        boolean anyGameDir = jobs.stream().anyMatch(j -> Files.isDirectory(j.gameDir()));

        Alert ask = themed(new Alert(Alert.AlertType.CONFIRMATION));
        ask.setTitle("删除实例");
        ask.setHeaderText(jobs.size() == 1 ? "删除「" + name(jobs.get(0).v()) + "」？" : "删除这 " + jobs.size() + " 个实例？");
        VBox body = new VBox(12);
        if (jobs.size() == 1) {
            Label info = new Label("将删除版本文件：\n" + jobs.get(0).versionDir());
            info.setWrapText(true);
            body.getChildren().add(info);
        } else {
            List<String> names = jobs.stream().map(j -> "·  " + name(j.v())).toList();
            Label list = new Label(String.join("\n", names.size() > 12
                    ? java.util.stream.Stream.concat(names.subList(0, 11).stream(), Stream.of("·  …以及另外 " + (names.size() - 11) + " 个")).toList()
                    : names));
            body.getChildren().add(list);
        }
        if (!missing.isEmpty()) {
            Label skip = new Label("找不到版本文件夹，将跳过：" + String.join("、", missing));
            skip.setWrapText(true);
            skip.getStyleClass().add("status");
            body.getChildren().add(skip);
        }
        CheckBox alsoGame = new CheckBox(jobs.size() == 1
                ? "同时删除这个实例的存档、模组和设置\n" + jobs.get(0).gameDir()
                : "同时删除这些实例的存档、模组和设置（instances 文件夹里对应的目录）");
        alsoGame.setWrapText(true);
        if (anyGameDir) body.getChildren().add(alsoGame);
        Label trashHint = new Label(canTrash() ? "文件会移到废纸篓 / 回收站，需要时还能找回。" : "系统回收站不可用，文件会保留，请自行打开目录管理。");
        trashHint.getStyleClass().add("status");
        body.getChildren().add(trashHint);
        body.setPadding(new Insets(4, 0, 0, 0));
        body.setPrefWidth(470);
        ask.getDialogPane().setContent(body);
        ButtonType delete = new ButtonType(jobs.size() == 1 ? "删除" : "删除 " + jobs.size() + " 个", ButtonBar.ButtonData.OK_DONE);
        ask.getButtonTypes().setAll(new ButtonType("取消", ButtonBar.ButtonData.CANCEL_CLOSE), delete);
        ask.getDialogPane().lookupButton(delete).getStyleClass().add("danger");
        ask.getDialogPane().lookupButton(delete).setDisable(!canTrash());
        Optional<ButtonType> answer = ask.showAndWait();
        if (answer.isEmpty() || answer.get() != delete) return;

        boolean removeGame = anyGameDir && alsoGame.isSelected();
        status("正在删除 " + jobs.size() + " 个实例…");
        List<Minecraft.Version> inventory = List.copyOf(versions.getItems());
        Thread worker = new Thread(() -> {
            List<Minecraft.Version> removed = new java.util.ArrayList<>();
            List<String> failed = new java.util.ArrayList<>();
            // 先删依赖别人的（加载器版本），再删原版
            List<Job> ordered = new java.util.ArrayList<>(jobs);
            ordered.sort(Comparator.comparing(j -> inherits(j.v()) == null));
            for (Job j : ordered) {
                try (var lease = InstanceAccess.open(j.gameDir())) {
                    if (inventory.stream().anyMatch(o -> j.v().id().equals(inherits(o)) && !removed.contains(o)))
                        throw new IOException("依赖此版本的实例尚未删除，已保留基础版本");
                    // Trash user data first. On failure the launch profile remains usable.
                    if (removeGame) remove(j.gameDir());
                    remove(j.versionDir());
                    removed.add(j.v());
                } catch (IOException | RuntimeException e) {
                    failed.add(name(j.v()) + "：" + e.getMessage());
                }
            }
            Platform.runLater(() -> {
                removed.forEach(this::forget);
                if (failed.isEmpty()) {
                    status(removed.size() == 1 ? "已删除 " + name(removed.get(0)) : "已删除 " + removed.size() + " 个实例");
                } else {
                    status("有 " + failed.size() + " 个实例删除失败");
                    alert(Alert.AlertType.ERROR, "部分实例删除失败", String.join("\n", failed));
                }
                if (done != null) done.run();
            });
        }, "delete-instances");
        worker.setDaemon(true);
        worker.start();
    }

    /** versions/<id>，并确认里面确实是这个版本（有 <id>.json）。 */
    private static Path versionDir(Minecraft.Version v) throws IOException {
        Path client = v.client();
        if (client == null || client.getParent() == null || client.getParent().getParent() == null) return null;
        Path dir = SafeFiles.child(client.getParent().getParent(),v.id());
        return Files.isRegularFile(dir.resolve(v.id() + ".json")) ? dir : null;
    }

    static String inherits(Minecraft.Version v) {
        var m = v.manifest();
        return m != null && m.has("inheritsFrom") ? m.get("inheritsFrom").getAsString() : null;
    }

    private static boolean canTrash() {
        return Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.MOVE_TO_TRASH);
    }

    static void remove(Path dir) throws IOException { SafeTrash.remove(dir); }

    private void forget(Minecraft.Version v) {
        boolean wasCurrent = v.equals(versions.getValue());
        versions.getItems().remove(v);
        if (instanceList != null) instanceList.getItems().remove(v);
        if (wasCurrent) {
            if (versions.getItems().isEmpty()) versions.setValue(null);
            else versions.getSelectionModel().select(0);
        }
    }

    // ---------------------------------------------------------------- helpers

    private void status(String text) {
        if (scene.lookup("#launch-status") instanceof Label l) l.setText(text);
    }

    private void alert(Alert.AlertType type, String header, String content) {
        Alert a = themed(new Alert(type));
        a.setTitle("实例管理");
        a.setHeaderText(header);
        a.setContentText(content);
        a.getButtonTypes().setAll(new ButtonType("知道了", ButtonBar.ButtonData.OK_DONE));
        a.showAndWait();
    }

    private Alert themed(Alert a) {
        if (scene.getWindow() != null) a.initOwner(scene.getWindow());
        a.getDialogPane().getStylesheets().addAll(scene.getStylesheets());
        a.getDialogPane().getStyleClass().add("hfml-dialog");
        a.setGraphic(null);
        return a;
    }
}
