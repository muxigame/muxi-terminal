package net.muxigame.terminal;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.muxigame.terminal.item.PlayerTerminalItem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

@Mod(MuxiTerminal.MOD_ID)
public final class MuxiTerminal {
    public static final String MOD_ID = "muxi_terminal";
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(BuiltInRegistries.ITEM, MOD_ID);
    public static final Supplier<Item> PLAYER_TERMINAL = ITEMS.register(
        "player_terminal", () -> new PlayerTerminalItem(new Item.Properties().stacksTo(1))
    );

    private static final String RECEIVED_KEY = "muxi_terminal_received";

    public MuxiTerminal(IEventBus modBus) {
        ITEMS.register(modBus);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLogin);
        if (FMLEnvironment.dist.isClient()) {
            net.muxigame.terminal.client.TerminalClient.bootstrap(modBus);
        }
    }

    private void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var data = player.getPersistentData();
        if (data.getBoolean(RECEIVED_KEY)) return;

        ItemStack stack = new ItemStack(PLAYER_TERMINAL.get());
        if (!player.getInventory().add(stack)) player.drop(stack, false);
        data.putBoolean(RECEIVED_KEY, true);
        player.sendSystemMessage(Component.translatable("muxi_terminal.received"));
    }
}

