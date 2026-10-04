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
    private static final List<Track> catalog = new ArrayList<>();
    static List<Track> tracks() { ensure(); return List.copyOf(catalog); }
    static Track find(String id) { return tracks().stream().filter(t -> t.id().equals(id)).findFirst().orElse(null); }
    static boolean loading() { ensure(); return loading; }
    static String error() { return error; }
    static int scans() { return scans; }
    static long resourceGeneration() { return resourceGeneration; }
    static void reloaded() { resourceGeneration++; invalidate(); }
    static long generation() { return generation; }
    static void invalidate() {
        generation++; if (worker != null) worker.cancel(true);
        worker = null; started = false; loading = false; error = ""; catalog.clear();
    }
    private static void ensure() {
        if (started) return;
        started = true; loading = true; scans++;
        Minecraft mc = Minecraft.getInstance(); ResourceManager resources = mc.getResourceManager();
        long token = generation;
        Map<String, String> providers = new HashMap<>();
        for (var mod : ModList.get().getMods()) {
            String key = "modmenu.nameTranslation." + mod.getModId();
            providers.put(mod.getModId(), MusicSecurity.title(I18n.exists(key) ? I18n.get(key) : mod.getDisplayName()));
        }
        // Minecraft's pack metadata supplies its official name, just like mod metadata.
        providers.putIfAbsent("minecraft", "Minecraft");
        worker = SCAN.submit(() -> {
            List<Track> batch = new ArrayList<>(); Set<ResourceLocation> seen = new HashSet<>();
            Map<String, String> groups = new HashMap<>();
            try {
                for (var pack : resources.listPacks().toList()) {
                    for (String namespace : new TreeSet<>(pack.getNamespaces(PackType.CLIENT_RESOURCES))) {
                        pack.listResources(PackType.CLIENT_RESOURCES, namespace, "sounds", (file, supplier) -> {
                            if (Thread.currentThread().isInterrupted() || token != generation) throw new CancellationException();
                            String path = file.getPath();
                            if (!path.endsWith(".ogg") || !(path.startsWith("sounds/music/") || path.contains("/music/")) || !seen.add(file)) return;
                            var resource = resources.getResource(file);
                            if (resource.isEmpty()) return;
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
                mc.execute(() -> { if (token == generation) loading = false; });
            } catch (CancellationException ignored) {
            } catch (Throwable failure) {
                if (!(failure instanceof InterruptedException)) mc.execute(() -> {
                    if (token == generation) { loading = false; error = "曲库扫描失败，可刷新重试。"; }
                });
            }
        });
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
