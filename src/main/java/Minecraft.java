import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.regex.Pattern;
import java.util.stream.Stream;

final class Minecraft {
    private static final Pattern VARIABLE = Pattern.compile("\\$\\{([^}]+)}");
    private static final boolean WINDOWS = System.getProperty("os.name").startsWith("Windows");
    private static final String OS = WINDOWS ? "windows" : "osx";
    private static final String ARCH = System.getProperty("os.arch");
    private static final boolean ARM64 = ARCH.equals("aarch64") || ARCH.equals("arm64");

    final Path installation;

    Minecraft() {
        this(installationDirectory());
    }

    Minecraft(Path installation) {
        this.installation = installation.toAbsolutePath();
    }
    private final Path instances = Path.of(System.getProperty("launcher.instances", "instances"))
            .toAbsolutePath();

    record Version(String id, Path client, JsonObject manifest) {
        String base() {
            return manifest.has("inheritsFrom") ? manifest.get("inheritsFrom").getAsString() : id;
        }
        String loader() {
            if (id.contains("fabric-loader")) return "fabric";
            if (id.contains("neoforge")) return "neoforge";
            if (id.toLowerCase().contains("forge")) return "forge";
            return "vanilla";
        }
        @Override public String toString() { return id; }
    }

    record GameLaunch(ProcessBuilder builder, Path classpathJar, Path legacyClasspath, InstanceAccess instanceLock) implements AutoCloseable {
        @Override public void close() throws IOException {
            try {
                if (classpathJar != null) Files.deleteIfExists(classpathJar);
                if (legacyClasspath != null) Files.deleteIfExists(legacyClasspath);
            }
            finally { instanceLock.close(); }
        }
    }

    private static Path installationDirectory() {
        String custom = System.getenv("MC_LAUNCHER_GAME_DIR");
        if (custom != null && !custom.isBlank()) return Path.of(custom);
        Path home = Path.of(System.getProperty("user.home"));
        if (!WINDOWS) {
            return home.resolve("Library/Application Support/minecraft");
        }

        Path roaming = home.resolve("AppData/Roaming");
        String appData = System.getenv("APPDATA");
        if (appData != null && !appData.isBlank()) {
            roaming = Path.of(appData);
        }
        Path standard = roaming.resolve(".minecraft");
        Path store = storeCache();
        if (!Files.isDirectory(standard.resolve("versions")) && store != null) {
            Path game = store.resolve("game");
            if (Files.isDirectory(game.resolve("versions"))) {
                return game;
            }
        }
        return standard;
    }

    private static Path storeCache() {
        String local = System.getenv("LOCALAPPDATA");
        if (local == null || local.isBlank()) {
            return null;
        }
        return Path.of(local, "Packages", "Microsoft.4297127D64EC6_8wekyb3d8bbwe", "LocalCache", "Local");
    }

    static String runtimePlatform() {
        return WINDOWS ? "windows-x64" : ARM64 ? "mac-os-arm64" : "mac-os";
    }

    Path gameJava(String component) throws IOException {
        List<Path> roots = new ArrayList<>();
        String custom = System.getenv("MC_LAUNCHER_RUNTIME");
        if (custom != null && !custom.isBlank()) {
            roots.add(Path.of(custom));
        }
        roots.add(installation.resolve("runtime"));
        if (WINDOWS) {
            Path store = storeCache();
            if (store != null) {
                roots.add(store.resolve("runtime"));
            }
            for (String variable : List.of("ProgramFiles(x86)", "ProgramFiles")) {
                String base = System.getenv(variable);
                if (base != null && !base.isBlank()) {
                    roots.add(Path.of(base, "Minecraft Launcher", "runtime"));
                }
            }
        }

        roots.add(installation.resolve("runtime/local-mc-launcher"));
        String platform = runtimePlatform();
        for (Path root : roots) {
            Path executable = root.resolve(component).resolve(platform).resolve(component)
                    .resolve(WINDOWS ? "bin/java.exe" : "jre.bundle/Contents/Home/bin/java")
                    .toAbsolutePath();
            if (Files.isRegularFile(executable) && Files.isExecutable(executable)) {
                return executable;
            }
        }
        return null;
    }

