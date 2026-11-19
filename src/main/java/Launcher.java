import javafx.animation.Timeline;
import javafx.animation.KeyFrame;
import javafx.application.Application;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.shape.SVGPath;
import javafx.geometry.Insets;
import javafx.scene.image.ImageView;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;


public final class Launcher extends Application {
    static final String VERSION = "0.2.0";
    static final String NAME = "Hoshino Fuji Launcher";
    private final StackPane pages = new StackPane();
    private final ToggleGroup navigation = new ToggleGroup();
    private Stage window;
    private final Minecraft minecraft = new Minecraft();
    private final ComboBox<Minecraft.Version> versions = new ComboBox<>();
    private final TextField player = new TextField("LocalPlayer");
    private final ComboBox<Integer> memory = new ComboBox<>();
    private final Button launch = new Button("启动游戏");
    private final Label status = new Label("选择一个已安装的原版版本");
    private volatile String result;
    private boolean running;
    private final Installer installer = new Installer(minecraft);
    private final Button refreshCatalog = new Button("获取官方列表");
    private final ComboBox<Installer.Entry> available = new ComboBox<>();
    private final ComboBox<String> category = new ComboBox<>();
    private final Button install = new Button("下载安装");
    private java.util.List<Installer.Entry> catalog = java.util.List.of();
    private volatile String progress;
    private boolean installing;

    @Override
    public void start(Stage stage) {
        player.setPrefColumnCount(12);
        player.setId("player-name");
        memory.setId("memory-setting");
        status.setId("launch-status");
        memory.getItems().addAll(1024, 2048, 4096, 6144);
        memory.setValue(2048);
        launch.setDefaultButton(true);
        launch.setOnAction(event -> launchGame());

        try {
            versions.getItems().setAll(minecraft.versions());
            var preferred = versions.getItems().stream()
                    .filter(version -> version.id().equals("1.21.1")).findFirst();
            preferred.ifPresentOrElse(versions.getSelectionModel()::select,
                    versions.getSelectionModel()::selectFirst);
        } catch (Exception exception) {
            status.setText("读取本地版本失败：" + exception.getMessage());
        }
        if (versions.getItems().isEmpty()) {
            status.setText("请选择官方版本进行安装");
            launch.setDisable(true);
        }

        category.getItems().addAll("正式版", "快照 / 预发布", "全部版本");
        category.setValue("正式版");
        category.setOnAction(event -> filterCatalog());
        available.setPrefWidth(230);
        available.setPromptText("选择官方版本");
        available.setOnAction(event -> updateInstallButton());
        refreshCatalog.setOnAction(event -> loadCatalog());
        install.setOnAction(event -> installVersion());
        updateInstallButton();
        window = stage;

        // All pages use these same controls and version collection, keeping the active instance consistent.
        versions.valueProperty().addListener((observable, before, currentVersion) -> {
            if (!running && !installing) {
                status.setText(currentVersion == null ? "请先安装游戏版本" : "已准备就绪");
                launch.setDisable(currentVersion == null);
            }
        });
        versions.setCellFactory(list -> versionCell());
        versions.setButtonCell(versionCell());
        versions.setMaxWidth(Double.MAX_VALUE);
        versions.setId("instance-selector");
        versions.getStyleClass().add("instance-selector");
        HBox current = new HBox(14, icon("layers"), field("当前实例", versions));
        current.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(current.getChildren().get(1), Priority.ALWAYS);
        HBox identity = new HBox(18, field("玩家名", player), field("内存分配（MB）", memory));
        launch.getStyleClass().add("primary");
        launch.setGraphic(icon("play"));
        launch.setMaxWidth(Double.MAX_VALUE);
        status.getStyleClass().add("status");
        status.setWrapText(true);
        status.setMinHeight(34);
        Button browse = action("模组与材质包", "puzzle", this::browseContent);
        Button loaders = action("加载器", "layers", this::installLoader);
        Button downloadsShortcut = action("版本下载", "download", () -> selectPage(2));
        HBox shortcuts = new HBox(12, browse, loaders, downloadsShortcut);
        for (var child : shortcuts.getChildren()) {
            HBox.setHgrow(child, Priority.ALWAYS);
            ((Button)child).setMaxWidth(Double.MAX_VALUE);
        }
        VBox home = new VBox(24, current, identity, launch, shortcuts, status);
        home.setAlignment(Pos.CENTER);
        home.getStyleClass().add("launch-card");
        home.setMaxWidth(680);
        home.setMaxHeight(530);

        VBox library = instancePage();
        VBox resources = resourcePage();
        pages.getChildren().addAll(home, library, resources);
        pages.getStyleClass().add("page-host");

        HBox tabs = new HBox(10, tab("开始游戏", "play", 0), tab("实例管理", "cube", 1),
                tab("资源中心", "layers", 2));
        tabs.getStyleClass().add("navigation");
        tabs.setAlignment(Pos.CENTER);
        HBox.setHgrow(tabs, Priority.ALWAYS);
        ScrollPane pageScroll = new ScrollPane(pages);
        pageScroll.setFitToWidth(true);
        pageScroll.setFitToHeight(true);
        pageScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        pageScroll.getStyleClass().add("main-scroll");
        VBox right = new VBox(tabs, pageScroll);
        right.setMinWidth(520);
        VBox.setVgrow(pageScroll, Priority.ALWAYS);
        HBox.setHgrow(right, Priority.ALWAYS);

        SkinView skin = new SkinView();
        skin.setId("skin-preview");
        ImageView signature = new ImageView(SignatureMark.image());
        signature.setPreserveRatio(true);
        signature.setSmooth(true);
        signature.setMouseTransparent(true);
        signature.setAccessibleText("星野藤 Hoshino Fuji");
        Tooltip.install(skin,new Tooltip("拖动旋转 · 双击复位"));
        StackPane poster = new StackPane(skin, signature);
        StackPane.setAlignment(signature,Pos.BOTTOM_LEFT);
        StackPane.setMargin(signature,new Insets(0,0,20,18));
        poster.getStyleClass().add("poster");
        signature.fitWidthProperty().bind(poster.widthProperty().multiply(.53));
        HBox body = new HBox(poster, right);
        VBox.setVgrow(body, Priority.ALWAYS);
        poster.prefWidthProperty().bind(body.widthProperty().multiply(.40));
        poster.visibleProperty().bind(body.widthProperty().greaterThanOrEqualTo(900));
        poster.managedProperty().bind(poster.visibleProperty());

        Label brand = new Label(NAME, icon("cube"));
        brand.getStyleClass().add("brand");
        brand.setMinWidth(Region.USE_PREF_SIZE);
        HBox header = new HBox(14, brand);
        header.getStyleClass().add("header");
        header.setAlignment(Pos.CENTER_LEFT);
        VBox root = new VBox(header,body);
        root.setId("hfml-root");
        selectPage(0);

        Timeline refresh = new Timeline(new KeyFrame(Duration.millis(80), event -> updateStatus()));
        refresh.setCycleCount(Timeline.INDEFINITE);
        refresh.play();
        stage.setOnCloseRequest(event -> {
            if (running || installing) {
                event.consume();
                status.setText(installing ? "正在安装，请等待下载完成" : "请先从 Minecraft 退出游戏");
            }
        });
        stage.setOnHidden(event -> { refresh.stop(); skin.stop(); });
        stage.setTitle(NAME);
        Scene scene = new Scene(root, 1180, 750);
        scene.getStylesheets().add(Launcher.class.getResource("/theme/launcher.css").toExternalForm());
        stage.setScene(scene);
        stage.setMinWidth(720);
        stage.setMinHeight(620);
        stage.show();
        skin.start();
        loadCatalog();
    }

