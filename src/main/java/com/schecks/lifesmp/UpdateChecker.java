package com.schecks.lifesmp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModOrigin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Checks the configured GitHub repo's latest release against the running mod
 * version. Used by /lives update and the boot-time notice.
 *
 * The {@link #LOGGER} here is the single intentional path to the main server
 * console — used only to surface an "update available" line on boot. Routine
 * results go to {@link LifeLog} (the dedicated file) instead.
 */
public final class UpdateChecker {
    private static final Logger LOGGER = LoggerFactory.getLogger("lifesmp");

    public record Release(String version, String jarUrl, String jarName) {
        public boolean hasJar() { return jarUrl != null && jarName != null; }
    }

    /** Outcome of a version check. */
    public sealed interface CheckResult {}
    public record UpToDate(String version) implements CheckResult {}
    public record UpdateAvailable(String current, Release release) implements CheckResult {}
    public record CheckFailed(String reason) implements CheckResult {}

    private UpdateChecker() {}

    /** The running mod version (Loom-expanded mod_version), e.g. "1.0.0". */
    public static String currentVersion() {
        return FabricLoader.getInstance()
            .getModContainer(LifeSMP.MOD_ID)
            .map(c -> c.getMetadata().getVersion().getFriendlyString())
            .orElse("0.0.0");
    }

    public static CompletableFuture<CheckResult> checkAsync() {
        return CompletableFuture.supplyAsync(UpdateChecker::checkBlocking);
    }

    private static CheckResult checkBlocking() {
        String repo = LifeConfig.get().updateRepo == null ? "" : LifeConfig.get().updateRepo.trim();
        if (repo.isEmpty()) return new CheckFailed("update-repo is not configured");
        Release latest;
        try {
            latest = fetchLatestRelease(repo);
        } catch (Exception e) {
            return new CheckFailed(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        if (latest == null) return new CheckFailed("no releases found for " + repo);

        String current = currentVersion();
        if (compareVersions(latest.version(), current) > 0) {
            return new UpdateAvailable(current, latest);
        }
        return new UpToDate(current);
    }

    private static Release fetchLatestRelease(String repo) throws IOException, InterruptedException {
        URI uri = URI.create("https://api.github.com/repos/" + repo + "/releases/latest");
        HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(15))
            .build();
        HttpRequest req = HttpRequest.newBuilder()
            .uri(uri)
            .timeout(Duration.ofSeconds(20))
            .header("User-Agent", "lifesmp-mod")          // GitHub 403s requests without a UA
            .header("Accept", "application/vnd.github+json")
            .GET()
            .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() == 404) return null;        // repo has no releases yet
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IOException("GitHub API HTTP " + resp.statusCode());
        }
        JsonObject obj = JsonParser.parseString(resp.body()).getAsJsonObject();
        if (!obj.has("tag_name")) return null;
        String tag = obj.get("tag_name").getAsString();
        String version = (tag.startsWith("v") || tag.startsWith("V")) ? tag.substring(1) : tag;

        String jarUrl = null, jarName = null;
        if (obj.has("assets") && obj.get("assets").isJsonArray()) {
            JsonArray assets = obj.getAsJsonArray("assets");
            for (var el : assets) {
                JsonObject a = el.getAsJsonObject();
                String name = a.has("name") ? a.get("name").getAsString() : "";
                if (name.toLowerCase().endsWith(".jar") && a.has("browser_download_url")) {
                    jarName = name;
                    jarUrl = a.get("browser_download_url").getAsString();
                    break;
                }
            }
        }
        return new Release(version, jarUrl, jarName);
    }

    /**
     * Returns &gt;0 if a is newer than b, &lt;0 if older, 0 if equal.
     * Tolerant of "v" prefixes, differing segment counts, and non-numeric tails.
     */
    public static int compareVersions(String a, String b) {
        String[] pa = a.replaceAll("^[vV]", "").split("[.+\\-_]");
        String[] pb = b.replaceAll("^[vV]", "").split("[.+\\-_]");
        int n = Math.max(pa.length, pb.length);
        for (int i = 0; i < n; i++) {
            int x = i < pa.length ? parseIntSafe(pa[i]) : 0;
            int y = i < pb.length ? parseIntSafe(pb[i]) : 0;
            if (x != y) return Integer.compare(x, y);
        }
        return 0;
    }

    private static int parseIntSafe(String s) {
        try { return Integer.parseInt(s.trim()); }
        catch (NumberFormatException e) { return 0; }
    }

    /** Path of this mod's own jar on disk, or null if it can't be resolved (e.g. dev env). */
    public static Path ownJarPath() {
        Optional<ModContainer> mc = FabricLoader.getInstance().getModContainer(LifeSMP.MOD_ID);
        if (mc.isEmpty()) return null;
        ModOrigin origin = mc.get().getOrigin();
        if (origin.getKind() != ModOrigin.Kind.PATH) return null;
        List<Path> paths = origin.getPaths();
        if (paths == null || paths.isEmpty()) return null;
        Path p = paths.get(0);
        return Files.isRegularFile(p) ? p : null;
    }

    /**
     * Removes the currently-running jar after a new one has been downloaded.
     * Returns a human-readable status string describing what happened.
     */
    public static String removeOldJar(Path newJarPath) {
        Path old = ownJarPath();
        if (old == null) {
            return "(could not locate the old jar — if a duplicate appears, remove it manually before restart)";
        }
        Path oldAbs = old.toAbsolutePath().normalize();
        Path newAbs = newJarPath.toAbsolutePath().normalize();
        if (oldAbs.equals(newAbs)) {
            return "(replaced the jar in place)";
        }
        try {
            Files.delete(oldAbs);
            return "(removed old jar " + oldAbs.getFileName() + ")";
        } catch (IOException deleteFailed) {
            // Likely a Windows file lock — rename so Fabric ignores it (not a .jar anymore).
            try {
                Path disabled = oldAbs.resolveSibling(oldAbs.getFileName() + ".disabled");
                Files.move(oldAbs, disabled, StandardCopyOption.REPLACE_EXISTING);
                return "(old jar locked — renamed to " + disabled.getFileName() + ")";
            } catch (IOException renameFailed) {
                return "(WARNING: could not remove old jar " + oldAbs.getFileName()
                    + " — delete it manually before restarting or the server will fail to start"
                    + " with a duplicate mod id)";
            }
        }
    }

    /**
     * Boot-time check. Async; the only thing it ever writes to the main server
     * console is a single WARN line, and only when an update actually exists.
     */
    public static void checkOnBoot() {
        if (!LifeConfig.get().updateCheckOnBoot) return;
        checkAsync().thenAccept(result -> {
            switch (result) {
                case UpdateAvailable ua -> {
                    LOGGER.warn("[LifeSMP] A new version is available: {} (currently running {}). "
                        + "Run /lives update to install it.", ua.release().version(), ua.current());
                    LifeLog.info("[lifesmp] update available: {} (current {})",
                        ua.release().version(), ua.current());
                }
                case UpToDate ut ->
                    LifeLog.info("[lifesmp] up to date ({})", ut.version());
                case CheckFailed cf ->
                    LifeLog.warn("[lifesmp] update check failed: {}", cf.reason());
            }
        });
    }
}
