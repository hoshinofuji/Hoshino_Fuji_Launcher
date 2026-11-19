import com.google.gson.*;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

final class ModManager {
    private final ContentService service;
    ModManager(ContentService service) { this.service = service; }

    static void recover(Path instance) throws IOException {
        Path backup = SafeFiles.child(instance,".mods-backup");
        Path mods = SafeFiles.child(instance,"mods");
        if (Files.exists(backup)) {
            if (!Files.exists(mods)) Files.move(backup,mods,StandardCopyOption.ATOMIC_MOVE);
            else LoaderInstaller.removeTree(backup);
        }
    }

    void install(Minecraft.Version instance,Path directory,ContentService.Project project,
                 ContentService.Release selected,Consumer<String> progress) throws Exception {
        if (instance.loader().equals("vanilla")) throw new IOException("请先安装并选择模组加载器实例");
        Files.createDirectories(directory);
        try (var lease = InstanceAccess.open(directory)) {
            recover(directory);
            Map<String,ContentService.Release> plan = new LinkedHashMap<>();
            resolve(project.id(),selected,instance,new HashSet<>(),plan);
            Path current = SafeFiles.child(directory,"mods");
            Path staged = Files.createTempDirectory(directory,".mods-next-");
            Path backup = SafeFiles.child(directory,".mods-backup");
            try {
                LoaderInstaller.copyTree(current,staged);
                Path registry = staged.resolve(".hoshinofuji-installed.json");
                JsonObject installed = Files.isRegularFile(registry)
                        ? JsonParser.parseString(Files.readString(registry)).getAsJsonObject() : new JsonObject();
                Set<String> filenames = new HashSet<>();
                for (var entry : plan.entrySet()) {
                    var release = entry.getValue();
                    String filename = safeName(release.filename());
                    if (!filename.toLowerCase(java.util.Locale.ROOT).endsWith(".jar"))
                        throw new IOException("模组文件必须为 JAR");
                    for (String other : installed.keySet()) {
                        if (!other.equals(entry.getKey()) && filename.equalsIgnoreCase(installed.getAsJsonObject(other).get("file").getAsString()))
                            throw new IOException("模组文件名与现有项目冲突：" + filename);
                    }
                    if (Files.exists(staged.resolve(filename)) && (!installed.has(entry.getKey())
                            || !filename.equalsIgnoreCase(installed.getAsJsonObject(entry.getKey()).get("file").getAsString())))
                        throw new IOException("同名本地模组已存在：" + filename);
                    if (!filenames.add(filename.toLowerCase(Locale.ROOT))) throw new IOException("模组文件名冲突：" + filename);
                    progress.accept("下载模组及依赖 " + filename);
                    if (installed.has(entry.getKey())) {
                        String old = safeName(installed.getAsJsonObject(entry.getKey()).get("file").getAsString());
                        Files.deleteIfExists(staged.resolve(old));
                    }
                    service.download(release,staged.resolve(filename));
                    var state = new JsonObject();
                    state.addProperty("file",filename);
                    state.addProperty("version",release.id());
                    installed.add(entry.getKey(),state);
                }
                Files.writeString(registry,new Gson().toJson(installed));
                if (Files.exists(current)) Files.move(current,backup,StandardCopyOption.ATOMIC_MOVE);
                try { Files.move(staged,current,StandardCopyOption.ATOMIC_MOVE); }
                catch(IOException failure) { recover(directory); throw failure; }
                if (Files.exists(backup)) LoaderInstaller.removeTree(backup);
            } finally { if (Files.exists(staged)) LoaderInstaller.removeTree(staged); }
        }
    }

    private void resolve(String project,ContentService.Release version,Minecraft.Version instance,
                         Set<String> visiting,Map<String,ContentService.Release> plan) throws IOException {
        if (!version.games().contains(instance.base()) || !Arrays.asList(version.loaders().split(", ")).contains(instance.loader()))
            throw new IOException("模组与实例的游戏版本或加载器不兼容：" + version.name());
        if (plan.containsKey(project)) {
            if (!plan.get(project).id().equals(version.id())) throw new IOException("依赖版本冲突，请选择其他模组版本");
            return;
        }
        if (!visiting.add(project) || visiting.size()>80) throw new IOException("模组依赖存在循环或数量过多");
        for (var dependency : version.required()) {
            ContentService.Release release;
            String dependencyProject = dependency.project();
            if (!dependency.version().isBlank()) {
                var resolved = service.version(dependency.version());
                dependencyProject = resolved.project();
                release = resolved.release();
            } else if (!dependencyProject.isBlank()) {
                var choices = service.releases(new ContentService.Project(dependencyProject,"","","","mod"),instance.base(),instance.loader());
                if (choices.isEmpty()) throw new IOException("找不到兼容的必需依赖 " + dependencyProject);
                release = choices.getFirst();
            } else throw new IOException("此模组含手动文件依赖，请从项目官网核对");
            resolve(dependencyProject,release,instance,visiting,plan);
        }
        visiting.remove(project);
        plan.put(project,version);
    }

    static String safeName(String filename) throws IOException {
        SafeFiles.name(filename);
        String lower = filename.toLowerCase(java.util.Locale.ROOT);
        if (!lower.endsWith(".jar") && !lower.endsWith(".zip")) throw new IOException("仅支持 JAR / ZIP 文件");
        return filename;
    }
}
