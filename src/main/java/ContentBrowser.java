import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.*;
import java.nio.file.*;
import java.util.function.Consumer;

final class ContentBrowser {
    private final Minecraft minecraft;
    private final Minecraft.Version instance;
    private final ContentService service = new ContentService();
    private final Stage stage = new Stage();
    private final ComboBox<String> type = new ComboBox<>();
    private final ComboBox<String> loader = new ComboBox<>();
    private final TextField game = new TextField();
    private final TextField query = new TextField();
    private final Button search = new Button("搜索");
    private final Button previous = new Button("上一页");
    private final Button next = new Button("下一页");
    private final ListView<ContentService.Project> results = new ListView<>();
    private final ComboBox<ContentService.Release> releases = new ComboBox<>();
    private final Label description = new Label("选择项目查看介绍和版本");
    private final Label compatibility = new Label();
    private final Label status = new Label();
    private final Button download = new Button("下载文件…");
    private final Button projectPage = new Button("项目官网");
    private final Consumer<String> open;
    private int page;
    private int total;
    private boolean busy;
    private String activeQuery, activeType, activeGame, activeLoader;

    ContentBrowser(Window owner, Minecraft minecraft, Minecraft.Version selected, Consumer<String> open) {
        this.open = open;
        this.minecraft = minecraft;
        this.instance = selected;
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle("模组与材质包");
        type.getItems().addAll("模组 Mod", "材质包 / 资源包");
        type.setValue("模组 Mod");
        loader.getItems().addAll("all","fabric","forge","neoforge","quilt");
        loader.setValue(selected != null && !selected.loader().equals("vanilla") ? selected.loader() : "all");
        game.setText(selected == null ? "" : selected.base());
        game.setPrefColumnCount(8);
        query.setPromptText("搜索项目名称，留空查看热门");
        type.setOnAction(e -> reset());
        loader.setOnAction(e -> reset());
        game.textProperty().addListener((o,a,b) -> reset());
        search.setOnAction(e -> { page = 0; search(); });
        query.setOnAction(e -> { page = 0; search(); });
        previous.setOnAction(e -> { page--; search(); });
        next.setOnAction(e -> { page++; search(); });
        results.getSelectionModel().selectedItemProperty().addListener((o,a,b) -> select(b));
        releases.setMaxWidth(Double.MAX_VALUE);
        releases.setPromptText("选择项目版本");
        releases.valueProperty().addListener((o,a,b) -> showRelease(b));
        download.setOnAction(e -> download());
        projectPage.setOnAction(e -> {
            var project = results.getSelectionModel().getSelectedItem();
            if (project != null) open.accept("https://modrinth.com/project/" + project.id());
        });
        description.setWrapText(true);
        compatibility.setWrapText(true);
        status.setWrapText(true);
        status.getStyleClass().add("status");
        Label note = new Label(selected != null && !selected.loader().equals("vanilla")
                ? "目标实例 " + selected.id() + "。模组将安装到此实例并补齐必需依赖，安装后可直接启动。"
                : "当前为原版实例。安装模组前，请回首页安装并选择 Fabric、Forge 或 NeoForge 加载器。");
        note.setWrapText(true);
        note.getStyleClass().add("status");
        HBox sources = new HBox(10, new Label("更多下载渠道"),
                link("Modrinth","https://modrinth.com/"),
                link("CurseForge","https://www.curseforge.com/minecraft"),
                link("Planet Minecraft","https://www.planetminecraft.com/"));
        HBox filters = new HBox(10, field("内容类型",type),field("游戏版本",game),field("加载器",loader));
        HBox keywords = new HBox(8,query,search);
        HBox.setHgrow(query,Priority.ALWAYS);
        results.setId("content-results");
        results.setPrefHeight(190);
        results.setPlaceholder(new Label("输入条件后搜索 Modrinth"));
        results.setCellFactory(list -> new ListCell<>() {
            @Override protected void updateItem(ContentService.Project item, boolean empty) {
                super.updateItem(item,empty);
                setText(empty || item == null ? null : item.toString());
                setWrapText(true);
            }
        });
        VBox details = new VBox(8,description,releases,compatibility,new HBox(8,download,projectPage));
        details.setPrefWidth(350);
        ScrollPane detailScroll = new ScrollPane(details);
        detailScroll.setFitToWidth(true);
        detailScroll.setPrefWidth(360);
        detailScroll.setMinWidth(300);
        results.setMinWidth(200);
        HBox content = new HBox(16,results,detailScroll);
        HBox.setHgrow(results,Priority.ALWAYS);
        VBox.setVgrow(content,Priority.ALWAYS);
        VBox root = new VBox(14,sources,filters,keywords,content,new HBox(8,previous,next),note,status);
        root.setPadding(new Insets(20));
        var scene = new Scene(root,900,580);
        scene.getStylesheets().add(ContentBrowser.class.getResource("/theme/launcher.css").toExternalForm());
        stage.setScene(scene);
        stage.setMinWidth(800);
        stage.setMinHeight(560);
        stage.setOnCloseRequest(e -> {
            if (busy) { e.consume(); status.setText("正在处理请求，请等待完成"); }
        });
        reset();
        stage.show();
        search();
    }

