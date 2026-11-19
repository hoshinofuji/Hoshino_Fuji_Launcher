import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Pattern;

final class LoaderInstaller {
    private final Minecraft minecraft;
    record Choice(String type,String version) {
        @Override public String toString() { return type + " " + version; }
    }
    LoaderInstaller(Minecraft minecraft) { this.minecraft = minecraft; }

    List<Choice> choices(String game,String type) throws IOException {
        List<Choice> choices = new ArrayList<>();
        if (type.equals("Fabric")) {
            for (var entry : json("https://meta.fabricmc.net/v2/versions/loader/" + safe(game)).getAsJsonArray()) {
                var loader = entry.getAsJsonObject().getAsJsonObject("loader");
                choices.add(new Choice(type,loader.get("version").getAsString()));
            }
        } else if (type.equals("Forge")) {
            var promos = json("https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json")
                    .getAsJsonObject().getAsJsonObject("promos");
            for (String suffix : List.of("recommended","latest")) {
                if (promos.has(game+"-"+suffix)) {
                    var choice = new Choice(type,promos.get(game+"-"+suffix).getAsString());
                    if (!choices.contains(choice)) choices.add(choice);
                }
            }
        } else {
            String[] numbers = game.split("\\.");
            if (numbers.length < 2 || !numbers[0].equals("1")) return choices;
            String prefix = numbers[1]+"."+(numbers.length > 2 ? numbers[2] : "0")+".";
            String xml = new String(Installer.fetch("https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml"),
                    java.nio.charset.StandardCharsets.UTF_8);
            var matcher = Pattern.compile("<version>([^<]+)</version>").matcher(xml);
            while (matcher.find()) if (matcher.group(1).startsWith(prefix))
                choices.add(new Choice(type,matcher.group(1)));
            Collections.reverse(choices);
        }
        return choices;
    }

    Minecraft.Version install(Minecraft.Version base,Choice choice,Consumer<String> progress) throws Exception {
        if (!base.loader().equals("vanilla")) base = minecraft.readVersion(base.base());
        if (choice.type().equals("Fabric")) return fabric(base,choice,progress);
        return forge(base,choice,progress);
    }

    private Minecraft.Version fabric(Minecraft.Version base,Choice choice,Consumer<String> progress) throws Exception {
        progress.accept("获取 Fabric 配置");
        JsonObject profile = json("https://meta.fabricmc.net/v2/versions/loader/" + safe(base.id()) + "/"
                + safe(choice.version()) + "/profile/json").getAsJsonObject();
        String id = safe(profile.get("id").getAsString());
        int n = 0;
        for (var entry : profile.getAsJsonArray("libraries")) {
            var library = entry.getAsJsonObject();
            String coordinate = library.get("name").getAsString();
            String path = mavenPath(coordinate);
            String root = library.has("url") ? library.get("url").getAsString() : "https://libraries.minecraft.net/";
            String url = root + (root.endsWith("/") ? "" : "/") + path;
            String hash = new String(Installer.fetch(url+".sha1"),java.nio.charset.StandardCharsets.US_ASCII).trim().split("\\s+")[0];
            if (!hash.matches("[0-9a-fA-F]{40}")) throw new IOException("依赖校验值无效");
            progress.accept("下载 Fabric 依赖 " + (++n));
            Installer.download(new Installer.FileJob(Installer.child(SafeFiles.child(minecraft.installation,"libraries"),path),
                    url,hash.toLowerCase(),-1,false));
            var artifact = new JsonObject();
            artifact.addProperty("path",path);
            artifact.addProperty("url",url);
            artifact.addProperty("sha1",hash);
            var downloads = new JsonObject();
            downloads.add("artifact",artifact);
            library.add("downloads",downloads);
        }
        return publish(profile,id);
    }

