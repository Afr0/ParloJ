import org.junit.jupiter.api.*;
import org.mockito.*;
import static org.mockito.Mockito.*;

import java.net.*;
import java.nio.*;
import java.nio.channels.CompletionHandler;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

import Parlo.Listener;
import Parlo.NetworkClient;
import Parlo.Packets.*;
import Parlo.LoginArgsContainer;
import Parlo.ClientDisconnectedDelegate;
import Parlo.IAsyncSocketChannel;
import Parlo.Logger;
import Parlo.LogLevel;
import Parlo.ClientDisconnectedFromListenerDelegate;
import Parlo.CancellationTokenSource;

class TestListener extends Listener 
{
    public TestListener(IAsyncSocketChannel socket)
    {
        super(socket);
    }

    @Override
    protected void acceptAsync() 
    {
        CompletableFuture future = CompletableFuture.runAsync(() -> 
        {
            while (true) 
            {
            	try
            	{
                	if(acceptCTS.isCancellationRequested())
                		break;
            		
	                IAsyncSocketChannel acceptedSocketInterface = listenerSock.accept().get();
	
	                if (acceptedSocketInterface != null) 
	                {
	                    Logger.log("\nNew client connected!\n", LogLevel.info);
	
	                    //Set max missed heartbeats to 0, for testing purposes.
	                    NetworkClient newClient = new NetworkClient(acceptedSocketInterface, 
	                    		this, 5, 0, null, this::newClientOnConnectionLostWrapper);
	                    newClient.setClientDisconnectedCallback(this::onClientDisconnected);
	                    newClient.setConnectionLostCallback(this::onClientConnectionLost);
	
	                    networkClients.add(newClient);
	                }
            	}
            	catch(Exception exception)
            	{
            		Logger.log("Exception in TestListener.acceptAsync: " + exception.getMessage(), null);
            	}
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

    protected void newClientOnConnectionLostWrapper(NetworkClient sender) 
    {
        onClientConnectionLost(sender);
    }
}

public class NetworkClientTests 
{

    private boolean mockSocketConnected = false;
    private boolean heartbeatReceived = false;
    private boolean missedHeartbeatDetected = false;

    @Test
    public void testMissedHeartbeat() 
    {
        //Arrange
        IAsyncSocketChannel mockSocket = Mockito.mock(IAsyncSocketChannel.class);
        TestListener server = new TestListener(mockSocket);
        CancellationTokenSource acceptCTS = new CancellationTokenSource();
        
        server.setClientDisconnectedCallback(new ClientDisconnectedFromListenerDelegate() 
        {
        	public void onClientDisconnected(NetworkClient client)
            {
        		missedHeartbeatDetected = true;
                Logger.log("Client disconnected!", LogLevel.info);
                //TODO: Dispose() of client...
            }
        });
        
        doAnswer(invocation -> 
        {
        	acceptCTS.cancel(); //Cancel the task so that the listener will stop listening.
        	IAsyncSocketChannel clientSocket = Mockito.mock(IAsyncSocketChannel.class);
            doAnswer(receiveInvocation -> 
            {
                return CompletableFuture.completedFuture(0);
            }).when(clientSocket).read(any(ByteBuffer.class)); //Return 0 bytes because it shouldn't matter for this test.
        	
        	return CompletableFuture.completedFuture(clientSocket);
        }).when(mockSocket).accept();
        
        try
        {
	        doAnswer(invocation -> 
	        {
	        	return anyInt(); //Not sure why this works, bind() is supposed to return a void...
	        }).when(mockSocket).bind(new InetSocketAddress(anyString(), anyInt()));
	    }
        catch(Exception exception)
        {
        	Logger.log("Exception in NetworkClientTests.testMissedHeartbeats: " + exception.getMessage(), LogLevel.error);
        }
        
        try
        {
        	server.initializeAsync(new InetSocketAddress("127.0.0.1", 8080), 1024, acceptCTS);
        	Thread.sleep(5000);
        }
        catch(Exception exception)
        {
        	Logger.log("Exception in NetworkClientTests.testMissedHeartbeats: " + exception.getMessage(), LogLevel.error);
        }
        
        Assertions.assertTrue(missedHeartbeatDetected);
    }

    public CompletableFuture<Void> newClientOnClientDisconnected(NetworkClient client) 
    {
        return CompletableFuture.completedFuture(null);
    }
}
