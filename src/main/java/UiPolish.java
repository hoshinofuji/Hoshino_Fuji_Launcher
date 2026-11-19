import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import javafx.stage.Stage;
import javafx.util.Duration;
import javafx.util.StringConverter;

/**
 * 把 Launcher 搭好的界面调整成设计稿的样子：
 * 品牌标题、导航栏横跨海报顶部、实例徽章、字段图标、内存显示 “2048 MB”、功能块图标。
 * 每一步都独立 try/catch，任何一步找不到节点都不会影响启动器本身的功能。
 */
final class UiPolish {
    static final String TITLE = Launcher.NAME;
    private static boolean done;

    static void apply(Scene scene, SkinView skin) {
        if (done) return;
        done = true;
        step(() -> brand(scene));
        step(() -> navigationOverPoster(skin));
        step(() -> instanceBadge(scene));
        step(() -> fieldIcons(scene));
        step(() -> memoryUnits(scene));
        step(() -> tileIcons(scene));
        step(() -> hideInstanceTab(scene));
        step(() -> openFolderTile(scene));
        InstanceDeleter[] deleter = new InstanceDeleter[1];
        step(() -> deleter[0] = InstanceDeleter.install(scene));
        step(() -> InstanceTable.install(scene, deleter[0]));
        step(() -> LoaderChain.install(scene));
        step(() -> contentCenter(scene, deleter[0]));
        step(() -> languageFooter(scene));
        step(() -> hideStartTiles(scene));
        step(I18n::init);
        step(() -> posterTooltip(skin));
    }

    private static void step(Runnable r) {
        try { r.run(); } catch (RuntimeException e) { System.err.println("UiPolish: " + e); }
    }

    private static void brand(Scene scene) {
        if (scene.getWindow() instanceof Stage stage) stage.setTitle(TITLE);
        if (scene.lookup(".brand") instanceof Label brand) {
            brand.setText(TITLE);
            brand.setGraphic(image("/theme/brand-block.png", 30));
        }
    }

    /** 设计稿里导航栏压在海报顶部、靠中间排布；右侧内容区从导航下方开始。 */
    private static void navigationOverPoster(SkinView skin) {
        Node poster = skin.getParent();
        if (!(poster.getParent() instanceof HBox body)) return;
        if (!(body.getParent() instanceof VBox root)) return;
        VBox right = null;
        Node nav = null;
        for (Node n : body.getChildren())
            if (n instanceof VBox v && !v.getChildren().isEmpty() && v.getChildren().get(0).getStyleClass().contains("navigation")) {
                right = v; nav = v.getChildren().get(0);
            }
        if (right == null || !(nav instanceof HBox navBar)) return;

        int index = root.getChildren().indexOf(body);
        right.getChildren().remove(navBar);
        right.getStyleClass().add("right-column");
        Region spacer = new Region();
        spacer.getStyleClass().add("nav-spacer");
        spacer.minHeightProperty().bind(navBar.heightProperty());
        spacer.prefHeightProperty().bind(navBar.heightProperty());
        right.getChildren().add(0, spacer);

        StackPane layers = new StackPane();
        layers.getStyleClass().add("body-layers");
        root.getChildren().set(index, layers);
        layers.getChildren().addAll(body, navBar);
        VBox.setVgrow(layers, Priority.ALWAYS);
        StackPane.setAlignment(navBar, Pos.TOP_LEFT);
        navBar.getStyleClass().add("floating");
        navBar.setMaxHeight(Region.USE_PREF_SIZE);
        navBar.setMinWidth(0);

        var posterShown = poster.visibleProperty();
        navBar.translateXProperty().bind(Bindings.when(posterShown)
                .then(layers.widthProperty().multiply(0.30))
                .otherwise(0));
        var width = Bindings.when(posterShown)
                .then(layers.widthProperty().multiply(0.40))
                .otherwise(layers.widthProperty());
        navBar.prefWidthProperty().bind(width);
        navBar.maxWidthProperty().bind(width);
    }

