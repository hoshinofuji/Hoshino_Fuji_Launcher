import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.awt.Desktop;
import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * 模组与资源中心：模组 / 资源包·材质包 / 光影包 三个选项卡。
 * 左侧在线搜索 Modrinth 并下载（可选安装到游戏或保存到本地），
 * 右侧按实例列出已安装的内容，支持本地导入、删除、打开文件夹。
 */
final class ContentCenter {
    enum Kind {
        MOD("mod", "模组", "mods", "模组文件", "*.jar"),
        PACK("resourcepack", "资源包 · 材质包", "resourcepacks", "资源包", "*.zip"),
        SHADER("shader", "光影包", "shaderpacks", "光影包", "*.zip");

        final String api, title, folder, fileLabel, pattern;

        Kind(String api, String title, String folder, String fileLabel, String pattern) {
            this.api = api; this.title = title; this.folder = folder; this.fileLabel = fileLabel; this.pattern = pattern;
        }
    }

    private static ContentCenter openWindow;

    private final Minecraft minecraft = new Minecraft();
    private final ContentService service = new ContentService();
    private final Function<Minecraft.Version, String> namer;
    private final Stage stage = new Stage();
    private Kind kind = Kind.MOD;

    // 左侧：在线
    private final TextField query = new TextField();
    private final TextField game = new TextField();
    private final Button search = new Button("搜索");
    private final ListView<ContentService.Project> results = new ListView<>();
    private final ComboBox<ContentService.Release> releases = new ComboBox<>();
    private final Button download = new Button("下载…");
    private final Label releaseInfo = new Label();
    private final Button previous = new Button("上一页");
    private final Button next = new Button("下一页");
    private final Label pageInfo = new Label();
    private int page, total;
    private String activeQuery = "", activeGame = "", activeLoader = "all";
    private Kind activeKind = Kind.MOD;
    private long searchToken;

    // 右侧：已安装
    private final ComboBox<Minecraft.Version> instance = new ComboBox<>();
    private final ListView<Path> installed = new ListView<>();
    private final Label instanceHint = new Label();
    private final Label installedTitle = new Label();
    private final Button remove = new Button("删除所选");

    private final Label status = new Label();
    private int busy;

    private ContentCenter(Window owner, ObservableList<Minecraft.Version> versions, Minecraft.Version current,
                          Function<Minecraft.Version, String> namer) {
        this.namer = namer;
        stage.initOwner(owner);
        stage.setTitle("模组与资源");
        instance.setItems(versions);
        instance.setValue(current != null ? current : versions.isEmpty() ? null : versions.get(0));

        VBox root = new VBox(14, header(), tabs(), body(), status);
        root.getStyleClass().add("content-center");
        root.setPadding(new Insets(18, 22, 16, 22));
        status.getStyleClass().add("status");
        status.setWrapText(true);
        Scene scene = new Scene(root, 1120, 720);
        scene.getStylesheets().addAll(owner.getScene().getStylesheets());
        stage.setScene(scene);
        stage.setMinWidth(860);
        stage.setMinHeight(560);
        stage.setOnHidden(e -> { if (openWindow == this) openWindow = null; });

        instance.valueProperty().addListener((o, a, b) -> { syncFilters(); refreshInstalled(); runSearch(0); });
        syncFilters();
        refreshInstalled();
        runSearch(0);
    }

    static void open(Window owner, ObservableList<Minecraft.Version> versions, Minecraft.Version current,
                     Function<Minecraft.Version, String> namer) {
        if (openWindow != null) {
            openWindow.stage.toFront();
            if (current != null) openWindow.instance.setValue(current);
            return;
        }
        openWindow = new ContentCenter(owner, versions, current, namer);
        openWindow.stage.show();
    }

    // ---------------------------------------------------------------- 布局

