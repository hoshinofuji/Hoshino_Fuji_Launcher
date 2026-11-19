import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

final class MapImport {
    static final int MAX_FILES = 50000;
    static final long MAX_TOTAL = 8L * 1024 * 1024 * 1024;
    static final long MAX_FILE = 1024L * 1024 * 1024;

    static Path install(Path source, Path instance) throws IOException {
        try (var lease = InstanceAccess.open(instance)) {
            Path saves = SafeFiles.child(instance, "saves");
            Files.createDirectories(saves);
            Path stage = Files.createTempDirectory(saves, ".map-import-");
            try {
                String name;
                if (Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)) {
                    Path world = findWorld(source, 3, new int[]{0});
                    if (world == null) throw new IOException("地图文件夹中没有 level.dat");
                    name = world.getFileName().toString();
                    copyWorld(world, stage);
                } else {
                    SafeFiles.regular(source);
                    name = unzip(source, stage, MAX_FILES, MAX_TOTAL);
                }
                if (!Files.isRegularFile(stage.resolve("level.dat"))) throw new IOException("无效地图");
                String base = name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
                if (base.isEmpty() || base.equals(".") || base.equals("..")) base = "world";
                SafeFiles.name(base);
                Path destination = SafeFiles.child(saves, base);
                for (int i = 2; Files.exists(destination, LinkOption.NOFOLLOW_LINKS); i++)
                    destination = SafeFiles.child(saves, base + " (" + i + ")");
                Files.move(stage, destination, StandardCopyOption.ATOMIC_MOVE);
                return destination;
            } finally { if (Files.exists(stage)) LoaderInstaller.removeTree(stage); }
        }
    }

    private static Path findWorld(Path directory, int depth, int[] examined) throws IOException {
        if (Files.isSymbolicLink(directory)) throw new IOException("地图包含符号链接");
        if (Files.isRegularFile(directory.resolve("level.dat"), LinkOption.NOFOLLOW_LINKS)) return directory;
        if (depth == 0) return null;
        try (var files = Files.list(directory)) {
            for (var iterator = files.iterator(); iterator.hasNext();) {
                Path child = iterator.next();
                if (++examined[0] > MAX_FILES) throw new IOException("地图文件过多");
                if (Files.isSymbolicLink(child)) throw new IOException("地图包含符号链接");
                if (Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
                    Path world = findWorld(child, depth - 1, examined);
                    if (world != null) return world;
                }
            }
        }
        return null;
    }

    private static void copyWorld(Path source, Path target) throws IOException {
        int count = 0;
        long total = 0;
        try (var paths = Files.walk(source)) {
            for (var iterator = paths.iterator(); iterator.hasNext();) {
                Path path = iterator.next();
                if (++count > MAX_FILES) throw new IOException("地图文件过多");
                if (Files.isSymbolicLink(path)) throw new IOException("地图包含符号链接");
                if (path.equals(source)) continue;
                String relative = source.relativize(path).toString().replace(File.separatorChar, '/');
                Path to = SafeFiles.child(target, relative);
                if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) Files.createDirectories(to);
                else {
                    SafeFiles.regular(path);
                    Files.createDirectories(to.getParent());
                    try (var in = Files.newInputStream(path); var out = Files.newOutputStream(to, StandardOpenOption.CREATE_NEW)) {
                        total += SafeFiles.copyLimited(in, out, Math.min(MAX_FILE, MAX_TOTAL - total));
                    }
                }
            }
        }
    }

    static String unzip(Path archive, Path target, int maxEntries, long maxBytes) throws IOException {
        try (var zip = new ZipFile(archive.toFile())) {
            String prefix = null;
            int count = 0;
            Set<String> names = new HashSet<>();
            for (var entries = zip.entries(); entries.hasMoreElements();) {
                var entry = entries.nextElement();
                if (++count > maxEntries) throw new IOException("压缩包条目过多");
                String name = entry.getName().replace('\\', '/');
                String checked = name.endsWith("/") ? name.substring(0, name.length()-1) : name;
                SafeFiles.child(target, checked);
                if (!names.add(name)) throw new IOException("压缩包包含重复条目");
                if (!entry.isDirectory() && (name.equals("level.dat") || name.endsWith("/level.dat"))) {
                    String candidate = name.substring(0, name.length()-"level.dat".length());
                    if (prefix == null || candidate.length() < prefix.length()) prefix = candidate;
                }
            }
            if (prefix == null) throw new IOException("压缩包没有 level.dat");
            long total = 0;
            for (var entries = zip.entries(); entries.hasMoreElements();) {
                var entry = entries.nextElement();
                String name = entry.getName().replace('\\', '/');
                if (!name.startsWith(prefix)) continue;
                String relative = name.substring(prefix.length());
                if (relative.isEmpty()) continue;
                if (relative.endsWith("/")) relative = relative.substring(0, relative.length()-1);
                Path to = SafeFiles.child(target, relative);
                if (entry.isDirectory()) Files.createDirectories(to);
                else {
                    if (entry.getSize() > Math.min(MAX_FILE, maxBytes-total)) throw new IOException("解压容量超过限制");
                    Files.createDirectories(to.getParent());
                    try (var in = zip.getInputStream(entry); var out = Files.newOutputStream(to, StandardOpenOption.CREATE_NEW)) {
                        total += SafeFiles.copyLimited(in, out, Math.min(MAX_FILE, maxBytes-total));
                    }
                }
            }
            String folder = prefix.isEmpty() ? archive.getFileName().toString().replaceFirst("(?i)\\.zip$", "")
                    : Path.of(prefix).getFileName().toString();
            return folder;
        }
    }

    private MapImport() {}
}
