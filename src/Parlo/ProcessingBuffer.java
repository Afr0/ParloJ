/*This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
If a copy of the MPL was not distributed with this file, You can obtain one at
http://mozilla.org/MPL/2.0/.

The Original Code is the Parlo library.

The Initial Developer of the Original Code is
Mats 'Afr0' Vederhus. All Rights Reserved.

Contributor(s): ______________________________________.
*/


package Parlo;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import Parlo.Packets.*;
import Parlo.Exceptions.*;

public class ProcessingBuffer implements AutoCloseable 
{
    public static int MAX_PACKET_SIZE = 1024;
    private BlockingQueue<Byte> internalBuffer = new ArrayBlockingQueue<>(MAX_PACKET_SIZE);
    private ExecutorService executor = Executors.newSingleThreadExecutor();

    private boolean m_HasReadHeader = false;
    private byte m_CurrentID;       //ID of current packet.
    private byte m_IsCompressed;    //Whether or not the current packet contains compressed data.
    private short m_CurrentLength; //Length of current packet.
    
    private ProcessedPacketDelegate onProcessedPacketDelegate;
    
    /**
     * Gets, but does NOT set the internal buffer.
     * Used by tests.
     * @return The internal buffer.
     */
    public BlockingQueue<Byte> getInternalBuffer()
    {
    	return internalBuffer;
    }
    
    public ProcessingBuffer(ProcessedPacketDelegate delegate) 
    {
        this.onProcessedPacketDelegate = delegate;
        CompletableFuture future = CompletableFuture.runAsync(() ->
        {
            try 
            {
                while (!Thread.currentThread().isInterrupted()) 
                {
                    if (internalBuffer.size() >= (int)PacketHeaders.STANDARD) 
                    {
                        if (!m_HasReadHeader) 
                        {
                            m_CurrentID = internalBuffer.take();

                            m_IsCompressed = internalBuffer.take();

                            byte[] LengthBuf = new byte[2];

                            for (int i = 0; i < LengthBuf.length; i++)
                                LengthBuf[i] = internalBuffer.take();

                            m_CurrentLength = ByteBuffer.wrap(LengthBuf).order(ByteOrder.LITTLE_ENDIAN).getShort();

                            m_HasReadHeader = true;
                        }
                    }

                    if (m_HasReadHeader) 
                    {
                        if (internalBuffer.size() >= (m_CurrentLength - PacketHeaders.STANDARD)) 
                        {
                            byte[] PacketData = new byte[m_CurrentLength - PacketHeaders.STANDARD];

                            for (int i = 0; i < PacketData.length; i++)
                                PacketData[i] = internalBuffer.take();

                            m_HasReadHeader = false;

                            Packet P;
                            P = new Packet(m_CurrentID, PacketData, m_IsCompressed == 1);

                            onProcessedPacketDelegate.onProcessedPacket(P);
                        }
                    }
                    
                    //Don't hog the processor!
                    Thread.sleep(10);
                }
            } 
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
            }
        });
        
        //Make sure the async task terminates when the program terminates
        //or after a user interrupt.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> 
        {
            future.cancel(true);
            executor.shutdown();
            try 
            {
                if (!executor.awaitTermination(800, TimeUnit.MILLISECONDS)) 
                    executor.shutdownNow();
            } 
            catch (InterruptedException e) 
            {
                executor.shutdownNow();
            }
        }));
    }

    public void addData(byte[] Data) throws BufferOverflowException 
    {
        if (Data.length > MAX_PACKET_SIZE) 
        {
            Logger.log("Tried adding too much data to ProcessingBuffer!", LogLevel.error);
            throw new BufferOverflowException("Buffer overflow occured when receiving data!");
        }

        for (int i = 0; i < Data.length; i++)
            internalBuffer.add(Data[i]);
    }

    @Override
    public void close() 
    {
        executor.shutdown();
    }
}
