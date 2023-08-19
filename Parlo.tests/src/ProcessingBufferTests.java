import org.junit.jupiter.api.Test;

import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Logger;

import Parlo.ProcessedPacketDelegate;
import Parlo.ProcessingBuffer;
import Parlo.Exceptions.BufferOverflowException;
import Parlo.Packets.Packet;

public class ProcessingBufferTests 
{

    @Test
    public void testAddingData() 
    {
        ProcessingBuffer processingBuffer = new ProcessingBuffer(new ProcessedPacketDelegate()
        {
        	public void onProcessedPacket(Packet packet)
        	{
        		Logger.getGlobal().info("testAddingData: In onProcessedPacket()!");
        	}
        });
        
        byte[] data = new byte[] { 1, 2, 3, 4 };
        
        try 
        {
        	processingBuffer.addData(data);
            assertEquals(4, processingBuffer.getInternalBuffer().size());
            byte peekedValue = processingBuffer.getInternalBuffer().poll(1, TimeUnit.SECONDS);

            assertEquals(data[0], peekedValue);
            processingBuffer.getInternalBuffer().add(peekedValue);
        } 
        catch (Exception e)
        {
            Logger.getGlobal().info(e.toString());
            processingBuffer.close();
        }
    }

    @Test
    public void testAddingTooMuchData() 
    {
        ProcessingBuffer processingBuffer = new ProcessingBuffer(new ProcessedPacketDelegate()
        {
        	public void onProcessedPacket(Packet packet)
        	{
        		Logger.getGlobal().info("testAddingTooMuchData: "
        				+ "In onProcessedPacket()!");
        	}
        });
        
        byte[] data = new byte[ProcessingBuffer.MAX_PACKET_SIZE + 1];

        assertThrows(BufferOverflowException.class, () -> processingBuffer.addData(data));
    }

    @Test
    public void testProcessingPacket() 
    {
        boolean[] eventFired = new boolean[] { false };
        CountDownLatch latch = new CountDownLatch(1);
        
        ProcessingBuffer processingBuffer = new ProcessingBuffer(new ProcessedPacketDelegate()
        {
        	public void onProcessedPacket(Packet packet)
        	{
        		Logger.getGlobal().info("testProcessingPacket: in onProcessedPacket()!");
        		eventFired[0] = true;
        		latch.countDown();
        	}
        });

        byte[] data = new byte[] { 1, 0, 4, 5, 6, 7 };
        
        Packet packet = new Packet((byte)1, data, false);

        try 
        {
            processingBuffer.addData(/*data*/packet.buildPacket());
            assertFalse(eventFired[0]);
            
            processingBuffer.addData(new byte[] { 8, 9, 10 });
            boolean wasCalled = latch.await(5, TimeUnit.SECONDS);
            assertTrue("Callback was not invoked within the timeout", wasCalled);
            assertTrue(eventFired[0]);
        } 
        catch (Exception e)
        {
            Logger.getGlobal().info(e.toString());
            processingBuffer.close();
        }
    }
}
