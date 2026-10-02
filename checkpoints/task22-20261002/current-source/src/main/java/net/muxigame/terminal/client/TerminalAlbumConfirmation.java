package net.muxigame.terminal.client;

import java.util.UUID;
import java.util.function.LongSupplier;
import java.lang.ref.WeakReference;

/** One-use, short-lived confirmation tied to the actual native browser and generation. */
public final class TerminalAlbumConfirmation {
    public record Ticket(String token,String id){}
    private final LongSupplier millis;
    private Ticket pending;private WeakReference<Object> owner;private long generation,deadline;
    public TerminalAlbumConfirmation(){this(()->System.nanoTime()/1_000_000L);}
    TerminalAlbumConfirmation(LongSupplier millis){this.millis=millis;}
    public synchronized Ticket prepare(String id,Object owner,long generation){
        if(!TerminalPhotoStore.validId(id) || owner==null)throw new IllegalArgumentException("Invalid confirmation");
        pending=new Ticket(UUID.randomUUID().toString(),id);this.owner=new WeakReference<>(owner);this.generation=generation;deadline=millis.getAsLong()+30_000;
        return pending;
    }
    public synchronized String consume(String token,Object owner,long generation){
        if(pending==null || !pending.token().equals(token) || this.owner.get()!=owner || this.generation!=generation || millis.getAsLong()>=deadline)
            throw new IllegalArgumentException("Confirmation expired; confirm again");
        String id=pending.id();pending=null;this.owner=null;return id;
    }
    public synchronized void cancel(Object owner,long generation){if(this.owner!=null && this.owner.get()==owner && this.generation==generation){pending=null;this.owner=null;}}
}
