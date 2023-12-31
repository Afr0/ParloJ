/*This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
If a copy of the MPL was not distributed with this file, You can obtain one at
http://mozilla.org/MPL/2.0/.

The Original Code is the Parlo library.

The Initial Developer of the Original Code is
Mats 'Afr0' Vederhus. All Rights Reserved.

Contributor(s): ______________________________________.
*/

package Parlo;

import Parlo.CancellationTokenSource;
import java.net.*;
import java.util.concurrent.*;
import java.util.logging.*;
import java.io.IOException;

/**
 * Represents a listener that listens for incoming clients.
 */
public class Listener implements AutoCloseable 
{
    protected BlockingQueue<NetworkClient> networkClients = new LinkedBlockingQueue<>();
    protected IAsyncSocketChannel listenerSock;
    private InetSocketAddress localEP;
    private ClientDisconnectedFromListenerDelegate disconnectedCallback;
    private OnConnectedDelegate onConnected;
    public boolean applyCompression = true;
    
    protected ExecutorService executor = Executors.newSingleThreadExecutor();
    protected CancellationTokenSource acceptCTS;

    public Listener(IAsyncSocketChannel sock) 
    {
        this.listenerSock = sock;
    }

    public void initializeAsync(InetSocketAddress localEP, int maxPacketSize, CancellationTokenSource acceptCTS) 
    		throws IOException 
    {
        this.localEP = localEP;
        this.acceptCTS = acceptCTS;

        if (maxPacketSize != 1024)
            ProcessingBuffer.MAX_PACKET_SIZE = maxPacketSize;

        listenerSock.bind(localEP);

        acceptAsync();
    }

    protected void acceptAsync() 
    {
        CompletableFuture future = CompletableFuture.runAsync(() -> 
        {
            try 
            {
                while (true) 
                {
                	if(acceptCTS.isCancellationRequested())
                		break;
                	
                    IAsyncSocketChannel acceptedSocketInterface = listenerSock.accept().get();
                    ParloSocketChannel acceptedSocket = (ParloSocketChannel)acceptedSocketInterface;

                    if (acceptedSocket != null) 
                    {
                        Logger.log("New client connected!", LogLevel.info);
                        
                        acceptedSocket.setOption(StandardSocketOptions.SO_LINGER, 5);
                        NetworkClient newClient = new NetworkClient(acceptedSocket, this);
                        newClient.setClientDisconnectedCallback(new ClientDisconnectedDelegate() 
                        {
                        	public void onClientDisconnected(NetworkClient client)
                        	{
                        		Logger.log("Client disconnected!", LogLevel.info);
                        		onClientDisconnected(client);
                        		networkClients.remove(client);
                        		//TODO: Dispose() of client...
                        	}
                        });
                        
                        newClient.setConnectionLostCallback(new OnConnectionLostDelegate()
                        {
                        	public void onConnectionLost(NetworkClient client)
                        	{
                        		Logger.log("Client connection lost!", LogLevel.info);
                        		onClientDisconnected(client);
                        		networkClients.remove(client);
                        		//TODO: Dispose() of client...
                        	}
                        });

                        if (!applyCompression)
                            newClient.applyCompression = false;

                        networkClients.add(newClient);

                        if (onConnected != null)
                            onClientConnected(newClient);
                    }
                }
            } 
            catch (Exception e) 
            {
                Logger.log(e.getMessage(), LogLevel.error);
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
    
    /**
     * Sets a callback to be notified of when a client disconnected.
     * @param callback The callback to set.
     */
    public void setClientDisconnectedCallback(ClientDisconnectedFromListenerDelegate callback)
    {
    	this.disconnectedCallback = callback;
    }
    
    /**
     * Notifies a consumer that a client has disconnected.
     * @param client The client that disconnected.
     */
    protected void onClientDisconnected(NetworkClient client) 
    {
        if (disconnectedCallback != null)
            disconnectedCallback.onClientDisconnected(client);
    }
    
    /**
     * The client missed too many heartbeats, so assume it's disconnected.
     * @param client The client in question.
     */
    protected void onClientConnectionLost(NetworkClient client)
    {
        Logger.log("Client connection lost!", LogLevel.info);
        
        if (disconnectedCallback != null)
            disconnectedCallback.onClientDisconnected(client);
        
        try
        {
	        networkClients.take();
	        //client.DisposeAsync();
	        //Dispose();
        }
        catch(Exception exception)
        {
        	Logger.log("Exception in Listener.onClientConnectionLost: " + exception.getMessage(), LogLevel.info);
        }
    }
    
    /**
     * Sets a callback to be notified of when a client connected.
     * @param callback The callback to set.
     */
    public void setConnectedCallback(OnConnectedDelegate callback) 
    {
    	this.onConnected = callback;
    }
    
    /**
     * Notifies a consumer that a client has connected.
     * @param client The client that connected.
     */
    protected void onClientConnected(NetworkClient client) 
    {
        if (disconnectedCallback != null) 
            onConnected.onConnected(client);
    }

    @Override
    public void close() 
    {
        // Dispose resources
    }
}

