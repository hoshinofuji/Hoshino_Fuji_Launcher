import com.google.gson.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

final class Installer {
    private static final String CATALOG = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    private static final String RUNTIMES = "https://launchermeta.mojang.com/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json";
    private final Minecraft minecraft;
    record Entry(String id, String type, String url, String hash) {
        @Override public String toString() {
            return id + "  ·  " + switch (type) {
                case "release" -> "正式版";
                case "snapshot" -> "快照 / 预发布";
                default -> "历史版本";
            };
        }
    }
    record FileJob(Path path, String url, String hash, long size, boolean executable) {
        static FileJob from(Path path, JsonObject data) {
            return new FileJob(path, str(data,"url"), str(data,"sha1"), data.get("size").getAsLong(), false);
        }
    }

    Installer(Minecraft minecraft) { this.minecraft = minecraft; }

    List<Entry> catalog() throws IOException {
        List<Entry> entries = new ArrayList<>();
        for (var item : json(fetch(CATALOG)).getAsJsonArray("versions")) {
            var value = item.getAsJsonObject();
            entries.add(new Entry(str(value,"id"), str(value,"type"), str(value,"url"), str(value,"sha1")));
        }
        return entries;
    }

    Minecraft.Version install(Entry entry, Consumer<String> progress) throws Exception {
        progress.accept("读取版本配置 " + entry.id());
        byte[] metadata = verifiedJson(entry.url(), entry.hash());
        JsonObject data = json(metadata);
        if (!entry.id().equals(str(data,"id")) || !data.has("arguments") || !data.has("javaVersion")) {
            throw new IOException("此历史版本使用旧式启动参数，当前启动器尚未支持。");
        }
        SafeFiles.name(entry.id());
        Path base = minecraft.installation;
        Path version = child(SafeFiles.child(base,"versions"), entry.id());
        Path client = child(version, entry.id() + ".jar");
        List<FileJob> files = new ArrayList<>();
        files.add(FileJob.from(client, data.getAsJsonObject("downloads").getAsJsonObject("client")));
        for (var item : data.getAsJsonArray("libraries")) {
            var lib = item.getAsJsonObject();
            if (!Minecraft.usesLibrary(lib)) continue;
            if (lib.has("natives")) throw new IOException("此版本使用旧式原生库，当前启动器尚未支持。");
            var artifact = lib.getAsJsonObject("downloads").getAsJsonObject("artifact");
            if (artifact != null) files.add(FileJob.from(child(SafeFiles.child(base,"libraries"), str(artifact,"path")), artifact));
        }
        installRuntime(str(data.getAsJsonObject("javaVersion"),"component"), progress);
        var index = data.getAsJsonObject("assetIndex");
        Path indexPath = child(SafeFiles.child(base,"assets/indexes"), str(index,"id") + ".json");
        download(FileJob.from(indexPath, index));
        var objects = json(Files.readAllBytes(indexPath)).getAsJsonObject("objects");
        Set<String> unique = new HashSet<>();
        for (var obj : objects.entrySet()) {
            var value = obj.getValue().getAsJsonObject();
            String hash = str(value,"hash");
            if (!hash.matches("[0-9a-f]{40}")) throw new IOException("资源哈希无效");
            if (!unique.add(hash)) continue;
            String suffix = hash.substring(0,2) + "/" + hash;
            files.add(new FileJob(child(SafeFiles.child(base,"assets/objects"),suffix),
                    "https://resources.download.minecraft.net/" + suffix, hash, value.get("size").getAsLong(), false));
        }
        batch(files, "游戏与资源", progress);
        Minecraft.Version installed = new Minecraft.Version(entry.id(),client,data);
        try (var check = minecraft.command(installed, "LocalPlayer", 2048)) {
            // Confirm this manifest can produce a launch command before publishing it.
        }
        Path manifest = child(version, entry.id() + ".json");
        Path temp = Files.createTempFile(version, ".manifest-", ".part");
        try {
            Files.write(temp, metadata);
            publish(temp, manifest);
        } finally { Files.deleteIfExists(temp); }
        return installed;
    }