    private Hyperlink link(String name,String url) {
        Hyperlink link = new Hyperlink(name);
        link.setOnAction(e -> open.accept(url));
        return link;
    }
    private static VBox field(String name,javafx.scene.control.Control value) {
        return new VBox(4,new Label(name),value);
    }
    private boolean isModded() { return instance != null && !instance.loader().equals("vanilla"); }
    private String kind() { return type.getValue().equals("模组 Mod") ? "mod" : "resourcepack"; }
    private void reset() {
        page = 0;
        total = 0;
        results.getItems().clear();
        releases.getItems().clear();
        loader.setDisable(!kind().equals("mod") || isModded());
        status.setText("按当前条件搜索 Modrinth");
        updateActions();
    }
    private void busy(boolean value) {
        busy = value;
        type.setDisable(value);
        game.setDisable(value);
        loader.setDisable(value || !kind().equals("mod") || isModded());
        game.setEditable(!isModded());
        query.setDisable(value);
        search.setDisable(value);
        results.setDisable(value);
        releases.setDisable(value);
        updateActions();
    }
    private void updateActions() {
        download.setText(kind().equals("mod") && isModded() ? "安装到实例" : "下载文件…");
        download.setDisable(busy || releases.getValue() == null);
        projectPage.setDisable(results.getSelectionModel().getSelectedItem() == null);
        previous.setDisable(busy || page == 0);
        next.setDisable(busy || (page+1)*20 >= total);
    }
    private <T> void task(java.util.concurrent.Callable<T> action,Consumer<T> success) {
        busy(true);
        Thread.ofVirtual().start(() -> {
            try {
                T value = action.call();
                Platform.runLater(() -> { busy(false); success.accept(value); updateActions(); });
            } catch(Exception e) {
                Platform.runLater(() -> { busy(false); status.setText("操作失败：" + e.getMessage()); });
            }
        });
    }
    private void search() {
        activeQuery = query.getText().trim();
        activeType = kind();
        activeGame = game.getText().trim();
        activeLoader = loader.getValue();
        results.getItems().clear();
        releases.getItems().clear();
        status.setText("正在搜索 Modrinth…");
        task(() -> service.search(activeQuery,activeType,activeGame,activeLoader,page), found -> {
            total = found.total();
            results.getItems().setAll(found.projects());
            status.setText("找到 " + total + " 个项目 · 第 " + (page+1) + " 页");
        });
    }
    private void select(ContentService.Project project) {
        releases.getItems().clear();
        compatibility.setText("");
        description.setText(project == null ? "选择项目查看介绍和版本" : project.description());
        updateActions();
        if (project == null) return;
        status.setText("正在获取匹配版本…");
        task(() -> service.releases(project,activeGame,activeLoader), versions -> {
            releases.getItems().setAll(versions);
            releases.getSelectionModel().selectFirst();
            status.setText(versions.isEmpty() ? "此项目没有匹配当前条件的版本" : "可选择文件版本后下载");
        });
    }
    private void showRelease(ContentService.Release release) {
        if (release == null) { compatibility.setText(""); updateActions(); return; }
        String dependencies = release.dependencies().isEmpty() ? "" : "\n必需依赖\n" + release.dependencies();
        compatibility.setText("版本 " + release.number() + " · " + release.loaders()
                + " · " + (release.size() < 1048576 ? String.format("%.1f KB",release.size()/1024.0) : String.format("%.1f MB",release.size()/1048576.0)) + dependencies);
        updateActions();
    }
    private void download() {
        var release = releases.getValue();
        if (release == null) return;
        if (activeType.equals("mod") && isModded()) {
            var project = results.getSelectionModel().getSelectedItem();
            status.setText("正在解析必需依赖…");
            task(() -> {
                new ModManager(service).install(instance,minecraft.instance(instance),project,release,
                        text -> Platform.runLater(() -> status.setText(text)));
                return release;
            }, done -> status.setText("安装完成，回首页选择 " + instance.id() + " 即可启动"));
            return;
        }
        if (!release.filename().equals(Path.of(release.filename()).getFileName().toString())
                || release.filename().contains("\\") || release.filename().contains("/")) {
            status.setText("下载渠道返回了无效文件名"); return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("保存 " + release.filename());
        chooser.setInitialFileName(release.filename());
        if (activeType.equals("resourcepack") && activeGame.matches("[A-Za-z0-9_.-]+")
                && !activeGame.equals(".") && !activeGame.equals("..")) {
            try {
                Path root = instance != null && instance.base().equals(activeGame) ? minecraft.instance(instance)
                    : SafeFiles.child(Path.of(System.getProperty("launcher.instances","instances")), SafeFiles.name(activeGame));
                Path packs = SafeFiles.child(root, "resourcepacks");
                Files.createDirectories(packs);
                chooser.setInitialDirectory(packs.toFile());
            } catch(java.io.IOException e) { status.setText("无法创建资源包目录：" + e.getMessage()); return; }
        }
        var target = chooser.showSaveDialog(stage);
        if (target == null) return;
        status.setText("正在下载并校验 " + release.filename());
        task(() -> { service.download(release,target.toPath()); return target; },
                file -> status.setText("下载完成：" + file.getAbsolutePath()));
    }
}
