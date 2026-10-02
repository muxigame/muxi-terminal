package net.muxigame.terminal.client.music;

import com.google.gson.*;
import java.nio.file.*;
import java.io.*;
import java.util.*;

/** Personal copies with opaque IDs. Original paths are never persisted or serialized. */
public final class LocalMusicLibrary {
    public record Track(String id, String title, String extension) {}
    public interface Probe { void check(Path file) throws Exception; }
    private final Path root;
    private final List<Track> tracks = new ArrayList<>();
    public LocalMusicLibrary(Path personalRoot) throws IOException {
        root = personalRoot.toAbsolutePath().normalize();
        localPath(root);
        Files.createDirectories(root);
        if (Files.isSymbolicLink(root)) throw new IOException("Unsafe library");
        Path manifest = root.resolve("library.json");
        if (Files.exists(manifest, LinkOption.NOFOLLOW_LINKS)) {
            if (!Files.isRegularFile(manifest, LinkOption.NOFOLLOW_LINKS) || Files.size(manifest) > 256 * 1024)
                throw new IOException("Invalid library");
            try (Reader reader = Files.newBufferedReader(manifest)) {
                for (JsonElement element : JsonParser.parseReader(reader).getAsJsonArray()) {
                    JsonObject row = element.getAsJsonObject();
                    String id = row.get("id").getAsString(), ext = row.get("extension").getAsString();
                    if (!MusicSecurity.id(id) || !LocalDecoder.allowed(ext) || tracks.size() >= 300)
                        throw new IOException("Invalid library entry");
                    tracks.add(new Track(id, MusicSecurity.title(row.get("title").getAsString()), ext));
                }
            } catch (RuntimeException error) { throw new IOException("Invalid library", error); }
        }
    }
    public synchronized List<Track> tracks() { return List.copyOf(tracks); }
    public synchronized Path file(String id) throws IOException {
        Track track = tracks.stream().filter(t -> t.id().equals(id)).findFirst().orElseThrow(() -> new IOException("Unknown track"));
        Path file = root.resolve(track.id() + "." + track.extension());
        localPath(file);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Missing track");
        return file;
    }
    public synchronized Track add(Path selected, Probe probe) throws Exception {
        if (tracks.size() >= 300) throw new IOException("Library full");
        Path original = selected.toAbsolutePath().normalize();
        localPath(original);
        if (original.toString().startsWith("\\\\") || !Files.isRegularFile(original, LinkOption.NOFOLLOW_LINKS)
            || Files.size(original) > 256L * 1024 * 1024) throw new IOException("Not a local regular audio file");
        String name = original.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String ext = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!LocalDecoder.allowed(ext)) throw new IOException("Unsupported extension");
        String id = UUID.randomUUID().toString();
        Path copy = root.resolve(id + "." + ext);
        try {
            Files.copy(original, copy);
            probe.check(copy);
            Track track = new Track(id, MusicSecurity.title(dot < 0 ? name : name.substring(0, dot)), ext);
            tracks.add(track);
            try { save(); } catch (IOException error) { tracks.remove(track); throw error; }
            return track;
        } catch (Exception | LinkageError error) { Files.deleteIfExists(copy); throw error; }
    }
    public synchronized void remove(String id) throws IOException {
        Track track = tracks.stream().filter(t -> t.id().equals(id)).findFirst().orElseThrow(() -> new IOException("Unknown track"));
        tracks.remove(track);
        try { save(); } catch (IOException error) { tracks.add(track); throw error; }
        Files.deleteIfExists(root.resolve(track.id() + "." + track.extension()));
    }
    private void save() throws IOException {
        Path staged = root.resolve("library.pending.json");
        if (Files.isSymbolicLink(staged)) throw new IOException("Unsafe manifest");
        JsonArray json = new JsonArray();
        for (Track track : tracks) {
            JsonObject row = new JsonObject();
            row.addProperty("id", track.id()); row.addProperty("title", track.title()); row.addProperty("extension", track.extension());
            json.add(row);
        }
        Files.writeString(staged, json.toString(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        try { Files.move(staged, root.resolve("library.json"), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException error) { Files.move(staged, root.resolve("library.json"), StandardCopyOption.REPLACE_EXISTING); }
    }
    private static void localPath(Path file) throws IOException {
        if (file.toString().startsWith("\\\\")) throw new IOException("Network paths are unavailable");
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows")) {
            int type = com.sun.jna.platform.win32.Kernel32.INSTANCE.GetDriveType(file.getRoot().toString());
            if (type != 2 && type != 3 && type != 5 && type != 6) throw new IOException("Not a local drive");
        }
    }
}