    private void installRuntime(String component, Consumer<String> progress) throws Exception {
        if (minecraft.gameJava(component) != null) return;
        String platform = Minecraft.runtimePlatform();
        progress.accept("查找官方运行时 " + component);
        var platformData = json(fetch(RUNTIMES)).getAsJsonObject(platform);
        var choices = platformData == null ? null : platformData.getAsJsonArray(component);
        if (choices == null || choices.isEmpty()) throw new IOException("官方未提供 " + platform + " 的 " + component);
        var meta = choices.get(0).getAsJsonObject().getAsJsonObject("manifest");
        var entries = json(verifiedJson(str(meta,"url"), str(meta,"sha1"))).getAsJsonObject("files");
        Path parent = child(SafeFiles.child(minecraft.installation,"runtime/local-mc-launcher"),component).resolve(platform);
        Files.createDirectories(parent);
        Path stage = Files.createTempDirectory(parent, ".install-");
        try {
            List<FileJob> jobs = new ArrayList<>();
            Map<Path, Path> links = new LinkedHashMap<>();
            for (var item : entries.entrySet()) {
                Path path = child(stage,item.getKey());
                var spec = item.getValue().getAsJsonObject();
                switch (str(spec,"type")) {
                    case "directory" -> Files.createDirectories(path);
                    case "file" -> {
                        var raw = spec.getAsJsonObject("downloads").getAsJsonObject("raw");
                        jobs.add(new FileJob(path,str(raw,"url"),str(raw,"sha1"),raw.get("size").getAsLong(),
                                spec.has("executable") && spec.get("executable").getAsBoolean()));
                    }
                    case "link" -> {
                        Path target = Path.of(str(spec,"target"));
                        if (target.isAbsolute() || !path.getParent().resolve(target).normalize().startsWith(stage))
                            throw new IOException("运行时链接路径无效");
                        links.put(path,target);
                    }
                    default -> throw new IOException("未知运行时文件类型");
                }
            }
            batch(jobs, "Java 运行时", progress);
            for (var link : links.entrySet()) {
                Files.createDirectories(link.getKey().getParent());
                Files.createSymbolicLink(link.getKey(),link.getValue());
            }
            try { Files.move(stage, parent.resolve(component), StandardCopyOption.ATOMIC_MOVE); }
            catch (IOException e) { if (minecraft.gameJava(component) == null) throw e; }
        } finally {
            if (Files.exists(stage)) try (Stream<Path> paths = Files.walk(stage)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }

    private static void batch(List<FileJob> jobs, String label, Consumer<String> progress) throws Exception {
        progress.accept(label + "  0 / " + jobs.size());
        try (var pool = Executors.newFixedThreadPool(8)) {
            var done = new ExecutorCompletionService<Void>(pool);
            try {
                for (FileJob job : jobs) done.submit(() -> { download(job); return null; });
                for (int n = 1; n <= jobs.size(); n++) {
                    done.take().get();
                    progress.accept(label + "  " + n + " / " + jobs.size());
                }
            } catch (ExecutionException e) {
                throw new IOException(label + "安装失败：" + e.getCause().getMessage(), e.getCause());
            } finally { pool.shutdownNow(); }
        }
    }

    static void download(FileJob job) throws IOException {
        Path path = job.path();
        if (Files.isSymbolicLink(path)) throw new IOException("下载目标是符号链接");
        if (!job.hash().matches("[0-9a-fA-F]{40}")) throw new IOException("无效 SHA-1 校验值");
        if (job.size() < -1 || job.size() > SafeFiles.MAX_DOWNLOAD) throw new IOException("下载文件超过容量限制");
        if (Files.isRegularFile(path) && (job.size() < 0 || Files.size(path) == job.size())) {
            try (var input = Files.newInputStream(path)) {
                if (digest(input, OutputStream.nullOutputStream()).equals(job.hash())) return;
            }
        }
        Files.createDirectories(path.getParent());
        Path part = Files.createTempFile(path.getParent(), ".mc-", ".part");
        try {
            try (var in = connect(job.url()); var out = Files.newOutputStream(part)) {
                if (!digest(in,out).equals(job.hash()) || (job.size() >= 0 && Files.size(part) != job.size()))
                    throw new IOException("文件校验失败：" + path.getFileName());
            }
            if (job.executable() && !path.getFileSystem().getSeparator().equals("\\")
                    && !part.toFile().setExecutable(true,true)) throw new IOException("无法设置执行权限：" + path);
            publish(part,path);
        } finally { Files.deleteIfExists(part); }
    }

    static void publish(Path source, Path target) throws IOException {
        Files.move(source,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
    }

    private static String digest(InputStream input, OutputStream out) throws IOException {
        try {
            var digest = MessageDigest.getInstance("SHA-1");
            try (var stream = new DigestInputStream(input,digest)) { SafeFiles.copyLimited(stream,out,SafeFiles.MAX_DOWNLOAD); }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) { throw new AssertionError(e); }
    }

    private static byte[] verifiedJson(String url, String hash) throws IOException {
        byte[] bytes = fetch(url);
        if (!digest(new ByteArrayInputStream(bytes),OutputStream.nullOutputStream()).equals(hash))
            throw new IOException("官方清单校验失败");
        return bytes;
    }

    private static InputStream connect(String address) throws IOException { return TrustedNetwork.open(address); }
    static byte[] fetch(String url) throws IOException { return TrustedNetwork.metadata(url); }
    private static JsonObject json(byte[] bytes) {
        return JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject();
    }
    private static String str(JsonObject obj,String key) { return obj.get(key).getAsString(); }
    static Path child(Path parent,String name) throws IOException { return SafeFiles.child(parent,name); }
}
