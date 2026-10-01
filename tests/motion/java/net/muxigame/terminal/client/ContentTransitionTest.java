package net.muxigame.terminal.client;

public final class ContentTransitionTest {
    private static long now;
    private static int passed;
    private static void check(boolean value,String label){if(!value)throw new AssertionError(label);passed++;}
    private static void near(float value,float expected,String label){check(Math.abs(value-expected)<0.00001f,label);}
    public static void main(String[] args){
        var motion=new TerminalContentTransition(()->now);
        near(motion.frame().alpha(),1,"idle opaque");check(!motion.frame().animating(),"idle stable");
        motion.open(false);near(motion.frame().alpha(),0,"entry begins transparent");near(motion.frame().scale(),0.975f,"entry scale");
        now+=55_000_000L;near(motion.frame().alpha(),0.5f,"entry midpoint smoothstep");check(motion.frame().animating(),"midpoint animating");
        motion.close(false);near(motion.frame().alpha(),0.5f,"interrupted entry closes continuously");check(motion.frame().closing(),"closing marker");
        now+=75_000_000L;near(motion.frame().alpha(),0.25f,"close midpoint");
        now+=75_000_000L;near(motion.frame().alpha(),0,"close ends transparent");check(!motion.frame().animating(),"close completion enables disposal");
        motion.open(false);check(!motion.frame().closing(),"new entry supersedes old closing");
        now+=110_000_000L;near(motion.frame().alpha(),1,"entry completes");near(motion.frame().scale(),1,"final native geometry");
        motion.close(true);near(motion.frame().alpha(),0,"reduced closing immediate");check(!motion.frame().animating(),"reduced close no timer");
        motion.open(true);near(motion.frame().alpha(),1,"reduced opening immediate");check(!motion.frame().animating(),"reduced open no timer");
        motion.reset();check(!motion.frame().closing(),"reset releases old transition");near(motion.frame().alpha(),1,"reset opaque");
        System.out.println("Native content transition assertions: "+passed+" passed");
    }
}
