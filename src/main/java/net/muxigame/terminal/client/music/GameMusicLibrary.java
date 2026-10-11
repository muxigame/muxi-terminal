package net.muxigame.terminal.client.music;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.fml.ModList;
import java.util.*;
import java.util.concurrent.*;
import java.nio.file.*;
import com.google.gson.*;

/** Cached, incrementally published actual files. Pack scanning never runs on the render thread. */
final class GameMusicLibrary {
    record Track(String id, ResourceLocation audio, String title, String group, String provider) {}
    private static final ExecutorService SCAN = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Muxi music catalog"); t.setDaemon(true); return t;
    });
    private static volatile long generation, resourceGeneration;
    private static Future<?> worker;
    private static boolean started, loading;
    private static int scans;
    private static String error = "";
    private static final MusicCatalogFilter FILTER = new MusicCatalogFilter();
    private record Metrics(double minimumSeconds,int examined,int filteredShort,int filteredCategory,
                           int cacheHits,int unknownDuration,long encodedBytes,double millis) {}
    private static volatile Metrics metrics = new Metrics(10,0,0,0,0,0,0,0);
    static JsonObject diagnostics() { return new Gson().toJsonTree(metrics).getAsJsonObject(); }
    private static final List<Track> catalog = new ArrayList<>();
    static List<Track> tracks() { ensure(); return List.copyOf(catalog); }
    static Track find(String id) { return tracks().stream().filter(t -> t.id().equals(id)).findFirst().orElse(null); }
    static boolean loading() { ensure(); return loading; }
    static String error() { return error; }
    static int scans() { return scans; }
    static long resourceGeneration() { return resourceGeneration; }
    static void reloaded() { resourceGeneration++; FILTER.clear(); invalidate(); }
    static long generation() { return generation; }
    static void invalidate() {
        generation++; if (worker != null) worker.cancel(true);
        worker = null; started = false; loading = false; error = ""; catalog.clear();
    }
    private static void ensure() {
        if (started) return;
        started = true; loading = true; scans++;
        Minecraft mc = Minecraft.getInstance(); ResourceManager resources = mc.getResourceManager();
        long token = generation, resourcesToken = resourceGeneration;
        Path filterConfig = mc.gameDirectory.toPath().resolve("config/muxi_terminal/music-catalog.json");
        Map<String, String> providers = new HashMap<>();
        for (var mod : ModList.get().getMods()) {
            String key = "modmenu.nameTranslation." + mod.getModId();
            providers.put(mod.getModId(), MusicSecurity.title(I18n.exists(key) ? I18n.get(key) : mod.getDisplayName()));
        }
        // Minecraft's pack metadata supplies its official name, just like mod metadata.
        providers.putIfAbsent("minecraft", "Minecraft");
        worker = SCAN.submit(() -> {
            long began = System.nanoTime();
            double minimum = minimumSeconds(filterConfig);
            int[] counts = new int[5]; long[] encoded = new long[1];
            List<Track> batch = new ArrayList<>(); Set<ResourceLocation> seen = new HashSet<>();
            Map<String, String> groups = new HashMap<>();
            try {
                for (var pack : resources.listPacks().toList()) {
                    for (String namespace : new TreeSet<>(pack.getNamespaces(PackType.CLIENT_RESOURCES))) {
                        pack.listResources(PackType.CLIENT_RESOURCES, namespace, "sounds", (file, supplier) -> {
                            if (Thread.currentThread().isInterrupted() || token != generation) throw new CancellationException();
                            String path = file.getPath();
                            if (!path.endsWith(".ogg") || !(path.startsWith("sounds/music/") || path.contains("/music/")) || !seen.add(file)) return;
                            if (!MusicCatalogFilter.musicPath(path)) { counts[2]++; return; }
                            var resource = resources.getResource(file);
                            if (resource.isEmpty()) return;
                            counts[0]++;
                            try {
                                String cacheKey = resourcesToken + "\n" + resource.get().sourcePackId() + "\n" + file;
                                var checked = FILTER.inspect(cacheKey, resource.get()::open, minimum);
                                if (checked.cacheHit()) counts[3]++; else encoded[0] += checked.metadata().encodedBytes();
                                if (checked.metadata().seconds() < 0) counts[4]++;
                                if (!checked.keep()) { counts[1]++; return; }
                            } catch (java.io.InterruptedIOException cancelled) { throw new CancellationException(); }
                              catch (java.io.IOException unknown) { counts[4]++; }
                            String provider = namespace;
                            String packId = resource.get().sourcePackId();
                            if (packId.startsWith("mod/") && providers.containsKey(packId.substring(4))) provider = packId.substring(4);
                            String label = providers.getOrDefault(provider, provider);
                            ResourceLocation audio = Sound.SOUND_LISTER.fileToId(file);
                            batch.add(new Track(UUID.randomUUID().toString(), audio,
                                MusicSecurity.title(audio.getPath().substring(audio.getPath().lastIndexOf('/') + 1).replace('_', ' ')),
                                groups.computeIfAbsent(provider, ignored -> UUID.randomUUID().toString()), label));
                            if (batch.size() >= 12) publish(mc, token, batch);
                        });
                    }
                }
                publish(mc, token, batch);
                Metrics measured = new Metrics(minimum,counts[0],counts[1],counts[2],counts[3],counts[4],encoded[0],
                    (System.nanoTime()-began)/1_000_000.0);
                mc.execute(() -> { if (token == generation) { loading = false; metrics = measured; } });
            } catch (CancellationException ignored) {
            } catch (Throwable failure) {
                if (!(failure instanceof InterruptedException)) mc.execute(() -> {
                    if (token == generation) { loading = false; error = "曲库扫描失败，可刷新重试。"; }
                });
            }
        });
    }
    private static double minimumSeconds(Path config) {
        try {
            if (!Files.exists(config,LinkOption.NOFOLLOW_LINKS)) {
                Files.createDirectories(config.getParent());
                Files.writeString(config,"{\"minTrackSeconds\":10}\n",StandardOpenOption.CREATE_NEW);
            }
            if (!Files.isRegularFile(config,LinkOption.NOFOLLOW_LINKS) || Files.size(config)>2048) return 10;
            double value = JsonParser.parseString(Files.readString(config)).getAsJsonObject().get("minTrackSeconds").getAsDouble();
            if (Double.isFinite(value) && value>=0 && value<=120) return value;
        } catch (Exception ignored) {}
        return MusicCatalogFilter.DEFAULT_MINIMUM_SECONDS;
    }
    private static void publish(Minecraft mc, long token, List<Track> batch) {
        if (batch.isEmpty()) return;
        List<Track> ready = List.copyOf(batch); batch.clear();
        CompletableFuture<Void> acknowledged = new CompletableFuture<>();
        mc.execute(() -> {
            if (token == generation) catalog.addAll(ready);
            acknowledged.complete(null);
        });
        try {
            acknowledged.get(10, TimeUnit.SECONDS);
            // Bound queued callbacks and allow the UI to observe real partial results.
            TimeUnit.MILLISECONDS.sleep(120);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt(); throw new CancellationException();
        } catch (ExecutionException | TimeoutException failure) { throw new CancellationException(); }
    }
}
