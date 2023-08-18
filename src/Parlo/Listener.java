package Parlo;

import java.net.*;
import java.util.concurrent.*;
import java.util.logging.*;
import java.io.IOException;

public class Listener implements AutoCloseable 
{
    protected BlockingQueue<NetworkClient> networkClients = new LinkedBlockingQueue<>();
    protected IAsyncSocketChannel listenerSock;
    private InetSocketAddress localEP;
    private ClientDisconnectedFromListenerDelegate disconnectedCallback;
    private OnConnectedDelegate onConnected;
    public boolean applyCompression = true;
    
    private ExecutorService executor = Executors.newSingleThreadExecutor();

    public Listener(IAsyncSocketChannel sock) 
    {
        this.listenerSock = sock;
    }

    public void initializeAsync(InetSocketAddress localEP, int maxPacketSize) 
    		throws IOException 
    {
        this.localEP = localEP;

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
                        		OnClientDisconnected(client);
                        		networkClients.remove(client);
                        		//TODO: Dispose() of client...
                        	}
                        });
                        
                        newClient.setConnectionLostCallback(new OnConnectionLostDelegate()
                        {
                        	public void onConnectionLost(NetworkClient client)
                        	{
                        		Logger.log("Client connection lost!", LogLevel.info);
                        		OnClientDisconnected(client);
                        		networkClients.remove(client);
                        		//TODO: Dispose() of client...
                        	}
                        });

                        if (!applyCompression)
                            newClient.applyCompression = false;

                        networkClients.add(newClient);

                        if (onConnected != null)
                            OnClientConnected(newClient);
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
    private void OnClientDisconnected(NetworkClient client) 
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
    private void OnClientConnected(NetworkClient client) 
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