    List<Version> versions() throws IOException {
        Path directory = SafeFiles.child(installation,"versions");
        if (!Files.isDirectory(directory)) return List.of();
        List<Version> versions = new ArrayList<>();
        try (Stream<Path> children = Files.list(directory)) {
            for (Path child : children.filter(Files::isDirectory).toList()) {
                String id = child.getFileName().toString();
                try {
                    versions.add(readVersion(id));
                } catch (RuntimeException | IOException ignored) {
                    // Partially installed profiles remain hidden until their manifest is valid.
                }
            }
        }
        return versions.stream().sorted(Comparator.comparing(Version::id).reversed()).toList();
    }

    Path instance(Version version) {
        try { return SafeFiles.child(instances,SafeFiles.name(version.id())); }
        catch (IOException e) { throw new IllegalArgumentException("无效实例路径",e); }
    }

    Version readVersion(String id) throws IOException {
        return readVersion(id, new java.util.HashSet<>());
    }

    private Version readVersion(String id, java.util.Set<String> visited) throws IOException {
        SafeFiles.name(id);
        require(visited.add(id) && visited.size() < 12, "版本继承存在循环");
        Path file = Installer.child(SafeFiles.child(installation,"versions"),id).resolve(id + ".json");
        JsonObject data;
        try (var reader = Files.newBufferedReader(file)) {
            data = JsonParser.parseReader(reader).getAsJsonObject();
        }
        require(id.equals(data.get("id").getAsString()), "版本 ID 不匹配");
        Path client = file.resolveSibling(id + ".jar");
        if (data.has("inheritsFrom")) {
            Version parent = readVersion(data.get("inheritsFrom").getAsString(),visited);
            client = parent.client();
            JsonObject merged = parent.manifest().deepCopy();
            data.entrySet().forEach(entry -> merged.add(entry.getKey(),entry.getValue()));
            JsonObject arguments = parent.manifest().getAsJsonObject("arguments").deepCopy();
            JsonObject additions = data.getAsJsonObject("arguments");
            if (additions != null) {
                for (String kind : List.of("jvm","game")) {
                    if (additions.has(kind)) arguments.getAsJsonArray(kind).addAll(additions.getAsJsonArray(kind));
                }
            }
            merged.add("arguments",arguments);
            var libraries = new com.google.gson.JsonArray();
            java.util.Set<String> coordinates = new java.util.HashSet<>();
            for (JsonElement entry : data.getAsJsonArray("libraries")) {
                libraries.add(entry);
                coordinates.add(libraryKey(entry.getAsJsonObject().get("name").getAsString()));
            }
            for (JsonElement entry : parent.manifest().getAsJsonArray("libraries")) {
                if (coordinates.add(libraryKey(entry.getAsJsonObject().get("name").getAsString()))) libraries.add(entry);
            }
            merged.add("libraries",libraries);
            data = merged;
        }
        require(Files.isRegularFile(client) && data.has("arguments") && data.has("javaVersion"),
                "缺少完整的原版基础文件");
        return new Version(id,client,data);
    }

    private static String libraryKey(String name) {
        String[] parts = name.split(":");
        return parts[0] + ":" + parts[1] + (parts.length > 3 ? ":" + parts[3] : "");
    }