    private Node header() {
        Label title = new Label("模组与资源");
        title.getStyleClass().add("center-title");
        Region grow = new Region();
        HBox.setHgrow(grow, Priority.ALWAYS);
        Label more = new Label("更多下载渠道");
        more.getStyleClass().add("instance-count");
        HBox box = new HBox(12, title, grow, more,
                link("Modrinth", "https://modrinth.com/"),
                link("CurseForge", "https://www.curseforge.com/minecraft"),
                link("Planet Minecraft", "https://www.planetminecraft.com/"));
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private Node tabs() {
        ToggleGroup group = new ToggleGroup();
        HBox bar = new HBox(6);
        bar.getStyleClass().add("center-tabs");
        for (Kind k : Kind.values()) {
            ToggleButton t = new ToggleButton(k.title, icon(switch (k) {
                case MOD -> "M3 6 L12 1 L21 6 V18 L12 23 L3 18 Z M3 6 L12 11 L21 6 M12 11 V23";
                case PACK -> "M2 7 L12 1 L22 7 L12 13 Z M2 12 L12 18 L22 12 M2 17 L12 23 L22 17";
                case SHADER -> "M12 3 V5 M12 19 V21 M3 12 H5 M19 12 H21 M5.6 5.6 L7 7 M17 17 L18.4 18.4 M5.6 18.4 L7 17 M17 7 L18.4 5.6 M12 8 A4 4 0 1 0 12 16 A4 4 0 1 0 12 8 Z";
            }, 22));
            t.getStyleClass().addAll("nav-button", "center-tab");
            t.setToggleGroup(group);
            t.setUserData(k);
            bar.getChildren().add(t);
            if (k == kind) t.setSelected(true);
        }
        group.selectedToggleProperty().addListener((o, a, b) -> {
            if (b == null) { a.setSelected(true); return; }   // 始终保持一个选项卡被选中
            kind = (Kind) b.getUserData();
            query.clear();
            syncFilters();
            refreshInstalled();
            runSearch(0);
        });
        return bar;
    }

    private Node body() {
        // ---- 左：在线下载
        Label onlineTitle = sectionTitle("在线下载");
        Label source = new Label("来自 Modrinth");
        source.getStyleClass().add("instance-count");
        HBox head = new HBox(10, onlineTitle, source);
        head.setAlignment(Pos.CENTER_LEFT);

        HBox.setHgrow(query, Priority.ALWAYS);
        query.setOnAction(e -> runSearch(0));
        search.setOnAction(e -> runSearch(0));
        search.getStyleClass().add("accent");
        Label gameLabel = new Label("游戏版本");
        gameLabel.getStyleClass().add("field-title");
        game.setPrefColumnCount(7);
        game.setPromptText("不限");
        game.setOnAction(e -> runSearch(0));
        HBox searchRow = new HBox(10, query, gameLabel, game, search);
        searchRow.setAlignment(Pos.CENTER_LEFT);

        results.getStyleClass().add("content-results-list");
        results.setCellFactory(v -> new ProjectCell());
        results.setPlaceholder(new Label("没有找到内容"));
        VBox.setVgrow(results, Priority.ALWAYS);
        results.getSelectionModel().selectedItemProperty().addListener((o, a, p) -> loadReleases(p));

        releases.setMaxWidth(Double.MAX_VALUE);
        releases.setPromptText("先在上面选一个项目");
        releases.setCellFactory(v -> new ReleaseCell());
        releases.setButtonCell(new ReleaseCell());
        HBox.setHgrow(releases, Priority.ALWAYS);
        download.getStyleClass().add("accent");
        download.setDisable(true);
        download.setOnAction(e -> askDownload());
        releases.valueProperty().addListener((o, a, r) -> {
            download.setDisable(r == null || busy > 0);
            releaseInfo.setText(r == null ? "" : describe(r));
        });
        releaseInfo.getStyleClass().add("status");
        releaseInfo.setWrapText(true);
        HBox releaseRow = new HBox(10, releases, download);
        releaseRow.setAlignment(Pos.CENTER_LEFT);

        previous.setOnAction(e -> runSearch(page - 1));
        next.setOnAction(e -> runSearch(page + 1));
        previous.setDisable(true);
        next.setDisable(true);
        pageInfo.getStyleClass().add("instance-count");
        Region pageGap = new Region();
        HBox.setHgrow(pageGap, Priority.ALWAYS);
        HBox pager = new HBox(10, previous, next, pageGap, pageInfo);
        pager.setAlignment(Pos.CENTER_LEFT);

        VBox left = new VBox(12, head, searchRow, results, releaseRow, releaseInfo, pager);
        left.getStyleClass().addAll("card", "center-card");
        HBox.setHgrow(left, Priority.ALWAYS);
        left.setPrefWidth(620);

        // ---- 右：已安装
        installedTitle.getStyleClass().add("section-title");
        Region marker = new Region();
        marker.getStyleClass().add("marker");
        HBox rightHead = new HBox(10, marker, installedTitle);
        rightHead.setAlignment(Pos.CENTER_LEFT);

        instance.setMaxWidth(Double.MAX_VALUE);
        instance.setCellFactory(v -> new VersionCell());
        instance.setButtonCell(new VersionCell());
        HBox.setHgrow(instance, Priority.ALWAYS);
        Label instLabel = new Label("实例");
        instLabel.getStyleClass().add("field-title");
        HBox instRow = new HBox(10, instLabel, instance);
        instRow.setAlignment(Pos.CENTER_LEFT);

        instanceHint.getStyleClass().add("warning-hint");
        instanceHint.setWrapText(true);
        instanceHint.managedProperty().bind(instanceHint.visibleProperty());

        installed.getStyleClass().add("instance-table");
        installed.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        installed.setCellFactory(v -> new FileCell());
        VBox.setVgrow(installed, Priority.ALWAYS);

        Button importLocal = new Button("本地导入", icon("M12 4 V15 M7 10 L12 15 L17 10 M4 19 H20", 18));
        importLocal.getStyleClass().add("accent");
        importLocal.setOnAction(e -> importLocal());
        Button openFolder = new Button("打开文件夹", icon("M3 6 C3 5 3.5 4.5 4.5 4.5 H9.5 L11.5 7 H19.5 C20.5 7 21 7.5 21 8.5 V18 C21 19 20.5 19.5 19.5 19.5 H4.5 C3.5 19.5 3 19 3 18 Z", 18));
        openFolder.setOnAction(e -> openFolder());
        remove.getStyleClass().add("danger");
        remove.disableProperty().bind(Bindings.isEmpty(installed.getSelectionModel().getSelectedItems()));
        remove.setOnAction(e -> deleteInstalled(new ArrayList<>(installed.getSelectionModel().getSelectedItems())));
        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        HBox actions = new HBox(10, importLocal, openFolder, gap, remove);
        actions.setAlignment(Pos.CENTER_LEFT);
        importLocal.disableProperty().bind(instance.valueProperty().isNull());
        openFolder.disableProperty().bind(instance.valueProperty().isNull());

        VBox right = new VBox(12, rightHead, instRow, instanceHint, installed, actions);
        right.getStyleClass().addAll("card", "center-card");
        right.setPrefWidth(420);
        right.setMinWidth(340);

        HBox body = new HBox(16, left, right);
        VBox.setVgrow(body, Priority.ALWAYS);
        return body;
    }

    // ---------------------------------------------------------------- 在线搜索

    /** 根据右侧实例自动填游戏版本；模组按实例的加载器筛选。 */
    private void syncFilters() {
        Minecraft.Version v = instance.getValue();
        game.setText(v == null ? "" : v.base());
        query.setPromptText(switch (kind) {
            case MOD -> "搜索模组名称，留空查看热门";
            case PACK -> "搜索资源包 / 材质包名称，留空查看热门";
            case SHADER -> "搜索光影包名称，留空查看热门";
        });
        installedTitle.setText(switch (kind) {
            case MOD -> "已安装的模组";
            case PACK -> "已安装的资源包";
            case SHADER -> "已安装的光影包";
        });
        boolean vanilla = v != null && "vanilla".equals(v.loader());
        if (v == null) {
            instanceHint.setText("还没有实例，请先在资源中心下载一个游戏版本。");
            instanceHint.setVisible(true);
        } else if (kind == Kind.MOD && vanilla) {
            instanceHint.setText("这是原版实例，不能直接装模组。请先在资源中心给它装上 Fabric、Forge 或 NeoForge。");
            instanceHint.setVisible(true);
        } else if (kind == Kind.SHADER && vanilla) {
            instanceHint.setText("原版不支持光影，需要先装 Iris 或 OptiFine 等模组。");
            instanceHint.setVisible(true);
        } else {
            instanceHint.setVisible(false);
        }
    }

    private String loaderFilter() {
        Minecraft.Version v = instance.getValue();
        if (kind != Kind.MOD || v == null || "vanilla".equals(v.loader())) return "all";
        return v.loader();
    }

    private void runSearch(int toPage) {
        if (toPage < 0) return;
        page = toPage;
        activeQuery = query.getText() == null ? "" : query.getText().trim();
        activeGame = game.getText() == null ? "" : game.getText().trim();
        activeLoader = loaderFilter();
        activeKind = kind;
        long token = ++searchToken;
        results.getItems().clear();
        releases.getItems().clear();
        releases.setValue(null);
        status.setText("正在搜索 Modrinth…");
        task(() -> service.search(activeQuery, activeKind.api, activeGame, activeLoader, page * 20), r -> {
            if (token != searchToken) return;
            total = r.total();
            results.getItems().setAll(r.projects());
            int pages = Math.max(1, (total + 19) / 20);
            pageInfo.setText("找到 " + total + " 个项目 · 第 " + (page + 1) + " 页");
            previous.setDisable(page == 0);
            next.setDisable(page + 1 >= pages);
            status.setText(total == 0 ? "没有找到内容，试试别的关键词或清空游戏版本" : "选择左侧项目查看可下载的版本");
        });
    }

    private void loadReleases(ContentService.Project p) {
        releases.getItems().clear();
        releases.setValue(null);
        if (p == null) return;
        String loader = activeKind == Kind.MOD ? activeLoader : "all";
        status.setText("正在获取匹配版本…");
        task(() -> {
            List<ContentService.Release> list = service.releases(p, activeGame, loader);
            if (list.isEmpty() && !activeGame.isEmpty()) list = service.releases(p, "", loader);
            return list;
        }, list -> {
            if (results.getSelectionModel().getSelectedItem() != p) return;
            releases.getItems().setAll(list);
            if (!list.isEmpty()) releases.getSelectionModel().select(0);
            status.setText(list.isEmpty() ? "此项目没有匹配当前条件的版本" : "选好版本后点「下载…」");
        });
    }

    private static String describe(ContentService.Release r) {
        String games = r.games() == null || r.games().isEmpty() ? "" : r.games().size() > 4
                ? String.join(", ", r.games().subList(0, 4)) + " …" : String.join(", ", r.games());
        return r.filename() + "  ·  " + size(r.size()) + (games.isEmpty() ? "" : "  ·  " + games);
    }

    // ---------------------------------------------------------------- 下载

    private void askDownload() {
        ContentService.Project p = results.getSelectionModel().getSelectedItem();
        ContentService.Release r = releases.getValue();
        Minecraft.Version v = instance.getValue();
        if (p == null || r == null) return;

        Alert ask = dialog(Alert.AlertType.CONFIRMATION, "下载「" + p.title() + "」");
        ButtonType toGame = new ButtonType("安装到游戏", ButtonBar.ButtonData.OK_DONE);
        ButtonType toDisk = new ButtonType("保存到本地…", ButtonBar.ButtonData.OTHER);
        ask.getButtonTypes().setAll(new ButtonType("取消", ButtonBar.ButtonData.CANCEL_CLOSE), toDisk, toGame);
        boolean canGame = v != null && !(kind == Kind.MOD && "vanilla".equals(v.loader()));
        Label where = new Label(v == null ? "还没有可以安装的实例，只能保存到本地。"
                : !canGame ? "当前实例「" + namer.apply(v) + "」是原版，不能安装模组，只能保存到本地。"
                : kind == Kind.MOD ? "安装到游戏：放进实例「" + namer.apply(v) + "」的 mods 文件夹，会自动补齐必需的前置模组。"
                : "安装到游戏：放进实例「" + namer.apply(v) + "」的 " + kind.folder + " 文件夹。");
        where.setWrapText(true);
        Label local = new Label("保存到本地：自己选择保存位置，之后可以用「本地导入」装进任意实例。");
        local.setWrapText(true);
        Label file = new Label(describe(r));
        file.getStyleClass().add("status");
        file.setWrapText(true);
        VBox content = new VBox(10, file, where, local);
        content.setPrefWidth(480);
        ask.getDialogPane().setContent(content);
        ask.getDialogPane().lookupButton(toGame).setDisable(!canGame);
        ask.getDialogPane().lookupButton(toGame).getStyleClass().add("accent");
        ButtonType answer = ask.showAndWait().orElse(null);
        if (answer == toGame) installToGame(p, r, v);
        else if (answer == toDisk) saveToDisk(r);
    }

    private void installToGame(ContentService.Project p, ContentService.Release r, Minecraft.Version v) {
        Kind k = kind;
        status.setText("正在下载并校验 " + r.filename());
        task(() -> {
            Path dir = minecraft.instance(v);
            if (k == Kind.MOD) {
                new ModManager(service).install(v, dir, p, r, msg -> Platform.runLater(() -> status.setText(msg)));
            } else {
                try (var lease = InstanceAccess.open(dir)) {
                    Path folder = SafeFiles.child(dir, k.folder);
                    Files.createDirectories(folder);
                    String name = ModManager.safeName(r.filename());
                    if (!name.toLowerCase(Locale.ROOT).endsWith(".zip")) throw new java.io.IOException("资源包必须为 ZIP");
                    service.download(r, SafeFiles.child(folder, name));
                }
            }
            return null;
        }, x -> {
            status.setText("已安装「" + p.title() + "」到 " + namer.apply(v));
            if (v.equals(instance.getValue()) && k == kind) refreshInstalled();
        });
    }

    private void saveToDisk(ContentService.Release r) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("选择保存位置");
        chooser.setInitialFileName(r.filename());
        Path downloads = Path.of(System.getProperty("user.home"), "Downloads");
        if (Files.isDirectory(downloads)) chooser.setInitialDirectory(downloads.toFile());
        File target = chooser.showSaveDialog(stage);
        if (target == null) return;
        status.setText("正在下载并校验 " + r.filename());
        task(() -> { service.download(r, target.toPath()); return target; },
                f -> status.setText("已保存到 " + f.getAbsolutePath()));
    }

