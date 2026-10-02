package net.muxigame.terminal.qa.mixin;

import java.io.IOException;
import java.net.InetAddress;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.network.ServerConnectionListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Report the IOException vanilla publishServer otherwise silently consumes. */
@Mixin(IntegratedServer.class)
public abstract class LanPublishDiagnosticsMixin {
    @Redirect(method="publishServer", at=@At(value="INVOKE",target="Lnet/minecraft/server/network/ServerConnectionListener;startTcpServerListener(Ljava/net/InetAddress;I)V"))
    private void minigamesQa$reportBind(ServerConnectionListener listener, InetAddress address, int port) throws IOException {
        // Both genuine clients are on this machine; bind the private lab to loopback.
        InetAddress loopback=InetAddress.getByName("127.0.0.1");
        try { listener.startTcpServerListener(loopback,port); }
        catch(IOException failure) {
            System.err.println("PRIVATE_QA_LAN_BIND_FAILED address="+loopback+" port="+port);
            failure.printStackTrace();throw failure;
        }
    }
}
