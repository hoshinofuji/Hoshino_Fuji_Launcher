import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 外部导入地图：先选要放进哪个实例，再选地图文件夹或 .zip 压缩包，
 * 复制到 instances/<实例>/saves/ 下面，进游戏后在单人游戏列表里就能看到。
 */
final class MapImporter {
    private MapImporter() {}

    static void open(Window owner, ObservableList<Minecraft.Version> versions, Minecraft.Version current,
                     Function<Minecraft.Version, String> namer, Consumer<String> status) {
        if (versions.isEmpty()) {
            Alert a = dialog(owner, Alert.AlertType.INFORMATION, "还没有实例");
            a.setContentText("请先在上面下载一个游戏版本，再导入地图。");
            a.getButtonTypes().setAll(new ButtonType("知道了", ButtonBar.ButtonData.OK_DONE));
            a.showAndWait();
            return;
        }
        // 第一步：选实例
        ComboBox<Minecraft.Version> instance = new ComboBox<>(versions);
        instance.setValue(current != null && versions.contains(current) ? current : versions.get(0));
        instance.setMaxWidth(Double.MAX_VALUE);
        instance.setCellFactory(v -> cell(namer));
        instance.setButtonCell(cell(namer));
        Label pick = new Label("导入到哪个实例（游戏版本）");
        pick.getStyleClass().add("field-title");
        Label hint = new Label("地图可以是一个存档文件夹（里面有 level.dat），也可以是下载来的 .zip 压缩包。");
        hint.setWrapText(true);
        hint.getStyleClass().add("status");
        VBox body = new VBox(10, pick, instance, hint);
        body.setAlignment(Pos.CENTER_LEFT);
        body.setPrefWidth(460);

        Alert ask = dialog(owner, Alert.AlertType.CONFIRMATION, "导入地图");
        ask.getDialogPane().setContent(body);
        ButtonType folder = new ButtonType("选择地图文件夹…", ButtonBar.ButtonData.OTHER);
        ButtonType zip = new ButtonType("选择 .zip 压缩包…", ButtonBar.ButtonData.OK_DONE);
        ask.getButtonTypes().setAll(new ButtonType("取消", ButtonBar.ButtonData.CANCEL_CLOSE), folder, zip);
        ask.getDialogPane().lookupButton(zip).getStyleClass().add("accent");
        ButtonType answer = ask.showAndWait().orElse(null);
        Minecraft.Version v = instance.getValue();
        if (v == null || (answer != folder && answer != zip)) return;

        // 第二步：选地图
        File source;
        if (answer == folder) {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("选择地图文件夹");
            source = chooser.showDialog(owner);
        } else {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("选择地图压缩包");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("地图压缩包", "*.zip"));
            source = chooser.showOpenDialog(owner);
        }
        if (source == null) return;

        String target = namer.apply(v);
        status.accept("正在导入地图…");
        Thread t = new Thread(() -> {
            try {
                Path world = MapImport.install(source.toPath(),new Minecraft().instance(v));
                String name = world.getFileName().toString();
                Platform.runLater(() -> {
                    status.accept("已导入地图「" + name + "」到 " + target);
                    Alert ok = dialog(owner, Alert.AlertType.INFORMATION, "已导入地图「" + name + "」");
                    ok.setContentText("启动「" + target + "」后，在「单人游戏」列表里就能看到这张地图。");
                    ok.getButtonTypes().setAll(new ButtonType("知道了", ButtonBar.ButtonData.OK_DONE));
                    ok.show();
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    status.accept("导入地图失败：" + e.getMessage());
                    Alert err = dialog(owner, Alert.AlertType.ERROR, "导入地图失败");
                    err.setContentText(String.valueOf(e.getMessage()));
                    err.getButtonTypes().setAll(new ButtonType("知道了", ButtonBar.ButtonData.OK_DONE));
                    err.show();
                });
            }
        }, "import-map");
        t.setDaemon(true);
        t.start();
    }

    // ---------------------------------------------------------------- helpers

    private static ListCell<Minecraft.Version> cell(Function<Minecraft.Version, String> namer) {
        return new ListCell<>() {
            @Override protected void updateItem(Minecraft.Version v, boolean empty) {
                super.updateItem(v, empty);
                setText(empty || v == null ? null : namer.apply(v));
            }
        };
    }

    private static Alert dialog(Window owner, Alert.AlertType type, String header) {
        Alert a = new Alert(type);
        if (owner != null) {
            a.initOwner(owner);
            if (owner.getScene() != null) a.getDialogPane().getStylesheets().addAll(owner.getScene().getStylesheets());
        }
        a.setTitle("导入地图");
        a.setHeaderText(header);
        a.setGraphic(null);
        a.getDialogPane().getStyleClass().add("hfml-dialog");
        return a;
    }
}
