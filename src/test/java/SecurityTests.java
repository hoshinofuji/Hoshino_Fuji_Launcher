import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Runs without JavaFX windows or network requests. Each check targets a security boundary. */
public final class SecurityTests {
    private static int passed;
    @FunctionalInterface interface Checked { void run() throws Exception; }
    static void check(boolean value, String label) { if (!value) throw new AssertionError(label); passed++; }
    static void denied(Checked action, String label) throws Exception {
        try { action.run(); } catch (IOException expected) { passed++; return; }
        throw new AssertionError("Not rejected: " + label);
    }
    static Path zip(Path root, String file, Map<String, byte[]> entries) throws Exception {
        Path archive = root.resolve(file);
        try (var out = new ZipOutputStream(Files.newOutputStream(archive))) {
            for (var entry : entries.entrySet()) {
                out.putNextEntry(new ZipEntry(entry.getKey())); out.write(entry.getValue()); out.closeEntry();
            }
        }
        return archive;
    }
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("hfml-security-").toRealPath();
        try {
            for (String path : List.of("../escape", "/absolute", "a/../../bad", "C:/evil", "a\\evil", "a//b", "a/./b", "a/..", "NUL.jar", "a/CON", "CON .jar", "CONIN$", "COM¹.txt", "a.", "a "))
                denied(() -> SafeFiles.child(root, path), "unsafe path " + path);
            check(SafeFiles.child(root,"中文/世界.zip").startsWith(root), "unicode path");
            Path outside = Files.createDirectory(root.resolve("outside"));
            Path boundary = Files.createDirectory(root.resolve("boundary"));
            try {
                Files.createSymbolicLink(boundary.resolve("link"), outside);
                denied(() -> SafeFiles.child(boundary,"link/stolen"), "symlink traversal");
                denied(() -> SafeFiles.atomicCopy(boundary.resolve("link"), root.resolve("copy")), "non-regular import");
            } catch (UnsupportedOperationException | java.nio.file.FileSystemException unsupported) {
                System.out.println("SKIP symbolic-link creation: OS does not allow it (macOS/Linux CI must cover)");
            }
            Path keep = Files.writeString(root.resolve("keep.txt"), "original");
            denied(() -> SafeTrash.remove(keep, f -> false), "failed trash retains original");
            check(Files.readString(keep).equals("original"), "no permanent delete fallback");
            try (var out = new ByteArrayOutputStream()) {
                denied(() -> SafeFiles.copyLimited(new ByteArrayInputStream(new byte[11]),out,10), "stream quota");
            }
            for (String url : List.of("http://cdn.modrinth.com/file", "https://evil.example/file", "https://cdn.modrinth.com.evil.example/file", "https://cdn.modrinth.com@evil.example/file", "https://cdn.modrinth.com:444/file", "file:///etc/passwd", "https://127.0.0.1/file", "https://cdn.modrinth.com/file#fragment"))
                denied(() -> TrustedNetwork.validate(url), "URL allowlist");
            check(TrustedNetwork.validate("https://cdn.modrinth.com/data/file.jar").getHost().equals("cdn.modrinth.com"), "valid CDN");
            Path target = Files.createDirectory(root.resolve("extract"));
            Path hostile = zip(root,"hostile.zip",Map.of("world/level.dat",new byte[]{1},"../escaped",new byte[]{2}));
            denied(() -> MapImport.unzip(hostile,target,20,1024), "ZIP slip");
            check(!Files.exists(root.resolve("escaped")), "ZIP cannot escape");
            Path windows = zip(root,"windows.zip",Map.of("world/level.dat",new byte[]{1},"C:\\evil",new byte[]{2}));
            denied(() -> MapImport.unzip(windows,target,20,1024), "Windows ZIP path");
            Path bomb = zip(root,"bomb.zip",Map.of("level.dat",new byte[1024]));
            denied(() -> MapImport.unzip(bomb,target,20,16), "ZIP expanded-byte cap");
            Path many = zip(root,"many.zip",Map.of("level.dat",new byte[]{1},"data/a",new byte[]{2}));
            denied(() -> MapImport.unzip(many,target,1,1024), "ZIP entry cap");
            Path instance = root.resolve("instances/demo");
            Files.createDirectories(instance);
            try (var lock = InstanceAccess.open(instance)) {
                denied(() -> InstanceAccess.open(instance), "exclusive instance lock");
                denied(() -> MapImport.install(many,instance), "running instance blocks map import");
            }
            Path first = MapImport.install(many,instance);
            Path second = MapImport.install(many,instance);
            check(!first.equals(second) && Files.exists(first.resolve("level.dat")), "import creates unique world directories");
            denied(() -> MapImport.install(hostile,instance), "failed import");
            try (var files = Files.list(instance.resolve("saves"))) {
                check(files.noneMatch(p -> p.getFileName().toString().startsWith(".map-import-")), "failed staging cleaned");
            }
            check(ModManager.safeName("example.zip").equals("example.zip"), "resource ZIP accepted");
            denied(() -> ModManager.safeName("malware.exe"), "unsupported content extension");
            denied(() -> LoaderInstaller.mavenPath("x:../bad:1"), "Maven coordinate traversal");
            Path existing = Files.writeString(root.resolve("download.jar"),"existing");
            var service = new ContentService();
            var malformed = new ContentService.Release("bad","1","fabric","","https://evil.example/file","evil.jar","bad",3,"id",List.of("1.21"),List.of());
            denied(() -> service.download(malformed,existing), "missing trusted checksum");
            check(Files.readString(existing).equals("existing"), "bad metadata preserves installed file");
            System.out.println("PASS " + passed + " security assertions");
        } finally { LoaderInstaller.removeTree(root); }
    }
}
