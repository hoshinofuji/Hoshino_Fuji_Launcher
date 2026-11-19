import java.io.*;
import java.net.*;
import java.util.*;

final class TrustedNetwork {
    private static final Set<String> HOSTS = Set.of(
            "piston-meta.mojang.com", "launchermeta.mojang.com", "piston-data.mojang.com",
            "resources.download.minecraft.net", "libraries.minecraft.net", "launcher.mojang.com",
            "meta.fabricmc.net", "maven.fabricmc.net", "maven2.fabricmc.net",
            "files.minecraftforge.net", "maven.minecraftforge.net", "maven.neoforged.net",
            "api.modrinth.com", "cdn.modrinth.com");
    static URI validate(String address) throws IOException {
        try {
            URI uri = URI.create(address);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || !HOSTS.contains(uri.getHost().toLowerCase(Locale.ROOT))
                    || uri.getUserInfo() != null || (uri.getPort() != -1 && uri.getPort() != 443)
                    || uri.getFragment() != null) throw new IOException("下载地址不在受信渠道列表");
            return uri;
        } catch (IllegalArgumentException e) { throw new IOException("无效下载地址", e); }
    }

    static InputStream open(String address) throws IOException {
        URI current = validate(address);
        for (int redirects = 0; redirects <= 5; redirects++) {
            var connection = (HttpURLConnection) current.toURL().openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(45000);
            connection.setRequestProperty("User-Agent", "HFML/1.0 (open-source desktop launcher)");
            int code;
            try { code = connection.getResponseCode(); }
            catch (IOException e) { connection.disconnect(); throw e; }
            if (Set.of(301, 302, 303, 307, 308).contains(code)) {
                String next = connection.getHeaderField("Location");
                connection.disconnect();
                if (next == null) throw new IOException("下载重定向缺少目标");
                current = validate(current.resolve(next).toString());
                continue;
            }
            if (code != 200) {
                connection.disconnect();
                throw new IOException(code == 429 ? "渠道请求频繁，请稍后再试" : "下载返回 HTTP " + code);
            }
            if (connection.getContentLengthLong() > SafeFiles.MAX_DOWNLOAD) {
                connection.disconnect();
                throw new IOException("下载文件超过容量限制");
            }
            try {
                return new FilterInputStream(connection.getInputStream()) {
                    @Override public void close() throws IOException {
                        try { super.close(); } finally { connection.disconnect(); }
                    }
                };
            } catch (IOException e) { connection.disconnect(); throw e; }
        }
        throw new IOException("下载重定向次数过多");
    }

    static byte[] metadata(String url) throws IOException {
        try (var input = open(url); var output = new ByteArrayOutputStream()) {
            SafeFiles.copyLimited(input, output, 16L * 1024 * 1024);
            return output.toByteArray();
        }
    }

    private TrustedNetwork() {}
}
