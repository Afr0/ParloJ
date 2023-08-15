package Parlo;
import Parlo.Packets.*;
import java.util.concurrent.*;

public interface ReceivedPacketDelegate 
{
	CompletableFuture<Void> onReceivedPacket(NetworkClient client, Packet packet);
}