    private void browseContent() {
        if (running || installing) { status.setText("请等待当前操作完成后管理模组"); selectPage(0); return; }
        new ContentBrowser(window, minecraft, versions.getValue(), getHostServices()::showDocument);
    }

    private void installLoader() {
        if (running || installing) { status.setText("请等待当前操作完成后安装加载器"); selectPage(0); return; }
        if (versions.getValue() == null) { selectPage(2); return; }
        new LoaderDialog(window, minecraft, versions.getValue(), version -> {
            versions.getItems().removeIf(v -> v.id().equals(version.id()));
            versions.getItems().addFirst(version);
            versions.setValue(version);
            status.setText(version.id() + " 已安装，可以下载模组并启动");
            selectPage(0);
        });
    }

    private ListCell<Minecraft.Version> versionCell() {
        return new ListCell<>() {
            @Override protected void updateItem(Minecraft.Version item,boolean empty) {
                super.updateItem(item,empty);
                setText(empty || item == null ? null : readableVersion(item));
                setTooltip(empty || item == null ? null : new Tooltip(item.id()));
            }
        };
    }

    private static String readableVersion(Minecraft.Version version) {
        if (version.loader().equals("vanilla")) return "原版 " + version.id();
        String loader = switch (version.loader()) {
            case "fabric" -> "Fabric";
            case "neoforge" -> "NeoForge";
            default -> "Forge";
        };
        String build = version.id();
        if (version.loader().equals("fabric")) build = build.replace("fabric-loader-","").replace("-"+version.base(),"");
        else if (version.loader().equals("neoforge")) build = build.replace("neoforge-","");
        else build = build.replace(version.base()+"-forge-","");
        return loader + " " + version.base() + "  ·  " + build;
    }

