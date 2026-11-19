import com.google.gson.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;

final class ContentService {
    private static final String API = "https://api.modrinth.com/v2/";
    record Project(String id, String title, String author, String description, String type) {
        @Override public String toString() { return title + "  ·  " + author; }
    }
    record Dependency(String project,String version) {}
    record Release(String name, String number, String loaders, String dependencies,
                   String url, String filename, String hash, long size, String id, List<String> games, List<Dependency> required) {
        @Override public String toString() { return name + "  ·  " + loaders; }
    }
    record Search(List<Project> projects, int total) {}

    Search search(String query, String type, String game, String loader, int page) throws IOException {
        List<List<String>> filters = new ArrayList<>();
        filters.add(List.of("project_type:" + type));
        if (!game.isBlank()) filters.add(List.of("versions:" + game));
        if (type.equals("mod") && !loader.equals("all")) filters.add(List.of("categories:" + loader));
        String route = "search?query=" + encode(query) + "&facets=" + encode(new Gson().toJson(filters))
                + "&limit=20&offset=" + page * 20 + "&index=downloads";
        var data = json(route).getAsJsonObject();
        List<Project> projects = new ArrayList<>();
        for (var hit : data.getAsJsonArray("hits")) {
            var p = hit.getAsJsonObject();
            projects.add(new Project(text(p,"project_id"),text(p,"title"),text(p,"author"),
                    text(p,"description"),type));
        }
        return new Search(projects, data.get("total_hits").getAsInt());
    }

    List<Release> releases(Project project, String game, String loader) throws IOException {
        String route = "project/" + encode(project.id()) + "/version?include_changelog=false";
        if (!game.isBlank()) route += "&game_versions=" + encode(new Gson().toJson(List.of(game)));
        if (project.type().equals("mod") && !loader.equals("all"))
            route += "&loaders=" + encode(new Gson().toJson(List.of(loader)));
        List<Release> releases = new ArrayList<>();
        for (var item : json(route).getAsJsonArray()) {
            Release release = parseRelease(item.getAsJsonObject());
            if (release != null) releases.add(release);
        }
        return releases;
    }

    record Resolved(String project,Release release) {}
    Resolved version(String id) throws IOException {
        var data = json("version/" + encode(id)).getAsJsonObject();
        Release release = parseRelease(data);
        if (release == null) throw new IOException("依赖没有可下载文件");
        return new Resolved(text(data,"project_id"),release);
    }

    private static Release parseRelease(JsonObject data) {
        JsonObject file = null;
        for (var candidate : data.getAsJsonArray("files")) {
            var next = candidate.getAsJsonObject();
            if (file == null || next.get("primary").getAsBoolean()) file = next;
            if (next.get("primary").getAsBoolean()) break;
        }
        if (file == null) return null;
        List<String> requiredText = new ArrayList<>();
        List<Dependency> required = new ArrayList<>();
        for (var dependency : data.getAsJsonArray("dependencies")) {
            var d = dependency.getAsJsonObject();
            if ("required".equals(text(d,"dependency_type"))) {
                String id = nullable(d,"project_id"), version = nullable(d,"version_id");
                required.add(new Dependency(id,version));
                requiredText.add(!id.isBlank() ? "modrinth.com/project/" + id
                        : !version.isBlank() ? "modrinth.com/version/" + version : nullable(d,"file_name"));
            }
        }
        var loaders = new ArrayList<String>();
        data.getAsJsonArray("loaders").forEach(value -> loaders.add(value.getAsString()));
        var games = new ArrayList<String>();
        data.getAsJsonArray("game_versions").forEach(value -> games.add(value.getAsString()));
        return new Release(text(data,"name"),text(data,"version_number"),String.join(", ",loaders),
                String.join("\n",requiredText),text(file,"url"),text(file,"filename"),
                text(file.getAsJsonObject("hashes"),"sha512"),file.get("size").getAsLong(),text(data,"id"),games,required);
    }

    void download(Release release, Path target) throws IOException {
        if (!release.hash().matches("[0-9a-fA-F]{128}")) throw new IOException("缺少有效的 SHA-512 校验值");
        SafeFiles.name(release.filename());
        if (release.size() < 0 || release.size() > SafeFiles.MAX_DOWNLOAD) throw new IOException("下载文件超过容量限制");
        target = target.toAbsolutePath();
        if (Files.isSymbolicLink(target)) throw new IOException("下载目标是符号链接");
        Files.createDirectories(target.getParent());
        Path part = Files.createTempFile(target.getParent(),".content-",".part");
        try {
            MessageDigest digest;
            try { digest = MessageDigest.getInstance("SHA-512"); }
            catch (NoSuchAlgorithmException e) { throw new AssertionError(e); }
            try (var input = new DigestInputStream(connect(release.url()),digest);
                 var output = Files.newOutputStream(part)) {
                SafeFiles.copyLimited(input,output,release.size());
            }
            if (Files.size(part) != release.size()
                    || !HexFormat.of().formatHex(digest.digest()).equalsIgnoreCase(release.hash()))
                throw new IOException("文件校验失败，请重试");
            Files.move(part,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(part); }
    }

    private static JsonElement json(String route) throws IOException {
        return JsonParser.parseString(new String(TrustedNetwork.metadata(API + route),StandardCharsets.UTF_8));
    }
    private static InputStream connect(String address) throws IOException { return TrustedNetwork.open(address); }
    private static String encode(String value) { return URLEncoder.encode(value,StandardCharsets.UTF_8); }
    private static String text(JsonObject object, String key) { return object.get(key).getAsString(); }
    private static String nullable(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? text(object,key) : "";
    }
}
