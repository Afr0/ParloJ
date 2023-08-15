package Parlo;

import Parlo.Packets.Packet;

public interface ProcessedPacketDelegate 
{
    void onProcessedPacket(Packet packet) throws InterruptedException;
}
