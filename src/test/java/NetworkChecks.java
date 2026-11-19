import java.nio.file.*;
import java.util.*;

/** Optional real-channel smoke check. Downloads a small mod JAR as data; never executes it. */
public final class NetworkChecks {
    public static void main(String[] args) throws Exception {
        Path root=Files.createTempDirectory("hfml-network-").toRealPath();
        try {
            Minecraft game=new Minecraft(root.resolve("game"));
            var catalog=new Installer(game).catalog();
            if(catalog.isEmpty())throw new AssertionError("Empty Mojang catalog");
            var loaders=new LoaderInstaller(game);
            for(String type:List.of("Fabric","Forge","NeoForge")) {
                if(loaders.choices("1.21.1",type).isEmpty())throw new AssertionError("Empty loader choices: "+type);
            }
            var service=new ContentService();
            var projects=service.search("sodium","mod","1.21.1","fabric",0).projects();
            if(projects.isEmpty())throw new AssertionError("No Modrinth search results");
            var releases=service.releases(projects.getFirst(),"1.21.1","fabric");
            var release=releases.stream().filter(r->r.size()<10*1024*1024).findFirst().orElseThrow();
            Path target=root.resolve("download.jar");
            service.download(release,target);
            byte[] good=Files.readAllBytes(target);
            var bad=new ContentService.Release(release.name(),release.number(),release.loaders(),release.dependencies(),release.url(),release.filename(),"0".repeat(128),release.size(),release.id(),release.games(),release.required());
            try {service.download(bad,target);throw new AssertionError("Wrong checksum accepted");}
            catch(java.io.IOException expected) {if(!expected.getMessage().contains("校验"))throw expected;}
            if(!Arrays.equals(good,Files.readAllBytes(target)))throw new AssertionError("Failed download replaced existing data");
            try(var paths=Files.list(root)) {
                if(paths.anyMatch(p->p.getFileName().toString().endsWith(".part")))throw new AssertionError("Download temp leak");
            }
            System.out.println("PASS real channels: Mojang catalog, Fabric/Forge/NeoForge listings, Modrinth search/download/SHA-512; wrong checksum preserves existing file");
        } finally {LoaderInstaller.removeTree(root);}
    }
}
