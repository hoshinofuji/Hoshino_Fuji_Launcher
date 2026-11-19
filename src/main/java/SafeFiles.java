import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Locale;

final class SafeFiles {
    static final long MAX_DOWNLOAD = 1024L * 1024 * 1024;

    static String name(String value) throws IOException {
        if (value == null || value.isBlank() || value.equals(".") || value.equals("..")
                || value.endsWith(".") || value.endsWith(" ")
                || value.chars().anyMatch(c -> c < 32 || c == 127 || "\\/:*?\"<>|".indexOf(c) >= 0))
            throw new IOException("无效文件名");
        String stem = value.split("\\.")[0].stripTrailing().toUpperCase(Locale.ROOT);
        if (stem.matches("CON|PRN|AUX|NUL|CONIN\\$|CONOUT\\$|COM[1-9¹²³]|LPT[1-9¹²³]")) throw new IOException("不支持保留设备文件名");
        return value;
    }

    static Path child(Path base, String relative) throws IOException {
        if (relative == null || relative.isEmpty() || relative.startsWith("/") || relative.contains("\\")
                || relative.contains(":")) throw new IOException("无效相对路径");
        for (String segment : relative.split("/", -1)) name(segment);
        Path root = base.toAbsolutePath().normalize();
        if (Files.exists(root)) root = root.toRealPath();
        Path result = root.resolve(relative).normalize();
        if (!result.startsWith(root) || result.equals(root)) throw new IOException("路径越界");
        Path cursor = root;
        for (Path segment : root.relativize(result)) {
            cursor = cursor.resolve(segment);
            if (Files.isSymbolicLink(cursor)) throw new IOException("拒绝符号链接路径");
            if (Files.exists(cursor, LinkOption.NOFOLLOW_LINKS)
                    && !cursor.toRealPath().startsWith(root)) throw new IOException("路径越界");
        }
        return result;
    }

    static void regular(Path path) throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isRegularFile() || Files.isSymbolicLink(path)) throw new IOException("需要普通文件");
    }

    static long copyLimited(InputStream input, OutputStream output, long limit) throws IOException {
        if (limit < 0) throw new IOException("无效容量限制");
        byte[] buffer = new byte[65536];
        long total = 0;
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException("操作已取消");
            if (count > limit - total) throw new IOException("文件或解压内容超过容量限制");
            output.write(buffer, 0, count);
            total += count;
        }
        return total;
    }

    static void atomicCopy(Path source, Path target) throws IOException {
        regular(source);
        if (Files.isSymbolicLink(target)) throw new IOException("目标是符号链接");
        Files.createDirectories(target.getParent());
        Path part = Files.createTempFile(target.getParent(), ".import-", ".part");
        try {
            try (var in = Files.newInputStream(source); var out = Files.newOutputStream(part)) {
                copyLimited(in, out, MAX_DOWNLOAD);
            }
            Files.move(part, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(part); }
    }

    private SafeFiles() {}
}