    private static void instanceBadge(Scene scene) {
        Node selector = scene.lookup("#instance-selector");
        if (selector == null || !(selector.getParent().getParent() instanceof HBox row)) return;
        StackPane badge = new StackPane(image("/theme/instance-glyph.png", 44));
        badge.getStyleClass().add("instance-badge");
        badge.setMinSize(74, 74);
        badge.setPrefSize(74, 74);
        badge.setMaxSize(74, 74);
        row.getChildren().set(0, badge);
        row.setSpacing(30);
        row.setAlignment(Pos.BOTTOM_LEFT);
    }

    private static void fieldIcons(Scene scene) {
        for (Node n : scene.getRoot().lookupAll(".field-title")) {
            if (!(n instanceof Label label)) continue;
            String text = label.getText();
            if ("玩家名".equals(text)) label.setGraphic(glyph(
                    "M12 12 A4.5 4.5 0 1 0 12 3 A4.5 4.5 0 1 0 12 12 Z M3.5 22 C3.5 17 7.5 15 12 15 C16.5 15 20.5 17 20.5 22"));
            else if (text != null && text.startsWith("内存")) {
                label.setText("内存");
                label.setGraphic(glyph("M6 6 H18 V18 H6 Z M9.5 9.5 H14.5 V14.5 H9.5 Z "
                        + "M9 2 V6 M12 2 V6 M15 2 V6 M9 18 V22 M12 18 V22 M15 18 V22 "
                        + "M2 9 H6 M2 12 H6 M2 15 H6 M18 9 H22 M18 12 H22 M18 15 H22"));
            } else if ("当前实例".equals(text)) {
                label.getStyleClass().add("instance-title");
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void memoryUnits(Scene scene) {
        if (!(scene.lookup("#memory-setting") instanceof ComboBox<?> box)) return;
        ComboBox<Integer> memory = (ComboBox<Integer>) box;
        memory.setCellFactory(list -> memoryCell());
        memory.setButtonCell(memoryCell());
        memory.setConverter(new StringConverter<>() {
            @Override public String toString(Integer v) { return v == null ? "" : v + " MB"; }
            @Override public Integer fromString(String s) {
                try { return Integer.valueOf(s.replaceAll("[^0-9]", "")); } catch (RuntimeException e) { return memory.getValue(); }
            }
        });
    }

    /** 「模组与材质包」改为打开新的模组与资源中心（模组 / 资源包·材质包 / 光影包）。 */
    @SuppressWarnings("unchecked")
    private static void contentCenter(Scene scene, InstanceDeleter deleter) {
        if (!(scene.lookup("#instance-selector") instanceof ComboBox<?> box)) return;
        ComboBox<Minecraft.Version> versions = (ComboBox<Minecraft.Version>) box;
        for (Node n : scene.getRoot().lookupAll(".tile-button")) {
            if (!(n instanceof Button b) || !"模组与材质包".equals(b.getText())) continue;
            b.setText("模组与资源");
            b.setOnAction(e -> ContentCenter.open(scene.getWindow(), versions.getItems(), versions.getValue(),
                    v -> deleter != null ? deleter.name(v) : v.id()));
            // 资源中心：在「模组与资源」下面加「导入地图」
            if (b.getStyleClass().contains("tall-tile") && b.getParent() instanceof HBox row) {
                Button map = new Button("导入地图", ContentCenter.icon(
                        "M3 6 L9 3.5 L15 6 L21 3.5 V18 L15 20.5 L9 18 L3 20.5 Z M9 3.5 V18 M15 6 V20.5", 26));
                map.getStyleClass().addAll("tile-button", "tall-tile");
                map.setContentDisplay(javafx.scene.control.ContentDisplay.TOP);
                map.setMaxWidth(Double.MAX_VALUE);
                map.setMaxHeight(Double.MAX_VALUE);
                map.setOnAction(e -> MapImporter.open(scene.getWindow(), versions.getItems(), versions.getValue(),
                        v -> deleter != null ? deleter.name(v) : v.id(),
                        text -> { if (scene.lookup("#launch-status") instanceof Label l) l.setText(text); }));
                int i = row.getChildren().indexOf(b);
                VBox column = new VBox(12);
                column.setMinWidth(150);
                column.setPrefWidth(150);
                row.getChildren().set(i, column);
                b.setMaxWidth(Double.MAX_VALUE);
                column.getChildren().addAll(b, map);
                VBox.setVgrow(b, Priority.ALWAYS);
                VBox.setVgrow(map, Priority.ALWAYS);
            }
        }
    }

    /** 开始游戏页只保留启动相关的内容，原来下面的四个功能块都移到了资源中心。 */
    private static void hideStartTiles(Scene scene) {
        Node card = scene.lookup(".launch-card");
        if (card == null) return;
        for (Node n : card.lookupAll(".tile-button"))
            if (n.getParent() instanceof HBox row && row.getParent() == card) {
                row.setVisible(false);
                row.setManaged(false);
            }
    }

    /** 开始游戏页底部：左边切换语言，右边保留状态提示。 */
    private static void languageFooter(Scene scene) {
        if (!(scene.lookup("#launch-status") instanceof Label status) || !(status.getParent() instanceof VBox card)) return;
        int index = card.getChildren().indexOf(status);
        card.getChildren().remove(status);

        ComboBox<I18n.Lang> lang = new ComboBox<>();
        lang.getItems().setAll(I18n.Lang.values());
        lang.getStyleClass().add("language-picker");
        lang.setValue(I18n.current());
        lang.valueProperty().addListener((o, a, l) -> I18n.set(l));
        Node globe = ContentCenter.icon("M12 3 A9 9 0 1 0 12 21 A9 9 0 1 0 12 3 Z M3 12 H21 M12 3 C9 6 9 18 12 21 M12 3 C15 6 15 18 12 21", 20);
        globe.getStyleClass().add("language-globe");
        HBox picker = new HBox(8, globe, lang);
        picker.setAlignment(Pos.CENTER_LEFT);
        picker.setMinWidth(Region.USE_PREF_SIZE);

        status.setMaxWidth(Double.MAX_VALUE);
        status.setAlignment(Pos.CENTER_RIGHT);
        status.setTextAlignment(javafx.scene.text.TextAlignment.RIGHT);
        status.setMinHeight(0);
        HBox.setHgrow(status, Priority.ALWAYS);
        HBox footer = new HBox(16, picker, status);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.getStyleClass().add("card-footer");
        card.getChildren().add(index, footer);
        // 开机时的初始提示不显示，留给语言选择；之后的状态消息照常显示
        if ("选择一个已安装的原版版本".equals(status.getText()) || "已准备就绪".equals(status.getText())) status.setText("");
    }

    private static javafx.scene.control.ListCell<Integer> memoryCell() {
        return new javafx.scene.control.ListCell<>() {
            @Override protected void updateItem(Integer v, boolean empty) {
                super.updateItem(v, empty);
                setText(empty || v == null ? null : v + " MB");
            }
        };
    }

    /** 不再需要单独的「实例管理」页：当前实例在开始游戏页直接选择。 */
    private static void hideInstanceTab(Scene scene) {
        for (Node n : scene.getRoot().lookupAll(".nav-button"))
            if (n instanceof javafx.scene.control.ToggleButton t && "实例管理".equals(t.getText())) {
                t.setVisible(false);
                t.setManaged(false);
            }
    }

    /** 在开始游戏页加「添加本地资源」：打开当前实例的文件夹，方便放入地图、资源包等外部资源。 */
    @SuppressWarnings("unchecked")
    private static void openFolderTile(Scene scene) {
        Node card = scene.lookup(".launch-card");
        Node selector = scene.lookup("#instance-selector");
        Node listNode = scene.lookup("#instance-list");
        if (card == null || !(selector instanceof ComboBox<?> versions) || !(listNode instanceof javafx.scene.control.ListView<?> list)) return;
        Button original = null;
        for (Node n : scene.getRoot().lookupAll(".button"))
            if (n instanceof Button b && "打开实例目录".equals(b.getText())) original = b;
        HBox tiles = null;
        for (Node n : card.lookupAll(".tile-button"))
            if (n.getParent() instanceof HBox h) tiles = h;
        if (original == null || tiles == null) return;

        Button folder = new Button("添加本地资源", folderIcon());
        folder.getStyleClass().add("tile-button");
        folder.setContentDisplay(javafx.scene.control.ContentDisplay.TOP);
        folder.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(folder, Priority.ALWAYS);
        Button open = original;
        javafx.scene.control.ListView<Object> instances = (javafx.scene.control.ListView<Object>) list;
        folder.setOnAction(e -> {
            Object current = versions.getValue();
            if (current != null) instances.getSelectionModel().select(current);
            if (instances.getSelectionModel().getSelectedItem() != null) open.fire();
        });
        folder.disableProperty().bind(versions.valueProperty().isNull());
        Tooltip.install(folder, new Tooltip("打开当前实例的文件夹：地图放进 saves，资源包放进 resourcepacks，光影放进 shaderpacks"));
        tiles.getChildren().add(folder);
    }

    private static Node folderIcon() {
        SVGPath svg = new SVGPath();
        svg.setContent("M3 6 C3 5 3.5 4.5 4.5 4.5 H9.5 L11.5 7 H19.5 C20.5 7 21 7.5 21 8.5 V18 C21 19 20.5 19.5 19.5 19.5 H4.5 C3.5 19.5 3 19 3 18 Z M12 10.5 V16.5 M9 13.5 H15");
        svg.getStyleClass().add("line-icon");
        StackPane box = new StackPane(svg);
        box.setMinSize(26, 26);
        box.setPrefSize(26, 26);
        box.setMaxSize(26, 26);
        return box;
    }

    /** 设计稿：模组与材质包 = 方块，加载器 = 拼图，版本下载 = 下载。 */
    private static void tileIcons(Scene scene) {
        Node card = scene.lookup(".launch-card");
        if (card == null) return;
        for (Node n : card.lookupAll(".tile-button")) {
            if (!(n instanceof Button b)) continue;
            if ("模组与材质包".equals(b.getText())) b.setGraphic(Launcher.icon("cube"));
            else if ("加载器".equals(b.getText())) b.setGraphic(Launcher.icon("puzzle"));
        }
    }

    private static void posterTooltip(SkinView skin) {
        Parent poster = skin.getParent();
        for (Node n : poster.getChildrenUnmodifiable()) {
            if (n != skin && n.getProperties().get("javafx.scene.control.Tooltip") instanceof Tooltip t)
                Tooltip.uninstall(n, t);
        }
        if (skin.getProperties().get("javafx.scene.control.Tooltip") instanceof Tooltip old)
            Tooltip.uninstall(skin, old);
        Tooltip hint = new Tooltip("点一下，和星野藤打个招呼");
        hint.setShowDelay(Duration.millis(600));
        Tooltip.install(skin, hint);
    }

    // ---------------------------------------------------------------- helpers

    private static ImageView image(String path, double size) {
        var in = UiPolish.class.getResourceAsStream(path);
        ImageView view = new ImageView(in == null ? null : new Image(in));
        view.setFitWidth(size);
        view.setFitHeight(size);
        view.setPreserveRatio(true);
        view.setSmooth(true);
        return view;
    }

    private static Node glyph(String path) {
        SVGPath svg = new SVGPath();
        svg.setContent(path);
        svg.getStyleClass().addAll("line-icon", "field-icon");
        StackPane box = new StackPane(svg);
        box.setMinSize(20, 20);
        box.setPrefSize(20, 20);
        box.setMaxSize(20, 20);
        svg.setScaleX(0.78);
        svg.setScaleY(0.78);
        return box;
    }

    private UiPolish() {}
}
