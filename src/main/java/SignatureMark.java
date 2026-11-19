import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;

/** 星野藤签名已经画进 poster-base.png 里（随海报一起缩放），这里只返回透明占位图。 */
final class SignatureMark {
    static Image image() {
        return new WritableImage(1, 1);
    }

    private SignatureMark() {}
}
