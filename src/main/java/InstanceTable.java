import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableSet;
import javafx.collections.SetChangeListener;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;

/**
 * 资源中心里的「我的实例」表格：列出所有已安装实例，
 * 平时鼠标悬停显示垃圾桶，打开「多选」后可以勾选、全选、批量删除。
 */
final class InstanceTable {
    private final InstanceDeleter deleter;
    private final ObservableSet<Minecraft.Version> checked = FXCollections.observableSet();
    private final BooleanProperty multi = new SimpleBooleanProperty(false);
    private final ListView<Minecraft.Version> list = new ListView<>();

    private InstanceTable(InstanceDeleter deleter) {
        this.deleter = deleter;
    }

    /** 替换资源中心里的「安装加载器」：模组与材质包右侧放实例表格。 */
    static void install(Scene scene, InstanceDeleter deleter) {
        if (deleter == null) return;
        Button loader = null;
        for (Node n : scene.getRoot().lookupAll(".tile-button"))
            if (n instanceof Button b && "安装加载器".equals(b.getText())) loader = b;
        if (loader == null || !(loader.getParent() instanceof HBox row)) return;

        InstanceTable table = new InstanceTable(deleter);
        VBox card = table.build();
        row.getChildren().remove(loader);
        row.getChildren().add(card);
        row.setAlignment(Pos.TOP_LEFT);
        HBox.setHgrow(card, Priority.ALWAYS);
        for (Node n : row.getChildren())
            if (n instanceof Button b) {             // 模组与材质包：竖长卡片，和表格等高
                b.setMinWidth(150);
                b.setPrefWidth(150);
                b.setMaxHeight(Double.MAX_VALUE);
                b.getStyleClass().add("tall-tile");
            }
        if (row.getParent() instanceof VBox page) {
            page.setAlignment(Pos.TOP_LEFT);
            page.setMaxHeight(Double.MAX_VALUE);
            VBox.setVgrow(row, Priority.ALWAYS);
        }
    }

    private VBox build() {
        // ---- 标题行
        Region marker = new Region();
        marker.getStyleClass().add("marker");
        Label title = new Label("我的实例");
        title.getStyleClass().add("section-title");
        Label count = new Label();
        count.getStyleClass().add("instance-count");
        count.textProperty().bind(Bindings.size(deleter.versions()).asString("共 %d 个"));
        ToggleButton multiButton = new ToggleButton("多选");
        multiButton.getStyleClass().add("multi-toggle");
        multiButton.selectedProperty().bindBidirectional(multi);
        Region grow = new Region();
        HBox.setHgrow(grow, Priority.ALWAYS);
        HBox header = new HBox(10, marker, title, count, grow, multiButton);
        header.setAlignment(Pos.CENTER_LEFT);

        // ---- 列表
        list.setItems(deleter.versions());
        list.getStyleClass().add("instance-table");
        list.setCellFactory(v -> new Row());
        list.setPlaceholder(new Label("还没有安装任何实例，去上面下载一个吧"));
        list.setMinHeight(150);
        VBox.setVgrow(list, Priority.ALWAYS);
        deleter.versions().addListener((ListChangeListener<Minecraft.Version>) c -> checked.retainAll(deleter.versions()));
        checked.addListener((SetChangeListener<Minecraft.Version>) c -> list.refresh());
        multi.addListener((o, was, on) -> { if (!on) checked.clear(); list.refresh(); });

        // ---- 多选操作条
        CheckBox all = new CheckBox("全选");
        all.getStyleClass().add("select-all");
        Runnable syncAll = () -> {
            int n = checked.size(), total = deleter.versions().size();
            all.setIndeterminate(n > 0 && n < total);
            all.setSelected(total > 0 && n == total);
        };
        checked.addListener((SetChangeListener<Minecraft.Version>) c -> syncAll.run());
        deleter.versions().addListener((ListChangeListener<Minecraft.Version>) c -> syncAll.run());
        all.setOnAction(e -> {
            if (checked.size() == deleter.versions().size()) checked.clear();
            else checked.addAll(deleter.versions());
            syncAll.run();
        });
        Label picked = new Label();
        picked.getStyleClass().add("instance-count");
        picked.textProperty().bind(Bindings.size(checked).asString("已选 %d 个"));
        Button remove = new Button("删除所选", trash());
        remove.getStyleClass().add("danger");
        remove.disableProperty().bind(Bindings.isEmpty(checked));
        remove.setOnAction(e -> deleter.delete(new ArrayList<>(ordered()), () -> {
            checked.clear();
            if (deleter.versions().isEmpty()) multi.set(false);
        }));
        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        HBox bar = new HBox(12, all, picked, gap, remove);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("batch-bar");
        bar.visibleProperty().bind(multi);
        bar.managedProperty().bind(multi);

        VBox card = new VBox(12, header, list, bar);
        card.getStyleClass().addAll("card", "instance-manager");
        card.setMinWidth(260);
        return card;
    }

