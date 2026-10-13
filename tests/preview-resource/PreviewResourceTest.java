import net.muxigame.terminal.client.PreviewResource;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public final class PreviewResourceTest {
    static int checks;
    static void rejects(Runnable operation){try{operation.run();throw new AssertionError("Expected rejection");}catch(IllegalArgumentException expected){checks++;}}
    static String uri(byte[] data){try{return PreviewResource.dataUri(new ByteArrayInputStream(data));}catch(java.io.IOException error){throw new RuntimeException(error);}}
    public static void main(String[] args)throws Exception{
        byte[] approved=Files.readAllBytes(Path.of(args[0]));
        PreviewResource.requireId("muxi_horse_racing","textures/gui/umamusume_preview/approved/suzuka_v8_123456789abc.gif");checks++;
        byte[] returned=java.util.Base64.getDecoder().decode(uri(approved).substring("data:image/gif;base64,".length()));
        if(!Arrays.equals(approved,returned))throw new AssertionError("Encoded approved GIF bytes changed");checks++;
        rejects(()->PreviewResource.requireId("minecraft","textures/gui/umamusume_preview/a.gif"));
        rejects(()->PreviewResource.requireId("muxi_horse_racing","textures/gui/umamusume_preview/../a.gif"));
        rejects(()->PreviewResource.requireId("muxi_horse_racing","textures/secret/a.gif"));
        rejects(()->PreviewResource.requireId("muxi_horse_racing","textures/gui/umamusume_preview/a.svg"));
        rejects(()->uri(new byte[1024*1024+1]));
        rejects(()->uri(new byte[13]));
        byte[] bad=approved.clone();bad[0]='X';rejects(()->uri(bad));
        byte[] wide=approved.clone();wide[6]=1;wide[7]=1;rejects(()->uri(wide));
        byte[] empty=approved.clone();empty[8]=0;empty[9]=0;rejects(()->uri(empty));
        System.out.println("PreviewResource: "+checks+" checks passed; approved native GIF transported byte-for-byte");
    }
}
