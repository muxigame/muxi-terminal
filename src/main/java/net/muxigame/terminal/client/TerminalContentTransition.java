package net.muxigame.terminal.client;

import java.util.function.LongSupplier;

/** Native texture timing. Security revocation happens separately, before the closing pixels fade. */
public final class TerminalContentTransition {
    public record Frame(float alpha,float scale,boolean closing,boolean animating) {}
    private final LongSupplier clock;
    private long started,duration;
    private float from,to;
    private boolean closing;
    public TerminalContentTransition(){this(System::nanoTime);}
    public TerminalContentTransition(LongSupplier clock){this.clock=clock;reset();}
    public void reset(){started=clock.getAsLong();duration=0;from=to=1;closing=false;}
    public void open(boolean reduced){started=clock.getAsLong();duration=reduced?0:110_000_000L;from=0;to=1;closing=false;}
    public void close(boolean reduced){from=frame().alpha();to=0;started=clock.getAsLong();duration=reduced?0:150_000_000L;closing=true;}
    public Frame frame(){
        float progress=duration==0?1:Math.max(0,Math.min(1,(clock.getAsLong()-started)/(float)duration));
        float eased=progress*progress*(3-2*progress);
        float alpha=from+(to-from)*eased;
        return new Frame(alpha,0.975f+0.025f*alpha,closing,progress<1);
    }
}