    /** 按列表顺序返回勾选的实例。 */
    private List<Minecraft.Version> ordered() {
        return deleter.versions().stream().filter(checked::contains).toList();
    }

    private static Node trash() {
        StackPane icon = InstanceDeleter.trashIcon();
        icon.getStyleClass().remove("trash-button");
        icon.setMinSize(18, 18);
        icon.setPrefSize(18, 18);
        icon.setMaxSize(18, 18);
        return icon;
    }

    // ---------------------------------------------------------------- 行

    private final class Row extends ListCell<Minecraft.Version> {
        private final CheckBox box = new CheckBox();
        private final Label name = new Label();
        private final Label tag = new Label();
        private final Label id = new Label();
        private final StackPane trash = InstanceDeleter.trashIcon();
        private final HBox row;

        Row() {
            getStyleClass().add("instance-row");
            name.getStyleClass().add("instance-name");
            tag.getStyleClass().add("loader-tag");
            id.getStyleClass().add("instance-id");
            Region grow = new Region();
            HBox.setHgrow(grow, Priority.ALWAYS);
            name.setMinWidth(Region.USE_PREF_SIZE);
            id.setMinWidth(0);
            row = new HBox(10, box, name, tag, grow, id, trash);
            row.setAlignment(Pos.CENTER_LEFT);

            box.visibleProperty().bind(multi);
            box.managedProperty().bind(multi);
            box.setOnAction(e -> toggle());
            trash.visibleProperty().bind(hoverProperty().and(emptyProperty().not()).and(multi.not()));
            Tooltip.install(trash, new Tooltip("删除这个实例"));
            trash.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> {
                e.consume();
                if (getItem() != null) deleter.delete(List.of(getItem()), null);
            });
            // 多选模式下点整行也能勾选
            addEventHandler(MouseEvent.MOUSE_CLICKED, e -> {
                if (multi.get() && !isEmpty() && e.getTarget() != box && !box.isHover()) toggle();
            });
        }

        private void toggle() {
            Minecraft.Version v = getItem();
            if (v == null) return;
            if (checked.contains(v)) checked.remove(v); else checked.add(v);
        }

        @Override protected void updateItem(Minecraft.Version v, boolean empty) {
            super.updateItem(v, empty);
            setText(null);
            if (empty || v == null) { setGraphic(null); pseudoClassStateChanged(CHECKED, false); return; }
            String loader = v.loader();
            String full = deleter.name(v);
            name.setText(full.replaceFirst("^(原版|Fabric|NeoForge|Forge) ", ""));
            tag.setText(switch (loader) {
                case "fabric" -> "Fabric";
                case "neoforge" -> "NeoForge";
                case "forge" -> "Forge";
                default -> "原版";
            });
            tag.getStyleClass().setAll("loader-tag", "loader-" + loader);
            id.setText(v.id().equals(name.getText()) ? "" : v.id());
            box.setSelected(checked.contains(v));
            pseudoClassStateChanged(CHECKED, checked.contains(v));
            setGraphic(row);
        }
    }

    private static final javafx.css.PseudoClass CHECKED = javafx.css.PseudoClass.getPseudoClass("checked");
}