    // ---------------------------------------------------------------- 已安装

    private Path folder() {
        Minecraft.Version v = instance.getValue();
        if (v == null) return null;
        try { return SafeFiles.child(minecraft.instance(v), kind.folder); }
        catch (java.io.IOException e) { status.setText(e.getMessage()); return null; }
    }

    private void refreshInstalled() {
        installed.getItems().clear();
        Path dir = folder();
        installed.setPlaceholder(new Label(dir == null ? "没有选择实例" : switch (kind) {
            case MOD -> "这个实例还没有模组";
            case PACK -> "这个实例还没有资源包";
            case SHADER -> "这个实例还没有光影包";
        }));
        if (dir == null || !Files.isDirectory(dir)) return;
        try (Stream<Path> files = Files.list(dir)) {
            installed.getItems().setAll(files
                    .filter(f -> !Files.isSymbolicLink(f))
                    .filter(f -> !f.getFileName().toString().startsWith("."))
                    .filter(f -> kind != Kind.MOD || f.getFileName().toString().matches("(?i).+\\.jar(\\.disabled)?"))
                    .sorted(Comparator.comparing(f -> f.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .toList());
        } catch (Exception e) {
            status.setText("读取已安装内容失败：" + e.getMessage());
        }
    }

    private void importLocal() {
        Path dir = folder();
        if (dir == null) return;
        FileChooser chooser = new FileChooser();
        chooser.setTitle("选择要导入的" + kind.fileLabel);
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter(kind.fileLabel, kind.pattern),
                new FileChooser.ExtensionFilter("所有文件", "*.*"));
        List<File> files = chooser.showOpenMultipleDialog(stage);
        if (files == null || files.isEmpty()) return;
        Kind selectedKind = kind;
        Path selectedInstance = minecraft.instance(instance.getValue());
        task(() -> {
            try (var lease = InstanceAccess.open(selectedInstance)) {
                Path checked = SafeFiles.child(selectedInstance, selectedKind.folder);
                Files.createDirectories(checked);
                for (File f : files) {
                    String name = ModManager.safeName(f.getName());
                    String suffix = selectedKind == Kind.MOD ? ".jar" : ".zip";
                    if (!name.toLowerCase(Locale.ROOT).endsWith(suffix)) throw new java.io.IOException("文件类型不匹配：" + name);
                    Path destination = SafeFiles.child(checked, name);
                    if (Files.exists(destination, java.nio.file.LinkOption.NOFOLLOW_LINKS))
                        throw new java.io.IOException("已存在同名文件，请先移至回收站：" + name);
                    SafeFiles.atomicCopy(f.toPath(), destination);
                }
            }
            return files.size();
        }, n -> {
            status.setText("已导入 " + n + " 个文件");
            refreshInstalled();
        });
    }

    private void openFolder() {
        Path dir = folder();
        if (dir == null) return;
        task(() -> {
            Files.createDirectories(dir);
            Desktop.getDesktop().open(dir.toFile());
            return null;
        }, x -> {});
    }

    private void deleteInstalled(List<Path> files) {
        if (files.isEmpty()) return;
        Alert ask = dialog(Alert.AlertType.CONFIRMATION,
                files.size() == 1 ? "删除「" + files.get(0).getFileName() + "」？" : "删除这 " + files.size() + " 个文件？");
        Label info = new Label("文件会移到废纸篓 / 回收站，需要时还能找回。");
        info.setWrapText(true);
        ask.getDialogPane().setContent(info);
        ButtonType delete = new ButtonType("删除", ButtonBar.ButtonData.OK_DONE);
        ask.getButtonTypes().setAll(new ButtonType("取消", ButtonBar.ButtonData.CANCEL_CLOSE), delete);
        ask.getDialogPane().lookupButton(delete).getStyleClass().add("danger");
        if (ask.showAndWait().orElse(null) != delete) return;
        Kind k = kind;
        Path selectedInstance = minecraft.instance(instance.getValue());
        task(() -> {
            try (var lease = InstanceAccess.open(selectedInstance)) {
                Path directory = SafeFiles.child(selectedInstance, k.folder);
                for (Path f : files) {
                    Path checked = SafeFiles.child(directory, f.getFileName().toString());
                    if (!checked.equals(f.toAbsolutePath().normalize())) throw new java.io.IOException("内容路径已改变");
                    InstanceDeleter.remove(checked);
                }
                if (k == Kind.MOD) forgetMods(files);
            }
            return files.size();
        }, n -> {
            status.setText("已删除 " + n + " 个文件");
            refreshInstalled();
        });
    }

    /** 同步更新启动器记录的模组清单，避免下次安装依赖时误以为还在。 */
    private static void forgetMods(List<Path> files) throws Exception {
        Path record = SafeFiles.child(files.get(0).getParent(), ".hoshinofuji-installed.json");
        if (!Files.isRegularFile(record)) return;
        var json = com.google.gson.JsonParser.parseString(Files.readString(record)).getAsJsonObject();
        var names = files.stream().map(f -> f.getFileName().toString()).toList();
        for (String key : new ArrayList<>(json.keySet())) {
            var entry = json.get(key);
            if (entry.isJsonObject() && entry.getAsJsonObject().has("file")
                    && names.contains(entry.getAsJsonObject().get("file").getAsString())) json.remove(key);
        }
        Path part = Files.createTempFile(record.getParent(), ".registry-", ".part");
        try {
            Files.writeString(part, new com.google.gson.Gson().toJson(json));
            Files.move(part, record, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(part); }
    }

    // ---------------------------------------------------------------- 单元格

    private final class ProjectCell extends ListCell<ContentService.Project> {
        private final Label title = new Label();
        private final Label author = new Label();
        private final Label text = new Label();
        private final VBox box;

        ProjectCell() {
            title.getStyleClass().add("instance-name");
            author.getStyleClass().add("instance-id");
            text.getStyleClass().add("project-desc");
            text.setWrapText(true);
            text.setMaxHeight(38);
            HBox top = new HBox(8, title, author);
            top.setAlignment(Pos.BASELINE_LEFT);
            box = new VBox(3, top, text);
            box.setPrefWidth(1);
            setPrefWidth(0);
        }

        @Override protected void updateItem(ContentService.Project p, boolean empty) {
            super.updateItem(p, empty);
            setText(null);
            if (empty || p == null) { setGraphic(null); return; }
            title.setText(p.title());
            author.setText("by " + p.author());
            text.setText(p.description());
            setGraphic(box);
        }
    }

    private static final class ReleaseCell extends ListCell<ContentService.Release> {
        @Override protected void updateItem(ContentService.Release r, boolean empty) {
            super.updateItem(r, empty);
            setText(empty || r == null ? null : r.number() + "  ·  " + (r.loaders() == null || r.loaders().isBlank() ? r.name() : r.loaders()));
        }
    }

    private final class VersionCell extends ListCell<Minecraft.Version> {
        @Override protected void updateItem(Minecraft.Version v, boolean empty) {
            super.updateItem(v, empty);
            setText(empty || v == null ? null : namer.apply(v));
        }
    }

    private final class FileCell extends ListCell<Path> {
        private final Label name = new Label();
        private final Label meta = new Label();
        private final StackPane trash = InstanceDeleter.trashIcon();
        private final HBox row;

        FileCell() {
            name.getStyleClass().add("file-name");
            meta.getStyleClass().add("instance-id");
            Region grow = new Region();
            HBox.setHgrow(grow, Priority.ALWAYS);
            name.setMinWidth(0);
            meta.setMinWidth(Region.USE_PREF_SIZE);
            row = new HBox(8, name, grow, meta, trash);
            row.setAlignment(Pos.CENTER_LEFT);
            trash.visibleProperty().bind(hoverProperty().and(emptyProperty().not()));
            Tooltip.install(trash, new Tooltip("删除"));
            trash.addEventHandler(MouseEvent.MOUSE_PRESSED, MouseEvent::consume);
            trash.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> {
                e.consume();
                if (getItem() != null) deleteInstalled(List.of(getItem()));
            });
            setPrefWidth(0);
        }

        @Override protected void updateItem(Path f, boolean empty) {
            super.updateItem(f, empty);
            setText(null);
            if (empty || f == null) { setGraphic(null); return; }
            String n = f.getFileName().toString();
            boolean disabled = n.endsWith(".disabled");
            name.setText(disabled ? n.substring(0, n.length() - 9) : n);
            name.setOpacity(disabled ? 0.55 : 1);
            String m;
            try { m = Files.isDirectory(f) ? "文件夹" : size(Files.size(f)); } catch (Exception e) { m = ""; }
            meta.setText(disabled ? "已停用 · " + m : m);
            setGraphic(row);
        }
    }

    // ---------------------------------------------------------------- helpers

    private <T> void task(Callable<T> work, Consumer<T> done) {
        busy++;
        search.setDisable(true);
        download.setDisable(true);
        Thread t = new Thread(() -> {
            try {
                T value = work.call();
                Platform.runLater(() -> { finish(); done.accept(value); });
            } catch (Exception e) {
                Platform.runLater(() -> { finish(); status.setText("操作失败：" + (e.getMessage() == null ? e : e.getMessage())); });
            }
        }, "content-center");
        t.setDaemon(true);
        t.start();
    }

    private void finish() {
        busy = Math.max(0, busy - 1);
        if (busy == 0) {
            search.setDisable(false);
            download.setDisable(releases.getValue() == null);
        }
    }

    private Alert dialog(Alert.AlertType type, String header) {
        Alert a = new Alert(type);
        a.initOwner(stage);
        a.setTitle("模组与资源");
        a.setHeaderText(header);
        a.setGraphic(null);
        a.getDialogPane().getStylesheets().addAll(stage.getScene().getStylesheets());
        a.getDialogPane().getStyleClass().add("hfml-dialog");
        return a;
    }

    private static Label sectionTitle(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("section-title");
        return l;
    }

    private Hyperlink link(String text, String url) {
        Hyperlink h = new Hyperlink(text);
        h.setOnAction(e -> {
            Thread t = new Thread(() -> { try { Desktop.getDesktop().browse(URI.create(url)); } catch (Exception ignored) {} });
            t.setDaemon(true);
            t.start();
        });
        return h;
    }

    static Node icon(String path, double size) {
        SVGPath svg = new SVGPath();
        svg.setContent(path);
        svg.getStyleClass().add("line-icon");
        StackPane box = new StackPane(svg);
        box.setMinSize(size, size);
        box.setPrefSize(size, size);
        box.setMaxSize(size, size);
        svg.setScaleX(size / 26);
        svg.setScaleY(size / 26);
        return box;
    }

    static String size(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0);
        return String.format(Locale.ROOT, "%.1f MB", bytes / 1024.0 / 1024.0);
    }
}
