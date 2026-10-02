package net.muxigame.terminal.client.music;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;
import java.lang.reflect.*;

/** Pinned to inspected net_music_list 4.3 APIs. Does not create or modify music items. */
final class NetMusicListAdapter {
    private static final String PREFIX = "com.gly091020.netMusicListNeoforge.";
    record Portable(int slot, UUID id, ItemStack playlist, List<?> songs, int index) {}
    static List<Portable> portable(Minecraft mc) throws ReflectiveOperationException {
        if (mc.player == null) return List.of();
        Class<?> item = Class.forName(PREFIX + "item.NetMusicPlayerItem");
        Class<?> list = Class.forName(PREFIX + "item.NetMusicListItem");
        List<Portable> out = new ArrayList<>();
        for (int slot = 0; slot < mc.player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = mc.player.getInventory().getItem(slot);
            if (!item.isInstance(stack.getItem())) continue;
            UUID id = (UUID)item.getMethod("getRingerId", ItemStack.class).invoke(null, stack);
            Object container = item.getMethod("getContainer", ItemStack.class).invoke(null, stack);
            ItemStack playlist = (ItemStack)container.getClass().getMethod("getItem", int.class).invoke(container, 0);
            if (!list.isInstance(playlist.getItem())) {
                Class<?> cd = Class.forName("com.github.tartaricacid.netmusic.item.ItemMusicCD");
                if (cd.isInstance(playlist.getItem())) {
                    Object info = cd.getMethod("getSongInfo", ItemStack.class).invoke(null, playlist);
                    if (info != null) out.add(new Portable(slot, id, playlist, List.of(info), 0));
                }
                continue;
            }
            List<?> songs = (List<?>)list.getMethod("getSongInfoList", ItemStack.class).invoke(null, playlist);
            Integer index = (Integer)list.getMethod("getSongIndex", ItemStack.class).invoke(null, playlist);
            out.add(new Portable(slot, id, playlist, songs, index == null ? -1 : index));
        }
        return out;
    }
    static boolean ringer(SoundInstance sound) { return sound.getClass().getName().equals(PREFIX + "sounds.RingerSound"); }
    static boolean self(SoundInstance sound) throws ReflectiveOperationException { return (boolean)sound.getClass().getMethod("isSelf").invoke(sound); }
    static UUID ringerId(SoundInstance sound) throws ReflectiveOperationException { return (UUID)sound.getClass().getMethod("getRingerId").invoke(sound); }
    static String title(SoundInstance sound) throws ReflectiveOperationException {
        Object info = sound.getClass().getMethod("getInfo").invoke(sound);
        return MusicSecurity.title(info == null ? null : info.getClass().getField("songName").get(info));
    }
    static void command(Portable player, String action, int index) throws ReflectiveOperationException {
        Class<?> packet = Class.forName(PREFIX + "packet.MusicPlayerActionPacket");
        Class<?> actionType = Class.forName(PREFIX + "packet.MusicPlayerActionPacket$Action");
        Class<?> mode = Class.forName(PREFIX + "util.PlayMode");
        Class<?> info = Class.forName("com.github.tartaricacid.netmusic.item.ItemMusicCD$SongInfo");
        Object act = actionType.getMethod("valueOf", String.class).invoke(null, action);
        Object loop = mode.getMethod("valueOf", String.class).invoke(null, "LOOP");
        // No song URL or local metadata is ever sent. Existing item data is authoritative.
        Object payload = packet.getConstructor(actionType, int.class, int.class, mode, info)
            .newInstance(act, player.slot(), index, loop, null);
        PacketDistributor.sendToServer((CustomPacketPayload)payload);
    }
}