    private Minecraft.Version forge(Minecraft.Version base,Choice choice,Consumer<String> progress) throws Exception {
        boolean neo = choice.type().equals("NeoForge");
        String version = neo ? safe(choice.version()) : safe(base.id())+"-"+safe(choice.version());
        String url = neo ? "https://maven.neoforged.net/releases/net/neoforged/neoforge/"+version+"/neoforge-"+version+"-installer.jar"
                : "https://maven.minecraftforge.net/net/minecraftforge/forge/"+version+"/forge-"+version+"-installer.jar";
        Path cache = SafeFiles.child(minecraft.installation,"cache/local-mc-loaders");
        Files.createDirectories(cache);
        Path installer = cache.resolve(choice.type()+"-"+version+"-installer.jar");
        progress.accept("下载 " + choice.type() + " 官方安装器");
        String hash = new String(Installer.fetch(url+".sha1"),java.nio.charset.StandardCharsets.US_ASCII).trim().split("\\s+")[0];
        if (!hash.matches("[0-9a-fA-F]{40}")) throw new IOException("安装器校验值无效");
        Installer.download(new Installer.FileJob(installer,url,hash.toLowerCase(),-1,false));
        Path staging = Files.createTempDirectory(cache,"stage-");
        try {
            Files.writeString(staging.resolve("launcher_profiles.json"),"{\"profiles\":{}}");
            Path vanilla = Files.createDirectories(staging.resolve("versions").resolve(base.id()));
            Files.copy(base.client(),vanilla.resolve(base.id()+".jar"));
            Files.copy(SafeFiles.child(minecraft.installation,"versions").resolve(base.id()).resolve(base.id()+".json"),
                    vanilla.resolve(base.id()+".json"));
            // Reuse cached base libraries. The official installer keeps processor outputs in staging.
            Path stagedLibraries = Files.createDirectories(staging.resolve("libraries"));
            copyTree(SafeFiles.child(minecraft.installation,"libraries"),stagedLibraries);
            Path log = cache.resolve(choice.type()+"-"+version+".log");
            Path executable = minecraft.gameJava(base.manifest().getAsJsonObject("javaVersion").get("component").getAsString());
            if (executable == null) throw new IOException("请先校验安装原版 Java 运行时");
            progress.accept("运行 " + choice.type() + " 官方安装器，正在修补客户端…");
            Process process = new ProcessBuilder(executable.toString(),"-Djava.net.useSystemProxies=true",
                    "-Djava.awt.headless=true","-jar",installer.toString(),"--installClient",staging.toString())
                    .directory(staging.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
            if (!process.waitFor(12,java.util.concurrent.TimeUnit.MINUTES)) {
                process.destroyForcibly().waitFor();
                throw new IOException("安装器超时，可重试。详情 " + log);
            }
            if (process.exitValue()!=0) throw new IOException("安装器失败，详情 " + log);
            Path profileFile;
            try (var folders = Files.list(staging.resolve("versions"))) {
                profileFile = folders.filter(Files::isDirectory).filter(path -> !path.getFileName().toString().equals(base.id()))
                        .map(path -> path.resolve(path.getFileName()+".json")).filter(Files::isRegularFile)
                        .findFirst().orElseThrow(() -> new IOException("安装器没有生成版本配置"));
            }
            JsonObject profile = JsonParser.parseString(Files.readString(profileFile)).getAsJsonObject();
            String id = safe(profile.get("id").getAsString());
            copyTree(stagedLibraries,SafeFiles.child(minecraft.installation,"libraries"));
            Path versionDir = Files.createDirectories(SafeFiles.child(minecraft.installation,"versions").resolve(id));
            try (var files = Files.list(profileFile.getParent())) {
                for (Path file : files.filter(Files::isRegularFile).filter(path -> !path.equals(profileFile)).toList())
                    copy(file,versionDir.resolve(file.getFileName()));
            }
            return publish(profile,id);
        } finally { removeTree(staging); }
    }

    private Minecraft.Version publish(JsonObject profile,String id) throws IOException {
        Path directory = Files.createDirectories(Installer.child(SafeFiles.child(minecraft.installation,"versions"),id));
        Path target = directory.resolve(id+".json");
        Path temp = Files.createTempFile(directory,".profile-",".part");
        try {
            Files.writeString(temp,new GsonBuilder().setPrettyPrinting().create().toJson(profile));
            Installer.publish(temp,target);
        } finally { Files.deleteIfExists(temp); }
        return minecraft.readVersion(id);
    }

    static String mavenPath(String coordinate) throws IOException {
        String[] parts = coordinate.split(":");
        if (parts.length < 3 || parts.length > 4) throw new IOException("无效 Maven 依赖");
        return parts[0].replace('.','/')+"/"+safe(parts[1])+"/"+safe(parts[2])+"/"+parts[1]+"-"+parts[2]
                +(parts.length==4 ? "-"+safe(parts[3]) : "")+".jar";
    }
    private static String safe(String value) throws IOException {
        SafeFiles.name(value);
        if (!value.matches("[A-Za-z0-9_.+-]+") || value.equals(".") || value.equals("..")) throw new IOException("无效版本名称");
        return value;
    }
    private static JsonElement json(String url) throws IOException {
        return JsonParser.parseString(new String(Installer.fetch(url),java.nio.charset.StandardCharsets.UTF_8));
    }
    private static void copy(Path source,Path destination) throws IOException {
        SafeFiles.regular(source);
        if (Files.isSymbolicLink(destination)) throw new IOException("目标是符号链接");
        Files.createDirectories(destination.getParent());
        if (Files.exists(destination) && Files.mismatch(source,destination)==-1) return;
        Path temp = Files.createTempFile(destination.getParent(),".loader-",".part");
        try { Files.copy(source,temp,StandardCopyOption.REPLACE_EXISTING); Installer.publish(temp,destination); }
        finally { Files.deleteIfExists(temp); }
    }
    static void copyTree(Path source,Path target) throws IOException {
        if (!Files.isDirectory(source)) return;
        try (var paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                if (Files.isSymbolicLink(path)) throw new IOException("依赖目录包含符号链接");
                if (Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)) {
                    String relative=source.relativize(path).toString().replace(java.io.File.separatorChar,'/');
                    copy(path,SafeFiles.child(target,relative));
                }
            }
        }
    }
    static void removeTree(Path directory) throws IOException {
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }
}
