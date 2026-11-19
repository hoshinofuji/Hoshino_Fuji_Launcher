import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * 资源中心「官方版本下载」里的加载器选择：选好游戏版本和加载器，点一次「下载安装」，
 * 先由启动器装好原版，再自动装上所选加载器（Fabric / Forge / NeoForge）并设为当前实例。
 */
final class LoaderChain {
    static final String NONE = "无";

    private final Scene scene;
    private final Button install;
    private final ComboBox<?> available;
    private final ComboBox<Minecraft.Version> versions;
    private final ComboBox<String> loader = new ComboBox<>();
    private String pendingBase, pendingLoader;
    private boolean started;
    private static volatile boolean busy;

    @SuppressWarnings("unchecked")
    private LoaderChain(Scene scene, Button install, ComboBox<?> available, ComboBox<?> versions) {
        this.scene = scene;
        this.install = install;
        this.available = available;
        this.versions = (ComboBox<Minecraft.Version>) versions;
    }

    static void install(Scene scene) {
        Button installButton = null;
        for (Node n : scene.getRoot().lookupAll(".button"))
            if (n instanceof Button b && ("下载安装".equals(b.getText()) || "校验安装".equals(b.getText()))) installButton = b;
        if (installButton == null || !(installButton.getParent() instanceof VBox card)) return;
        HBox pickers = null;
        for (Node n : card.getChildren())
            if (n instanceof HBox h && h.getChildren().size() == 2 && h.getChildren().get(1) instanceof ComboBox<?>) pickers = h;
        if (pickers == null || !(scene.lookup("#instance-selector") instanceof ComboBox<?> versionsBox)) return;
        ComboBox<?> category = (ComboBox<?>) pickers.getChildren().get(0);
        ComboBox<?> available = (ComboBox<?>) pickers.getChildren().get(1);

        LoaderChain chain = new LoaderChain(scene, installButton, available, versionsBox);
        // 版本选择框正下方：加载器 [无 / Fabric / Forge / NeoForge]
        Label label = new Label("加载器");
        label.getStyleClass().add("loader-label");
        label.minWidthProperty().bind(category.widthProperty());
        label.prefWidthProperty().bind(category.widthProperty());
        chain.loader.getItems().setAll(NONE, "Fabric", "Forge", "NeoForge");
        chain.loader.setValue(NONE);
        chain.loader.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(chain.loader, Priority.ALWAYS);
        HBox row = new HBox(pickers.getSpacing(), label, chain.loader);
        row.setAlignment(Pos.CENTER_LEFT);
        card.getChildren().add(card.getChildren().indexOf(pickers) + 1, row);
        chain.wire();
    }

    private void wire() {
        // 点击时记下要装的加载器，让启动器照常安装原版
        install.addEventFilter(ActionEvent.ACTION, e -> {
            String choice = loader.getValue();
            Object entry = available.getValue();
            if (choice == null || NONE.equals(choice) || !(entry instanceof Installer.Entry en)) { pendingLoader = null; return; }
            pendingBase = en.id();
            pendingLoader = choice;
            started = false;
        });
        // 原版安装结束（按钮重新可用）后接着装加载器
        install.disabledProperty().addListener((o, was, now) -> {
            if (pendingLoader == null) return;
            if (now) { started = true; return; }
            if (!started) return;
            String base = pendingBase, type = pendingLoader;
            pendingLoader = null;
            installLoader(base, type);
        });
        // 加载器安装期间不允许启动游戏
        for (Node n : scene.getRoot().lookupAll(".button"))
            if (n instanceof Button b && b.getStyleClass().contains("primary"))
                b.addEventFilter(ActionEvent.ACTION, e -> {
                    if (busy) { e.consume(); status("正在安装加载器，请等待完成"); }
                });
    }

    @SuppressWarnings("unchecked")
    private void installLoader(String base, String type) {
        busy = true;
        install.setDisable(true);
        loader.setDisable(true);
        versions.setDisable(true);
        Thread t = new Thread(() -> {
            try {
                Minecraft mc = new Minecraft();
                Minecraft.Version vanilla;
                try { vanilla = mc.readVersion(base); } catch (Exception missing) {
                    Platform.runLater(this::done);      // 原版没装成功，启动器已经显示了原因
                    return;
                }
                Platform.runLater(() -> status("正在获取 " + type + " 官方版本…"));
                LoaderInstaller installer = new LoaderInstaller(mc);
                List<LoaderInstaller.Choice> choices = installer.choices(vanilla.base(), type);
                if (choices.isEmpty()) throw new IllegalStateException("此游戏版本暂无匹配的加载器");
                Minecraft.Version made = installer.install(vanilla, choices.get(0), msg -> Platform.runLater(() -> status(msg)));
                List<Minecraft.Version> all = mc.versions();
                Platform.runLater(() -> {
                    versions.getItems().setAll(all);
                    if (scene.lookup("#instance-list") instanceof ListView<?> list && list.getItems() != versions.getItems())
                        ((ListView<Minecraft.Version>) list).getItems().setAll(all);
                    all.stream().filter(v -> v.id().equals(made.id())).findFirst().ifPresent(versions::setValue);
                    status(type + " 已安装，已设为当前实例");
                    done();
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    status(type + "安装失败：" + (e.getMessage() == null ? e : e.getMessage()));
                    done();
                });
            }
        }, "install-loader");
        t.setDaemon(true);
        t.start();
    }

    private void done() {
        busy = false;
        install.setDisable(false);
        loader.setDisable(false);
        versions.setDisable(false);
        loader.setValue(NONE);
    }

    private void status(String text) {
        if (scene.lookup("#launch-status") instanceof Label l) l.setText(text);
    }
}
