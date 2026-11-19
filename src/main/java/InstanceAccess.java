import java.io.IOException;
import java.nio.channels.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.HexFormat;

final class InstanceAccess implements AutoCloseable {
    private final FileChannel channel;

    private InstanceAccess(FileChannel channel) { this.channel = channel; }

    static InstanceAccess open(Path instance) throws IOException {
        Path path = instance.toAbsolutePath().normalize();
        Path checked = SafeFiles.child(path.getParent(), path.getFileName().toString());
        Files.createDirectories(checked.getParent());
        if (Files.exists(checked)) checked = checked.toRealPath();
        Path lockDirectory = SafeFiles.child(checked.getParent(), ".hfml-locks");
        Files.createDirectories(lockDirectory);
        String key;
        try {
            key = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(checked.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) { throw new AssertionError(e); }
        Path file = SafeFiles.child(lockDirectory, key + ".lock");
        FileChannel channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        try {
            if (channel.tryLock() == null) throw new IOException("实例正在运行或修改中");
            // Respect a still-running copy of the older launcher during migration.
            Path legacy = SafeFiles.child(checked, ".mods.lock");
            if (Files.exists(legacy)) {
                try (var old = FileChannel.open(legacy, StandardOpenOption.WRITE); var lease = old.tryLock()) {
                    if (lease == null) throw new IOException("旧版启动器正在使用此实例");
                }
            }
            return new InstanceAccess(channel);
        } catch (IOException | RuntimeException failure) {
            channel.close();
            throw new IOException("实例正在运行、修改中或无法锁定", failure);
        }
    }

    @Override public void close() throws IOException { channel.close(); }
}
