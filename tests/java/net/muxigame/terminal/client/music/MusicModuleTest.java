package net.muxigame.terminal.client.music;
import net.minecraft.client.sounds.AudioStream;
import javax.sound.sampled.*;
import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Isolated JVM tests: real MC Vorbis / Java PCM decoders and local store; no game boot. */
public final class MusicModuleTest {
    private static final List<String> checks = new ArrayList<>();
    private static void check(boolean value, String label) { if (!value) throw new AssertionError(label); checks.add(label); }
    private static void fails(RunnableChecked body, String label) throws Exception {
        boolean failed = false; try { body.run(); } catch (Exception error) { failed = true; }
        check(failed, label);
    }
    private interface RunnableChecked { void run() throws Exception; }
    private static final class Stream implements AudioStream {
        final AtomicInteger closes = new AtomicInteger(); boolean fail;
        public AudioFormat getFormat() { return new AudioFormat(44100, 16, 2, true, false); }
        public ByteBuffer read(int bytes) throws IOException { if (fail) throw new IOException("file:///PRIVATE/SECRET"); return ByteBuffer.allocate(bytes); }
        public void close() { closes.incrementAndGet(); }
    }
    public static void main(String[] args) throws Exception {
        String url = MusicSecurity.SHELL + "#/music";
        check(MusicSecurity.trusted(true, true, url, url), "owned built-in music main frame allowed");
        for (String rejected : List.of("https://evil.invalid/", "mod://muxi_terminal/custom/index.html#/music",
            MusicSecurity.SHELL + "?external=1#/music", MusicSecurity.SHELL + ".evil#/music", MusicSecurity.SHELL + "#/settings"))
            check(!MusicSecurity.trusted(true, true, rejected, rejected), "foreign document rejected: " + rejected.replace("https://evil.invalid/", "external"));
        check(!MusicSecurity.trusted(false, true, url, url), "unowned browser rejected even with matching URL");
        check(!MusicSecurity.trusted(true, false, url, url), "iframe rejected");
        check(!MusicSecurity.trusted(true, true, url, "https://evil.invalid/"), "redirected top-level browser rejected");
        check(!MusicSecurity.id("../../secret") && !MusicSecurity.id("C:\\secret.wav"), "opaque IDs exclude path input");
        check(MusicSecurity.title("C:\\PRIVATE\\song.wav").equals("song.wav"), "full Windows path stripped from display title");
        check(MusicSecurity.title("file:///PRIVATE/song.wav").equals("未命名曲目"), "file URL suppressed");
        check(MusicSecurity.title("/PRIVATE/song.wav").equals("song.wav"), "absolute POSIX path stripped");
        Path lab = Path.of(args[0]); Files.createDirectories(lab);
        Path original = lab.resolve("私人本机曲目.wav");
        try (AudioInputStream in = new AudioInputStream(new ByteArrayInputStream(new byte[8192]), new AudioFormat(44100,16,2,true,false),2048)) {
            AudioSystem.write(in, AudioFileFormat.Type.WAVE, original.toFile());
        }
        LocalDecoder.probe(original);
        check(true, "real PCM WAV decode probe succeeds");
        LocalDecoder.probe(Path.of(args[1])); check(true, "real MC JOrbis Vorbis decode probe succeeds");
        Path invalid = lab.resolve("invalid.wav"); Files.writeString(invalid, "not audio");
        fails(() -> LocalDecoder.probe(invalid), "malformed WAV rejected");
        Path opus = lab.resolve("invalid.ogg"); Files.writeString(opus, "Ogg is a container, not a codec guarantee");
        fails(() -> LocalDecoder.probe(opus), "non-Vorbis OGG rejected");
        LocalMusicLibrary library = new LocalMusicLibrary(lab.resolve("personal"));
        fails(() -> library.add(Path.of("\\\\invalid-host\\share\\private.wav"), LocalDecoder::probe), "UNC input rejected before file access");
        var track = library.add(original, LocalDecoder::probe);
        check(MusicSecurity.id(track.id()) && Files.exists(library.file(track.id())), "native import creates opaque local copy");
        check(!library.file(track.id()).equals(original), "import does not replace original");
        String manifest = Files.readString(lab.resolve("personal/library.json"));
        check(!manifest.contains(lab.toString()) && !manifest.contains("私人本机曲目.wav") && !manifest.contains("songUrl"), "manifest stores title and opaque ID; no source path or network payload");
        check(new LocalMusicLibrary(lab.resolve("personal")).tracks().getFirst().equals(track), "personal library persists after module reload");
        int before = library.tracks().size(); fails(() -> library.add(invalid, LocalDecoder::probe), "failed decoder import rejected");
        check(library.tracks().size() == before && Files.list(lab.resolve("personal")).filter(p -> p.getFileName().toString().endsWith(".wav")).count() == 1,
            "failed import rolls back copied audio and manifest");
        fails(() -> library.file("../../secret"), "unknown ID cannot read arbitrary file");
        library.remove(track.id()); check(Files.exists(original) && library.tracks().isEmpty(), "removal deletes personal copy and preserves chosen original");
        Files.writeString(lab.resolve("personal/library.json"), "[{\"id\":\"../../secret\",\"extension\":\"wav\",\"title\":\"X\"}]");
        fails(() -> new LocalMusicLibrary(lab.resolve("personal")), "tampered persistent ID rejected without fallback overwrite");
        Stream delegate = new Stream(); AtomicInteger failures = new AtomicInteger(); OwnedAudioStream owned = new OwnedAudioStream(delegate, failures::incrementAndGet);
        owned.close(); owned.close(); check(delegate.closes.get() == 1 && !owned.read(32).hasRemaining(), "stop, reload and MC cleanup share exactly one close");
        Stream failing = new Stream(); failing.fail = true; OwnedAudioStream stream = new OwnedAudioStream(failing, failures::incrementAndGet);
        try { stream.read(10); throw new AssertionError(); } catch (IOException error) { check(!error.getMessage().contains("PRIVATE") && failures.get() == 1, "playback decoder error surfaces without private path"); }
        stream.close();
        Stream race = new Stream(); OwnedAudioStream concurrent = new OwnedAudioStream(race, () -> {});
        List<Thread> threads = new ArrayList<>();
        for (int i=0;i<32;i++) { Thread t = new Thread(() -> { try { concurrent.read(16); concurrent.close(); } catch(IOException e) { throw new RuntimeException(e); } }); threads.add(t); t.start(); }
        for (Thread t: threads) t.join(); check(race.closes.get() == 1, "rapid concurrent disposal is idempotent");
        System.out.println(new com.google.gson.Gson().toJson(Map.of("success", true, "count", checks.size(), "checks", checks,
            "scope", "Isolated JVM; no Minecraft/MCEF/OpenAL gameplay session")));
    }
}
