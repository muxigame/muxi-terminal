package net.muxigame.terminal.client.music;
import net.minecraft.client.sounds.AudioStream;
import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.nio.ByteBuffer;

/** MC and module lifecycle share one close. Reads and close cannot race. */
final class OwnedAudioStream implements AudioStream {
    private final AudioStream delegate;
    private final Runnable failure;
    private boolean closed;
    OwnedAudioStream(AudioStream delegate, Runnable failure) { this.delegate = delegate; this.failure = failure; }
    public AudioFormat getFormat() { return delegate.getFormat(); }
    public synchronized ByteBuffer read(int bytes) throws IOException {
        if (closed) return ByteBuffer.allocate(0);
        try { return delegate.read(bytes); }
        catch (IOException | RuntimeException error) { failure.run(); throw new IOException("本地曲目解码失败"); }
    }
    public synchronized void close() throws IOException { if (!closed) { closed = true; delegate.close(); } }
}
