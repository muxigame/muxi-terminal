package net.muxigame.terminal.client.music;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import java.util.*;

/** Actual loaded music files, including resource-pack overrides. No catalog or downloads. */
final class GameMusicLibrary {
    record Track(String id, ResourceLocation audio, String title) {}
    private static Object generation;
    private static List<Track> tracks = List.of();
    static List<Track> tracks() {
        var mc = Minecraft.getInstance();
        Object current = mc.getSoundManager().getSoundEvent(SoundEvents.MUSIC_MENU.value().getLocation());
        if (current == generation) return tracks;
        generation = current;
        tracks = Sound.SOUND_LISTER.listMatchingResources(mc.getResourceManager()).keySet().stream()
            .map(Sound.SOUND_LISTER::fileToId)
            .filter(id -> id.getPath().startsWith("music/") || id.getPath().contains("/music/"))
            .sorted(Comparator.comparing(ResourceLocation::toString))
            .map(id -> new Track(UUID.randomUUID().toString(), id,
                MusicSecurity.title(id.getPath().substring(id.getPath().lastIndexOf('/') + 1).replace('_', ' '))))
            .toList();
        return tracks;
    }
    static Track find(String id) { return tracks().stream().filter(t -> t.id().equals(id)).findFirst().orElse(null); }
}