    private VBox instancePage() {
        ListView<Minecraft.Version> list = new ListView<>(versions.getItems());
        list.setId("instance-list");
        list.setCellFactory(view -> versionCell());
        list.getSelectionModel().select(versions.getValue());
        Button choose = action("设为当前实例", "play", () -> {
            if (list.getSelectionModel().getSelectedItem() != null) {
                versions.setValue(list.getSelectionModel().getSelectedItem());
                selectPage(0);
            }
        });
        Button folder = action("打开实例目录", "cube", () -> {
            var selected = list.getSelectionModel().getSelectedItem();
            if (selected == null) return;
            try {
                java.nio.file.Path directory = java.nio.file.Files.createDirectories(minecraft.instance(selected));
                java.awt.Desktop.getDesktop().open(directory.toFile());
            } catch (Exception exception) {
                new Alert(Alert.AlertType.ERROR, "无法打开实例目录：" + exception.getMessage(), ButtonType.OK).show();
            }
        });
        VBox page = new VBox(18, heading("我的实例"), list, new HBox(10, choose, folder));
        VBox.setVgrow(list, Priority.ALWAYS);
        page.getStyleClass().add("card");
        return page;
    }

    private VBox resourcePage() {
        category.setPrefWidth(145);
        available.setMinWidth(100);
        available.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(available, Priority.ALWAYS);
        refreshCatalog.getStyleClass().add("link-button");
        HBox title = new HBox(12,heading("官方版本下载"),refreshCatalog);
        title.setAlignment(Pos.CENTER_LEFT);
        VBox downloads = new VBox(18,title,new HBox(10,category,available),install);
        downloads.getStyleClass().add("card");
        Button browse = action("模组与材质包", "puzzle", this::browseContent);
        Button loaders = action("安装加载器", "layers", this::installLoader);
        Label resourceStatus = new Label();
        resourceStatus.textProperty().bind(status.textProperty());
        resourceStatus.setWrapText(true);
        resourceStatus.getStyleClass().add("status");
        Label designer = new Label("Skin design / hoshinofuji");
        designer.getStyleClass().add("designer");
        VBox page = new VBox(20,downloads,new HBox(12,browse,loaders),resourceStatus,designer);
        page.setAlignment(Pos.CENTER_LEFT);
        page.setMaxHeight(490);
        return page;
    }

    private ToggleButton tab(String label,String symbol,int index) {
        ToggleButton button = new ToggleButton(label,icon(symbol));
        button.setToggleGroup(navigation);
        button.setUserData(index);
        button.getStyleClass().add("nav-button");
        button.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(button, Priority.ALWAYS);
        button.setOnAction(e -> selectPage(index));
        return button;
    }

    private void selectPage(int index) {
        for (int i = 0; i < pages.getChildren().size(); i++) {
            pages.getChildren().get(i).setVisible(i == index);
            pages.getChildren().get(i).setManaged(i == index);
        }
        if (navigation.getToggles().size() > index) navigation.selectToggle(navigation.getToggles().get(index));
    }

    private static Button action(String label,String symbol,Runnable task) {
        Button button = new Button(label,icon(symbol));
        button.getStyleClass().add("tile-button");
        button.setContentDisplay(ContentDisplay.TOP);
        button.setOnAction(e -> task.run());
        return button;
    }

    static Region icon(String name) {
        String data = switch (name) {
            case "play" -> "M5 3 L21 12 L5 21 Z";
            case "download" -> "M12 3 V16 M6 10 L12 16 L18 10 M3 17 V22 H21 V17";
            case "puzzle" -> "M3 3 H9 V7 C13 3 17 7 13 9 H21 V15 H17 C21 19 17 23 15 19 V23 H3 V15 H7 C11 11 7 7 3 11 Z";
            case "layers" -> "M2 7 L12 1 L22 7 L12 13 Z M2 12 L12 18 L22 12 M2 17 L12 23 L22 17";
            default -> "M3 6 L12 1 L21 6 V18 L12 23 L3 18 Z M3 6 L12 11 L21 6 M12 11 V23";
        };
        SVGPath path = new SVGPath();
        path.setContent(data);
        path.getStyleClass().add(name.equals("play") ? "play-icon" : "line-icon");
        StackPane holder = new StackPane(path);
        holder.setMinSize(26,26);
        holder.setPrefSize(26,26);
        holder.setMaxSize(26,26);
        return holder;
    }

