import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.*;
import java.util.function.Consumer;

final class LoaderDialog {
    private final Stage stage = new Stage();
    private final ComboBox<String> type = new ComboBox<>();
    private final ComboBox<LoaderInstaller.Choice> choices = new ComboBox<>();
    private final Button refresh = new Button("获取版本");
    private final Button install = new Button("安装加载器");
    private final Label status = new Label();
    private boolean busy;

    LoaderDialog(Window owner,Minecraft minecraft,Minecraft.Version base,Consumer<Minecraft.Version> installed) {
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle("安装模组加载器 · " + base.base());
        type.getItems().addAll("Fabric","Forge","NeoForge");
        type.setValue("Fabric");
        choices.setPrefWidth(260);
        install.setDisable(true);
        status.setWrapText(true);
        var installer = new LoaderInstaller(minecraft);
        Runnable fetch = () -> {
            setBusy(true);
            status.setText("正在获取 " + type.getValue() + " 官方版本…");
            String selected = type.getValue();
            Thread.ofVirtual().start(() -> {
                try {
                    var versions = installer.choices(base.base(),selected);
                    Platform.runLater(() -> {
                        choices.getItems().setAll(versions);
                        choices.getSelectionModel().selectFirst();
                        setBusy(false);
                        status.setText(versions.isEmpty() ? "此游戏版本暂无匹配的加载器" : "选择版本后安装，将创建独立实例");
                    });
                } catch(Exception e) {
                    Platform.runLater(() -> { setBusy(false); status.setText("获取失败：" + e.getMessage()); });
                }
            });
        };
        type.setOnAction(e -> fetch.run());
        refresh.setOnAction(e -> fetch.run());
        install.setOnAction(e -> {
            var choice = choices.getValue();
            setBusy(true);
            Thread.ofVirtual().start(() -> {
                try {
                    var version = installer.install(base,choice,
                            text -> Platform.runLater(() -> status.setText(text)));
                    Platform.runLater(() -> {
                        setBusy(false);
                        installed.accept(version);
                        stage.close();
                    });
                } catch(Exception failure) {
                    Platform.runLater(() -> { setBusy(false); status.setText("安装失败：" + failure.getMessage()); });
                }
            });
        });
        VBox root = new VBox(14,new Label("基础游戏版本  " + base.base()),new HBox(8,type,choices,refresh),install,status);
        root.setPadding(new Insets(20));
        Scene scene = new Scene(root,580,230);
        scene.getStylesheets().add(LoaderDialog.class.getResource("/theme/launcher.css").toExternalForm());
        stage.setScene(scene);
        stage.setOnCloseRequest(e -> {
            if (busy) { e.consume(); status.setText("正在安装或获取版本，请等待完成"); }
        });
        stage.show();
        fetch.run();
    }
    private void setBusy(boolean value) {
        busy = value;
        type.setDisable(value);
        choices.setDisable(value);
        refresh.setDisable(value);
        install.setDisable(value || choices.getValue() == null);
    }
}
