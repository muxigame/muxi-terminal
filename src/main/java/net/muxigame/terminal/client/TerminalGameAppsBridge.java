package net.muxigame.terminal.client;

import com.google.gson.*;
import org.cef.callback.CefQueryCallback;
import java.util.Set;

/** Called only after the persistent container's owned-main-frame authorization. */
final class TerminalGameAppsBridge {
    private static String context="{\"game\":\"\",\"page\":\"lobby\",\"drafts\":{}}";
    private static Object connection;
    private TerminalGameAppsBridge(){}
    private static void checkConnection(){
        Object current=net.minecraft.client.Minecraft.getInstance().getConnection();
        if(current!=connection){connection=current;context="{\"game\":\"\",\"page\":\"lobby\",\"drafts\":{}}";}
    }
    static void select(String game,String page){
        checkConnection();var data=JsonParser.parseString(context).getAsJsonObject();
        data.addProperty("game",game);data.addProperty("page",page);context=data.toString();
    }
    static boolean dispatch(String request,CefQueryCallback callback){
        if(!request.startsWith("games."))return false;
        checkConnection();
        try{
            if(request.equals("games.snapshot")){Object snapshot=invoke("snapshotJson",new Class<?>[0]);callback.success(snapshot==null?"{\"supported\":false,\"games\":[]}":snapshot.toString());}
            else if(request.equals("games.request")){invoke("request",new Class<?>[0]);callback.success("{\"ok\":true}");}
            else if(request.equals("games.context"))callback.success(context);
            else if(request.startsWith("games.context:")){
                var data=JsonParser.parseString(request.substring(14)).getAsJsonObject();
                if(request.length()>3072||!data.keySet().equals(Set.of("game","page","drafts"))||!data.get("game").getAsString().matches("[a-z0-9_-]{0,32}")||!Set.of("lobby","shop").contains(data.get("page").getAsString()))throw new IllegalArgumentException("Invalid app context");
                var drafts=data.getAsJsonObject("drafts");if(drafts.size()>24)throw new IllegalArgumentException("Too many form drafts");
                for(var entry:drafts.entrySet())if(!entry.getKey().matches("[a-zA-Z0-9_:-]{1,80}")||!entry.getValue().isJsonPrimitive()||entry.getValue().getAsString().length()>64)throw new IllegalArgumentException("Invalid form draft");
                context=data.toString();callback.success("{\"ok\":true}");
            }
            else if(request.startsWith("games.action:")){
                var data=JsonParser.parseString(request.substring(13)).getAsJsonObject();
                if(!data.keySet().equals(Set.of("game","action","value")))throw new IllegalArgumentException("Invalid action fields");
                for(var field:data.entrySet())if(!field.getValue().isJsonPrimitive()||!field.getValue().getAsJsonPrimitive().isString())throw new IllegalArgumentException("String action fields required");
                String game=data.get("game").getAsString(),action=data.get("action").getAsString(),value=data.get("value").getAsString();
                if(!game.matches("[a-z0-9_-]{1,32}")||!action.matches("[a-zA-Z][a-zA-Z0-9_-]{0,23}")||value.length()>128)throw new IllegalArgumentException("Invalid game action");
                invoke("action",new Class<?>[]{String.class,String.class,String.class},game,action,value);callback.success("{\"ok\":true}");
            }
            else throw new IllegalArgumentException("Unknown minigame command");
        }catch(ReflectiveOperationException|LinkageError unavailable){callback.failure(503,"小游戏服务当前不可用");}
        catch(RuntimeException invalid){callback.failure(400,invalid.getMessage()==null?"无效小游戏请求":invalid.getMessage());}
        return true;
    }
    private static Object invoke(String method,Class<?>[] types,Object... args)throws ReflectiveOperationException{
        return Class.forName("net.muxigame.minigames.client.TerminalGamesApi").getMethod(method,types).invoke(null,args);
    }
}
