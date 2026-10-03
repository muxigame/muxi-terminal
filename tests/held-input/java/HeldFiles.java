package net.muxigame.terminal.heldqa;
import com.google.gson.*;
import java.nio.file.*;
public final class HeldFiles {
 public static final Path ROOT=Path.of(System.getProperty("qa.local.root","missing-held-qa-root"));
 public static final String RUN=System.getProperty("qa.local.runId","");
 public static boolean enabled(){try{var m=JsonParser.parseString(Files.readString(ROOT.resolve("local-mc-owner.json"))).getAsJsonObject();return !RUN.isBlank()&&RUN.equals(m.get("runId").getAsString())&&ROOT.toAbsolutePath().normalize().toString().equals(m.get("instanceRoot").getAsString());}catch(Exception absent){return false;}}
 public static JsonObject read(String name){try{var m=JsonParser.parseString(Files.readString(ROOT.resolve("coordinator").resolve(name))).getAsJsonObject();return RUN.equals(m.get("runId").getAsString())?m:null;}catch(Exception busy){return null;}}
 public static void write(String name,JsonObject value)throws Exception{value.addProperty("runId",RUN);value.addProperty("physicalOSInput",false);Path p=ROOT.resolve("coordinator").resolve(name),tmp=p.resolveSibling(name+".tmp");Files.writeString(tmp,new GsonBuilder().setPrettyPrinting().create().toJson(value));Files.move(tmp,p,StandardCopyOption.REPLACE_EXISTING);}
}
