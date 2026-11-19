import java.awt.Desktop;
import java.io.*;
import java.nio.file.*;
import java.util.function.Predicate;

final class SafeTrash {
    static void remove(Path path) throws IOException {
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.MOVE_TO_TRASH))
            throw new IOException("系统回收站不可用，文件已保留。请打开目录自行管理。");
        remove(path, file -> Desktop.getDesktop().moveToTrash(file));
    }

    static void remove(Path path, Predicate<File> trash) throws IOException {
        if (Files.isSymbolicLink(path)) throw new IOException("拒绝删除符号链接");
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return;
        if (!trash.test(path.toFile())) throw new IOException("移入回收站失败，文件已保留");
    }

    private SafeTrash() {}
}