    GameLaunch command(Version version, String player, int memoryMb) throws IOException {
        JsonObject data = version.manifest();
        String component = data.getAsJsonObject("javaVersion").get("component").getAsString();
        Path executable = gameJava(component);
        require(executable != null, "缺少运行时 " + component + "，请在官方列表中选择此版本并安装。");

        Path assets = SafeFiles.child(installation,"assets");
        String index = data.getAsJsonObject("assetIndex").get("id").getAsString();
        require(Files.isRegularFile(SafeFiles.child(assets,"indexes/" + SafeFiles.name(index) + ".json")),
                "缺少资源索引 " + index + "，请在官方列表中选择此版本并校验安装。");

        List<Path> classpath = new ArrayList<>();
        for (JsonElement entry : data.getAsJsonArray("libraries")) {
            JsonObject library = entry.getAsJsonObject();
            String name = library.get("name").getAsString();
            if (!usesLibrary(library)) continue;
            require(!library.has("natives"), "该版本使用旧式原生库配置。");
            JsonObject downloads = library.getAsJsonObject("downloads");
            JsonObject artifact = downloads == null ? null : downloads.getAsJsonObject("artifact");
            Path jar = Installer.child(SafeFiles.child(installation,"libraries"),artifact == null
                    ? LoaderInstaller.mavenPath(name) : artifact.get("path").getAsString());
            require(Files.isRegularFile(jar), "缺少依赖 " + name + "，请重新安装或校验此加载器。");
            classpath.add(jar);
        }
        classpath.add(version.client());

        Path instance = instance(version);
        var lock = InstanceAccess.open(instance);
        Path classpathJar = null;
        Path legacyClasspath = null;
        try {
        Path natives = Files.createDirectories(SafeFiles.child(instance,"natives"));
        String uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + player)
                .getBytes(StandardCharsets.UTF_8)).toString().replace("-", "");
        String separator = WINDOWS ? ";" : File.pathSeparator;
        String classpathValue = String.join(separator, classpath.stream().map(Path::toString).toList());
        Map<String, String> values = Map.ofEntries(
                Map.entry("auth_player_name", player),
                Map.entry("auth_uuid", uuid),
                Map.entry("auth_access_token", "0"),
                Map.entry("auth_session", "0"),
                Map.entry("clientid", ""),
                Map.entry("auth_xuid", ""),
                Map.entry("user_type", "msa"),
                Map.entry("version_name", version.id()),
                Map.entry("version_type", data.get("type").getAsString()),
                Map.entry("game_directory", instance.toString()),
                Map.entry("assets_root", assets.toString()),
                Map.entry("assets_index_name", index),
                Map.entry("natives_directory", natives.toString()),
                Map.entry("library_directory", SafeFiles.child(installation,"libraries").toString()),
                Map.entry("classpath_separator", separator),
                Map.entry("classpath", classpathValue),
                Map.entry("launcher_name", "HFML"),
                Map.entry("launcher_version", Launcher.VERSION));