    private void loadCatalog() {
        refreshCatalog.setDisable(true);
        refreshCatalog.setText("正在获取…");
        updateInstallButton();
        Thread.ofVirtual().start(() -> {
            try {
                var entries = installer.catalog();
                javafx.application.Platform.runLater(() -> {
                    catalog = entries;
                    filterCatalog();
                });
            } catch (Exception e) {
                javafx.application.Platform.runLater(() -> status.setText("官方列表获取失败，请重试：" + e.getMessage()));
            } finally {
                javafx.application.Platform.runLater(() -> {
                    refreshCatalog.setText("刷新官方列表");
                    refreshCatalog.setDisable(installing);
                    updateInstallButton();
                });
            }
        });
    }

    private void filterCatalog() {
        String type = switch (category.getValue()) {
            case "正式版" -> "release";
            case "快照 / 预发布" -> "snapshot";
            default -> "";
        };
        available.getItems().setAll(catalog.stream()
                .filter(entry -> type.isEmpty() || entry.type().equals(type)).toList());
        available.getSelectionModel().selectFirst();
        updateInstallButton();
    }

    private void updateInstallButton() {
        var entry = available.getValue();
        boolean local = entry != null && versions.getItems().stream().anyMatch(v -> v.id().equals(entry.id()));
        install.setText(local ? "校验安装" : "下载安装");
        install.setDisable(entry == null || installing || running || refreshCatalog.isDisabled());
    }

    private void installVersion() {
        var entry = available.getValue();
        installing = true;
        install.setDisable(true);
        available.setDisable(true);
        category.setDisable(true);
        refreshCatalog.setDisable(true);
        launch.setDisable(true);
        versions.setDisable(true);
        String message = "正在安装 " + entry.id();
        status.setText(message);
        Thread.ofVirtual().start(() -> {
            try {
                var version = installer.install(entry, value -> progress = value);
                javafx.application.Platform.runLater(() -> {
                    versions.getItems().removeIf(v -> v.id().equals(version.id()));
                    versions.getItems().addFirst(version);
                    versions.setValue(version);
                    finishInstall(version.id() + " 已安装，可以启动游戏");
                });
            } catch (Exception e) {
                javafx.application.Platform.runLater(() -> finishInstall("安装失败：" + e.getMessage()));
            }
        });
    }

    private void finishInstall(String message) {
        installing = false;
        progress = null;
        available.setDisable(false);
        category.setDisable(false);
        refreshCatalog.setDisable(false);
        versions.setDisable(false);
        launch.setDisable(versions.getValue() == null);
        updateInstallButton();
        status.setText(message);
    }

    private static HBox heading(String title) {
        Region marker = new Region();
        marker.getStyleClass().add("marker");
        Label label = new Label(title);
        label.getStyleClass().add("section-title");
        HBox heading = new HBox(10, marker, label);
        heading.setAlignment(Pos.CENTER_LEFT);
        return heading;
    }

    private static VBox field(String title, javafx.scene.control.Control control) {
        Label label = new Label(title);
        label.setLabelFor(control);
        label.getStyleClass().add("field-title");
        control.setMaxWidth(Double.MAX_VALUE);
        control.setMinWidth(42);
        VBox field = new VBox(5, label, control);
        field.setPrefWidth(130);
        field.setMinWidth(65);
        HBox.setHgrow(field, Priority.ALWAYS);
        return field;
    }

    private void launchGame() {
        String name = player.getText().trim();
        if (!name.matches("[A-Za-z0-9_]{3,16}")) {
            status.setText("玩家名需为 3–16 个英文字母、数字或下划线");
            player.requestFocus();
            return;
        }

        Minecraft.Version version = versions.getValue();
        if (version == null) { selectPage(2); return; }
        int memoryMb = memory.getValue();
        running = true;
        updateInstallButton();
        launch.setDisable(true);
        status.setText("Minecraft " + version.id() + " 运行中");
        Thread.ofPlatform().daemon().name("minecraft-output").start(() -> {
            int exitCode;
            try (var gameLaunch = minecraft.command(version, name, memoryMb)) {
                ProcessBuilder builder = gameLaunch.builder();
                builder.redirectOutput(new java.io.File(builder.directory(), "launcher-output.log"));
                Process game = builder.start();
                exitCode = game.waitFor();
            } catch (Exception exception) {
                result = "启动失败：" + exception.getMessage();
                return;
            }
            result = exitCode == 0 ? "游戏已退出（退出码 0）"
                    : "游戏异常退出（退出码 " + exitCode + "）";
        });
    }

    private void updateStatus() {
        if (installing && progress != null) status.setText(progress);
        String finished = result;
        if (finished != null) {
            status.setText(finished);
            result = null;
            running = false;
            updateInstallButton();
            launch.setDisable(false);
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
