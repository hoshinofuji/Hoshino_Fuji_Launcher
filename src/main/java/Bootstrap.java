import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class Bootstrap {
    public static void main(String[] args) throws Exception {
        Path self = Path.of(Bootstrap.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch").toLowerCase(Locale.ROOT);
        boolean x64 = arch.equals("amd64") || arch.equals("x86_64");
        boolean windows = os.startsWith("windows");
        String platform;
        if (windows && x64) {
            platform = "win";
        } else if (os.startsWith("mac") && x64) {
            platform = "mac";
        } else if (os.startsWith("mac") && List.of("arm64", "aarch64").contains(arch)) {
            platform = "mac-aarch64";
        } else {
            throw new IOException("此安装包支持 macOS 和 Windows x64。当前平台：" + os + " / " + arch);
        }

        Path temp = Files.createTempDirectory("mc-launcher-");
        int status;
        try {
            Path modules = Files.createDirectory(temp.resolve("javafx"));
            for (String module : List.of("base", "graphics", "controls")) {
                String name = "javafx-" + module + ".jar";
                unpack("lib/" + platform + "/" + name, modules.resolve(name));
            }
            Path gson = temp.resolve("gson.jar");
            unpack("lib/gson.jar", gson);
            String instances = System.getenv("MC_LAUNCHER_INSTANCES");
            if (instances == null || instances.isBlank()) {
                instances = self.resolveSibling("instances").toString();
            }
            Path java = Path.of(System.getProperty("java.home"), "bin", windows ? "java.exe" : "java");
            status = new ProcessBuilder(java.toString(), "--module-path", modules.toString(),
                    "--add-modules", "javafx.controls", "-Djava.net.useSystemProxies=true", "-Djavafx.cachedir=" + temp.resolve("natives"),
                    "-Dlauncher.instances=" + instances, "-cp", self + File.pathSeparator + gson,
                    "Launcher").inheritIO().start().waitFor();
        } finally {
            try (var paths = Files.walk(temp)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
        System.exit(status);
    }

    private static void unpack(String resource, Path output) throws IOException {
        try (var input = Bootstrap.class.getResourceAsStream("/" + resource)) {
            if (input == null) {
                throw new IOException("安装包缺少 " + resource);
            }
            Files.copy(input, output);
        }
    }
}
