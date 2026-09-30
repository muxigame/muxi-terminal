package net.muxigame.terminal.net;

import com.mojang.serialization.DataResult;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.muxigame.terminal.MuxiTerminal;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Server-assisted native actions that cannot be opened safely from a client-only screen. */
public final class TerminalNetwork {
    private TerminalNetwork() {}

    public record OpenManual(String manual) implements CustomPacketPayload {
        public static final Type<OpenManual> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(MuxiTerminal.MOD_ID,"open_manual"));
        public static final StreamCodec<RegistryFriendlyByteBuf,OpenManual> CODEC=StreamCodec.of(
            (b,p)->b.writeUtf(p.manual,48), b->new OpenManual(b.readUtf(48)));
        @Override public Type<OpenManual> type(){return TYPE;}
    }

    public static void register(IEventBus modBus){modBus.addListener(TerminalNetwork::payloads);}

    private static void payloads(RegisterPayloadHandlersEvent event){
        event.registrar("1").optional().playToServer(OpenManual.TYPE,OpenManual.CODEC,(packet,context)->{
            if(context.player() instanceof ServerPlayer player) openManual(player,packet.manual());
        });
    }

    private static void openManual(ServerPlayer player,String manual){
        if("iceandfire".equals(manual)) openMenuItem(player,ResourceLocation.fromNamespaceAndPath("iceandfire","bestiary"));
    }

    private static void openMenuItem(ServerPlayer player,ResourceLocation itemId){
        Item item=BuiltInRegistries.ITEM.get(itemId);
        if(!(item instanceof MenuProvider provider)) return;
        ItemStack stack=ItemStack.EMPTY;
        for(int slot=0;slot<player.getInventory().getContainerSize();slot++){
            ItemStack candidate=player.getInventory().getItem(slot);
            if(candidate.is(item)){stack=candidate.copy();break;}
        }
        if(stack.isEmpty()){
            player.sendSystemMessage(Component.literal("需要先获得对应的游戏内手册。"));
            return;
        }
        ItemStack book=stack;
        player.openMenu(provider,buf->{
            CompoundTag wrapper=new CompoundTag();
            DataResult<net.minecraft.nbt.Tag> encoded=ItemStack.OPTIONAL_CODEC.encodeStart(NbtOps.INSTANCE,book);
            encoded.result().ifPresent(tag->wrapper.put("data",tag));
            buf.writeNbt(wrapper);
        });
    }
}
