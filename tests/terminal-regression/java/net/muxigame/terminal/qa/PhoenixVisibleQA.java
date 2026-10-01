package net.muxigame.terminal.qa;
import com.google.gson.*;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.entity.ShootResult;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import net.minecraft.client.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import java.nio.file.*;

/** Appended to the visible, isolated singleplayer suite. No production connection. */
final class PhoenixVisibleQA {
    private static final String[] IDS={"fn57","hk_mp5a5_bolt","mp155","nl545","nl545_fde","rhino19","rsh12","vssk","vulkan"};
    private int index,frame;private volatile boolean equipped;private volatile String failure="",shot="";private final JsonArray evidence=new JsonArray();
    boolean tick() throws Exception {
        var mc=Minecraft.getInstance();if(index==IDS.length)return true;
        if(!failure.isEmpty())throw new IllegalStateException(failure);
        var id=ResourceLocation.parse("ra1k:"+IDS[index]);
        if(frame==0){
            if(mc.getSingleplayerServer()==null||mc.player==null)throw new IllegalStateException("Phoenix QA requires private integrated server");
            if(TimelessAPI.getClientGunIndex(id).isEmpty())throw new IllegalStateException("Phoenix client index missing "+id);
            mc.setScreen(null);mc.options.setCameraType(CameraType.FIRST_PERSON);equipped=false;shot="";
            var uuid=mc.player.getUUID();mc.getSingleplayerServer().execute(()->{
                try{var p=mc.getSingleplayerServer().getPlayerList().getPlayer(uuid);var common=TimelessAPI.getCommonGunIndex(id).orElseThrow();
                    var stack=GunItemBuilder.create().setId(id).setAmmoCount(1).setAmmoInBarrel(true).setFireMode(common.getGunData().getFireModeSet().getFirst()).build(p.registryAccess());
                    if(stack.isEmpty())throw new IllegalStateException("Phoenix gun build empty "+id);p.getInventory().selected=0;p.getInventory().setItem(0,stack);p.inventoryMenu.broadcastChanges();
                    var operator=IGunOperator.fromLivingEntity(p);operator.initialData();operator.draw(p::getMainHandItem);equipped=true;
                }catch(Throwable e){failure=e.toString();}
            });frame++;return false;
        }
        if(!equipped)return false;
        var gun=IGun.getIGunOrNull(mc.player.getMainHandItem());
        if(gun==null||!id.equals(gun.getGunId(mc.player.getMainHandItem()))){if(frame>120)throw new IllegalStateException("Phoenix item sync failed "+id);frame++;return false;}
        if(frame==40){var uuid=mc.player.getUUID();mc.getSingleplayerServer().execute(()->{try{var p=mc.getSingleplayerServer().getPlayerList().getPlayer(uuid);shot=IGunOperator.fromLivingEntity(p).shoot(()->p.getXRot(),()->p.getYRot()).toString();}catch(Throwable e){failure=e.toString();}});}
        if(frame==45)capture(IDS[index]+"-firstperson-shot.png");
        if(frame==60){if(!shot.equals(ShootResult.SUCCESS.toString()))throw new IllegalStateException("Phoenix native shot failed "+id+" "+shot);var uuid=mc.player.getUUID();mc.getSingleplayerServer().execute(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayer(uuid);IGunOperator.fromLivingEntity(p).bolt();IGunOperator.fromLivingEntity(p).reload();});}
        if(frame==70)capture(IDS[index]+"-firstperson-reload.png");
        if(frame==150)mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
        if(frame==170)capture(IDS[index]+"-thirdperson.png");
        if(frame==190){
            var row=new JsonObject();row.addProperty("gun",id.toString());row.addProperty("nativeShoot",shot);row.addProperty("clientIndex",true);row.addProperty("firstAndThirdPersonCaptured",true);evidence.add(row);
            Files.writeString(Path.of("phoenix-visible-result.json"),new GsonBuilder().setPrettyPrinting().create().toJson(evidence));index++;frame=0;mc.options.setCameraType(CameraType.FIRST_PERSON);return index==IDS.length;
        }
        frame++;return false;
    }
    private void capture(String name)throws Exception{var mc=Minecraft.getInstance();try(var screenshot=Screenshot.takeScreenshot(mc.getMainRenderTarget())){screenshot.writeToFile(Path.of("phoenix-"+name));}}
}
