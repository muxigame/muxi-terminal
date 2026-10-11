package net.muxigame.terminal.client.music;

import java.lang.reflect.Field;
import java.util.*;

/** Actual service state predicate, no Minecraft instance or OpenAL initialized. */
public final class MusicArbitrationStateTest {
    private static int checks;
    private static void check(boolean result,String label){if(!result)throw new AssertionError(label);checks++;}
    private static void set(String name,Object value)throws Exception{Field field=TerminalMusicService.class.getDeclaredField(name);field.setAccessible(true);field.set(null,value);}
    public static void main(String[] args)throws Exception{
        check(!TerminalMusicService.hasOwnedPlayback(),"empty APP suppressed vanilla music");
        set("queue",List.of());check(!TerminalMusicService.hasOwnedPlayback(),"empty queue suppressed vanilla music");
        set("busy",true);check(!TerminalMusicService.hasOwnedPlayback(),"local import busy suppressed vanilla music");
        set("busy",false);
        var portable=new NetMusicListAdapter.Portable(1,UUID.randomUUID(),null,List.of(),-1);
        set("ownedPortable",portable);check(TerminalMusicService.hasOwnedPlayback(),"pending requested portable wasn't exclusive");
        TerminalMusicService.closeLocal();check(TerminalMusicService.hasOwnedPlayback(),"closing UI released intentional portable session");
        set("ownedPortable",null);check(!TerminalMusicService.hasOwnedPlayback(),"released failed/stopped portable suppressed vanilla music");
        System.out.println("{\"actualServicePredicateChecks\":"+checks+",\"nativeStopFailureLifecycleTested\":false,\"minecraftInstanceStarted\":false}");
    }
}
