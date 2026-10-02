package net.muxigame.terminal.client.music;

import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.JOrbisAudioStream;
import javax.sound.sampled.*;
import java.nio.*;
import java.nio.file.*;
import java.io.*;
import java.util.Locale;

/** File-only decoding; reuses Minecraft Vorbis and the installed NetMusic decoder. */
public final class LocalDecoder {
    private LocalDecoder() {}
    public static boolean allowed(String suffix) {
        return switch (suffix.toLowerCase(Locale.ROOT)) {
            case "ogg", "wav", "mp3", "flac", "aac" -> true;
            default -> false;
        };
    }
    public static AudioStream open(Path file) throws Exception {
        String suffix = file.getFileName().toString().replaceFirst(".*\\.", "").toLowerCase(Locale.ROOT);
        if (!allowed(suffix)) throw new IOException("Unsupported format");
        if (suffix.equals("ogg")) {
            InputStream in = Files.newInputStream(file);
            try { return new JOrbisAudioStream(in); }
            catch (Exception error) { in.close(); throw error; }
        }
        if (suffix.equals("wav")) return new PcmWav(file);
        // Direct constructor with a generated local file URL. No resolver, playlist or packets.
        return (AudioStream) Class.forName("com.github.tartaricacid.netmusic.client.audio.NetMusicAudioStream")
            .getConstructor(java.net.URL.class).newInstance(file.toUri().toURL());
    }
    public static void probe(Path file) throws Exception {
        try (AudioStream stream = open(file)) {
            AudioFormat format = stream.getFormat();
            if (format.getChannels() < 1 || format.getChannels() > 2 || format.getSampleRate() <= 0)
                throw new IOException("Unsupported channel layout");
            if (!stream.read(4096).hasRemaining()) throw new IOException("Empty audio");
        }
    }
    private static final class PcmWav implements AudioStream {
        private final AudioInputStream stream;
        private final AudioFormat format;
        PcmWav(Path file) throws Exception {
            AudioInputStream source = AudioSystem.getAudioInputStream(file.toFile());
            try {
                AudioFormat f = source.getFormat();
                if (!(f.getEncoding().equals(AudioFormat.Encoding.PCM_SIGNED)
                    || f.getEncoding().equals(AudioFormat.Encoding.PCM_UNSIGNED)) || f.getChannels() > 2)
                    throw new UnsupportedAudioFileException("Only PCM WAV");
                format = new AudioFormat(f.getSampleRate(), 16, f.getChannels(), true, false);
                stream = AudioSystem.getAudioInputStream(format, source);
            } catch (Exception error) { source.close(); throw error; }
        }
        public AudioFormat getFormat() { return format; }
        public ByteBuffer read(int bytes) throws IOException {
            int size = Math.max(format.getFrameSize(), bytes - bytes % format.getFrameSize());
            byte[] data = stream.readNBytes(size);
            return ByteBuffer.allocateDirect(data.length).order(ByteOrder.LITTLE_ENDIAN).put(data).flip();
        }
        public void close() throws IOException { stream.close(); }
    }
}