        List<String> command = new ArrayList<>(List.of(executable.toString(),
                "-Xmx" + memoryMb + "M", "-Dfile.encoding=UTF-8",
                "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8"));
        if (!WINDOWS) {
            command.add("-Xdock:name=Minecraft " + version.id());
        }
        JsonObject arguments = data.getAsJsonObject("arguments");
        append(command, arguments.get("jvm"), values);
        for (int i = 0; i < command.size(); i++) {
            if (command.get(i).startsWith("-DignoreList="))
                command.set(i,command.get(i)+","+version.client().getFileName());
        }
        command.add(data.get("mainClass").getAsString());
        append(command, arguments.get("game"), values);
        if (WINDOWS) {
            // Keep the command line short even with many libraries or long installation paths.
            int classpathArgument = command.indexOf(classpathValue);
            require(classpathArgument >= 0, "该版本缺少 JVM 类路径参数。");
            classpathJar = writeClasspathJar(instance, classpath);
            command.set(classpathArgument, classpathJar.toString());
            if (version.loader().equals("forge") || version.loader().equals("neoforge")) {
                legacyClasspath = Files.createTempFile(instance,"legacy-classpath-",".txt");
                Files.writeString(legacyClasspath,classpathValue,StandardCharsets.UTF_8);
                command.add(command.indexOf(data.get("mainClass").getAsString()),
                        "-DlegacyClassPath.file="+legacyClasspath);
            }
        }
        var builder = new ProcessBuilder(command).directory(instance.toFile()).redirectErrorStream(true);
            ModManager.recover(instance);
            return new GameLaunch(builder, classpathJar, legacyClasspath, lock);
        } catch (IOException | RuntimeException failure) {
            lock.close();
            if (classpathJar != null) Files.deleteIfExists(classpathJar);
            if (legacyClasspath != null) Files.deleteIfExists(legacyClasspath);
            throw new IOException("实例暂不可用：" + failure.getMessage(),failure);
        }
    }

    private static Path writeClasspathJar(Path instance, List<Path> libraries) throws IOException {
        Manifest manifest = new Manifest();
        var attributes = manifest.getMainAttributes();
        attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attributes.put(Attributes.Name.CLASS_PATH, String.join(" ", libraries.stream()
                .map(path -> path.toUri().toASCIIString()).toList()));
        Path jar = Files.createTempFile(instance, "classpath-", ".jar");
        try {
            try (var output = new JarOutputStream(Files.newOutputStream(jar), manifest)) {
                output.flush();
            }
            return jar;
        } catch (IOException exception) {
            try {
                Files.deleteIfExists(jar);
            } catch (IOException cleanupFailure) {
                exception.addSuppressed(cleanupFailure);
            }
            throw exception;
        }
    }

    private static void append(List<String> command, JsonElement entries,
                               Map<String, String> values) throws IOException {
        for (JsonElement entry : entries.getAsJsonArray()) {
            if (entry.isJsonObject() && !allowed(entry.getAsJsonObject())) {
                continue;
            }
            JsonElement value = entry.isJsonObject() ? entry.getAsJsonObject().get("value") : entry;
            if (value.isJsonArray()) {
                for (JsonElement item : value.getAsJsonArray()) {
                    command.add(expand(item.getAsString(), values));
                }
            } else {
                command.add(expand(value.getAsString(), values));
            }
        }
    }

    private static String expand(String text, Map<String, String> values) throws IOException {
        var matcher = VARIABLE.matcher(text);
        var result = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            require(values.containsKey(key), "尚未支持启动参数 " + key);
            matcher.appendReplacement(result, java.util.regex.Matcher.quoteReplacement(values.get(key)));
        }
        return matcher.appendTail(result).toString();
    }

    static boolean usesLibrary(JsonObject library) {
        String name = library.get("name").getAsString();
        return allowed(library)
                && !(WINDOWS && (name.endsWith(":natives-windows-arm64") || name.endsWith(":natives-windows-x86")))
                && !(!WINDOWS && ARM64 && name.endsWith(":natives-macos"))
                && !(!WINDOWS && !ARM64 && name.endsWith(":natives-macos-arm64"));
    }

    private static boolean allowed(JsonObject item) {
        if (!item.has("rules")) {
            return true;
        }
        boolean allow = false;
        for (JsonElement entry : item.getAsJsonArray("rules")) {
            JsonObject rule = entry.getAsJsonObject();
            JsonObject os = rule.getAsJsonObject("os");
            if (os != null && ((os.has("name") && !OS.equals(os.get("name").getAsString()))
                    || (os.has("arch") && !ARCH.matches(os.get("arch").getAsString()))
                    || (os.has("version") && !Pattern.compile(os.get("version").getAsString())
                        .matcher(System.getProperty("os.version")).find()))) {
                continue;
            }
            JsonObject features = rule.getAsJsonObject("features");
            if (features != null && features.entrySet().stream().anyMatch(feature -> feature.getValue().getAsBoolean())) {
                continue;
            }
            allow = "allow".equals(rule.get("action").getAsString());
        }
        return allow;
    }

    private static void require(boolean condition, String message) throws IOException {
        if (!condition) {
            throw new IOException(message);
        }
    }
}
