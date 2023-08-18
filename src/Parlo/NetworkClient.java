package Parlo;

import Parlo.Packets.*;
import Parlo.Exceptions.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.time.*;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.nio.channels.CompletionHandler;
import java.io.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class NetworkClient
{
	private ClientDisconnectedDelegate disconnectedCallback;
	private ServerDisconnectedDelegate serverDisconnectedCallback;
	private OnConnectionLostDelegate connectionLostCallback;
	private OnConnectedDelegate connectedCallback;
	private NetworkErrorDelegate networkErrorCallback;
	private OnReceivedHeartbeatDelegate receivedHeartbeatCallback;
	private ReceivedPacketDelegate receivedDataCallback;
	
	private Instant lastHeartbeatSent;
	
	private Listener server;
	private IAsyncSocketChannel sockChannel;
	private ProcessingBuffer processingBuffer;
	
	//The last recorded Round Trip Time.
	private int lastRTT;
	
	private Semaphore missedHeartbeatsLock;
	private int missedHeartbeats = 0;
	private int heartbeatInterval = 30; //In seconds.
	private int maxMissedHeartbeats = 6;
	
	private Semaphore isAliveLock;
	private boolean isAlive = true;
	
	private Semaphore connectedLock;
	private boolean connected = false;
	
	private InetSocketAddress localEP;
	
	/**
	* Is this client connected?
	* @return True if it is, false if it isn't.
	*/
	public boolean isConnected()
	{
		return connected;
	}
	
	/**
	 * Should compression be applied to data before transmission?
	 */
	public boolean applyCompression = false;
	
	private ByteBuffer recvBuf;
	
	/**
	 * The threshold size for packet compression, in bytes.
	 * Packets smaller than this won't be compressed.
	 */
	public int compressionThreshold = 500;
	
	/**
	 * The RTT (Round Trip Time) compression threshold.
	 * Defaults to 100 ms.
	 */
	public int RTTcompressionThreshold = 100;
	
	private ExecutorService executor = Executors.newSingleThreadExecutor();
	
	/**
	 * Sets a callback function to be notified when this NetworkClient disconnected from 
	 * a server.
	 * @param callback The callback function to be notified.
	 */
    public void setClientDisconnectedCallback(ClientDisconnectedDelegate callback) 
    {
        this.disconnectedCallback = callback;
    }
    
    /**
	 * Sets a callback function to be notified when this NetworkClient disconnected received
	 * a notification that the server is about to disconnect.
     * @param callback The callback function to be notified.
     */
    public void setServerDisconnectedCallback(ServerDisconnectedDelegate callback)
    {
    	this.serverDisconnectedCallback = callback;
    }
    
    private void onClientDisconnected(NetworkClient client)
    {
    	if(disconnectedCallback != null)
    		disconnectedCallback.onClientDisconnected(client);
    }
    
    private void onServerDisconnected(NetworkClient client)
    {
    	if(serverDisconnectedCallback != null)
    		serverDisconnectedCallback.onServerDisconnected(client);
    }
    
	/**
	 * Sets a callback function to be notified when this NetworkClient
	 * lost the connection to a server.
	 * @param callback The callback function to be notified.
	 */
    public void setConnectionLostCallback(OnConnectionLostDelegate callback) 
    {
        this.connectionLostCallback = callback;
    }
    
    private void onConnectionLost(NetworkClient client)
    {
    	if(connectionLostCallback != null)
    		this.connectionLostCallback.onConnectionLost(client);
    }
    
	/**
	 * Sets a callback function to be notified when this NetworkClient
	 * connected to a server.
	 * @param callback The callback function to be notified.
	 */
    public void setConnectedCallback(OnConnectedDelegate callback) 
    {
    	this.connectedCallback = callback;
    }
    
    private void onConnected(NetworkClient client)
    {
    	if(connectedCallback != null)
    		this.connectedCallback.onConnected(client);
    }
    
	/**
	 * Sets a callback function to be notified when this NetworkClient 
	 * received a heartbeat from the server.
	 * @param callback The callback function to be notified.
	 */
    public void setReceivedHeartbeatCallback(OnReceivedHeartbeatDelegate callback)
    {
    	this.receivedHeartbeatCallback = callback;
    }
    
    private void onReceivedHeartbeat(NetworkClient client)
    {
    	if(receivedHeartbeatCallback != null)
    		this.receivedHeartbeatCallback.onReceivedHeartbeat(client);
    }
    
	/**
	 * Sets a callback function to be notified when this NetworkClient 
	 * received data from the server.
	 * @param callback The callback function to be notified.
	 */
    public void setReceivedDataCallback(ReceivedPacketDelegate callback)
    {
    	this.receivedDataCallback = callback;
    }
    
    private void onReceivedData(NetworkClient client, Packet packet) throws ExecutionException, InterruptedException
    {
    	if(receivedDataCallback != null)
    		this.receivedDataCallback.onReceivedPacket(client, packet).get();
    }
    
	/**
	 * Sets a callback function to be notified when a network
	 * error occurred.
	 * @param callback The callback function to be notified.
	 */
    public void setNetworkErrorCallback(NetworkErrorDelegate callback)
    {
    	this.networkErrorCallback = callback;
    }
    
    private void onNetworkError(Exception exception)
    {
    	if(networkErrorCallback != null)
    		networkErrorCallback.onNetworkError(exception);
    }
    
    /**
     * Initializes a client that listens for data.
     * Consumed by Parlo.tests.NetworkClientTests.
     * @param clientChannel The client's channel
     * @param server The Listener instance calling this constructor.
     * @param heartbeatInterval The interval at which heartbeats are sent.
     * @param onClientDisconnectedDelegate The delegate to be called when the client disconnects. Can be null.
     * @param onClientConnectionLost The delegate to be called when the client's connection is lost. Can be null.
     */
    public NetworkClient(IAsyncSocketChannel clientChannel, Listener server, int heartbeatInterval, 
    		ClientDisconnectedDelegate onClientDisconnectedDelegate, OnConnectionLostDelegate onClientConnectionLost)
    {
    	if(clientChannel == null || server == null)
    		throw new IllegalArgumentException("clientChannel or server was null in NetworkClient constructor!");
    	
    	this.sockChannel = clientChannel;
    	this.server = server;
    	
    	recvBuf = ByteBuffer.wrap(new byte[ProcessingBuffer.MAX_PACKET_SIZE]);
    	
        int numberOfCores = PhysicalCores.physicalCoreCount();
        int numLogicalProcessors = Runtime.getRuntime().availableProcessors();
    	
    	missedHeartbeatsLock = new Semaphore((numberOfCores > numLogicalProcessors) ? 
        		numberOfCores : numLogicalProcessors);
    	isAliveLock = new Semaphore((numberOfCores > numLogicalProcessors) ? 
        		numberOfCores : numLogicalProcessors);
    	connectedLock = new Semaphore((numberOfCores > numLogicalProcessors) ? 
        		numberOfCores : numLogicalProcessors);
    	
    	this.processingBuffer = new ProcessingBuffer(new ProcessedPacketDelegate()
    	{
    		public void onProcessedPacket(Packet packet)
    		{
                if (packet.getID() == (byte)ParloIDs.SGoodbye.ordinal())
                {
                    onServerDisconnected(NetworkClient.this);
                    return;
                }
                //Client notified server of disconnection.
                if (packet.getID() == (byte)ParloIDs.CGoodbye.ordinal())
                {
                    onClientDisconnected(NetworkClient.this);
                    return;
                }
                if (packet.getID() == (byte)ParloIDs.Heartbeat.ordinal())
                {
                	//isAlive and missedHeartbeats will be updated asynchronously,
                	//but it shouldn't matter in this case because the proceeding
                	//code doesn't depend on them.
                    SemaphoreUtils.waitAsync(isAliveLock).thenRun(() -> {
                        isAlive = true;
                        isAliveLock.release();
                    });

                    SemaphoreUtils.waitAsync(missedHeartbeatsLock).thenRun(() -> {
                        missedHeartbeats = 0;
                        missedHeartbeatsLock.release();
                    });

                    HeartbeatPacket Heartbeat = HeartbeatPacket.byteArrayToObject(packet.getData());
                    // Calculate the duration between now and the timestamp from the Heartbeat packet
                    Duration duration = Duration.between(Instant.now(), Heartbeat.getSentTimestamp());
                    lastRTT = (int)(duration.toMillis() + Heartbeat.getTimeSinceLast().toMillis());
                    
                    onReceivedHeartbeat(NetworkClient.this);

                    return;
                }

                if (packet.getIsCompressed() == 1)
                {
                	try
                	{
	                    byte[] DecompressedData = decompressData(packet.getData());
	                    onReceivedData(NetworkClient.this, new Packet(packet.getID(), 
	                    		DecompressedData, false));
                	}
                	catch(IOException exception)
                	{
                		Logger.log("Received badly compressed data!", LogLevel.error);
                	}
                	catch(InterruptedException exception)
                	{
                		Logger.log("Thread was interrupted: " + exception.getMessage(), 
                				LogLevel.error);
                	}
                	catch(ExecutionException exception)
                	{
                		Logger.log("ExecutionException: " + exception.getMessage(), 
                				LogLevel.error);
                	}
                }
                else
                {
                	try
                	{
                		onReceivedData(NetworkClient.this, packet);
                	}
                	catch(InterruptedException exception)
                	{
                		Logger.log("Thread was interrupted: " + exception.getMessage(), 
                				LogLevel.error);
                	}
                	catch(ExecutionException exception)
                	{
                		Logger.log("ExecutionException: " + exception.getMessage(), 
                				LogLevel.error);
                	}
                }
    		}
    	});
    	
    	setClientDisconnectedCallback(onClientDisconnectedDelegate);
    	setConnectionLostCallback(onClientConnectionLost);
    	
    	missedHeartbeats = 0;
    	this.heartbeatInterval = heartbeatInterval;
    	checkForMissedHeartbeats();
    	
        SemaphoreUtils.waitAsync(connectedLock).thenRun(() -> 
        {
            connected = true;
            connectedLock.release();
        });
        
        receiveAsync();
    }
    
    /**
     * Creates a new NetworkClient for connecting to a remote server.
     * @param sockChannel The IAsyncSocketChannel for connecting.
     */
    public NetworkClient(IAsyncSocketChannel sockChannel)
    {
    	if(sockChannel == null)
    		throw new IllegalArgumentException("sockChannel was null!");
    	
        int numberOfCores = PhysicalCores.physicalCoreCount();
        int numLogicalProcessors = Runtime.getRuntime().availableProcessors();
    	
    	missedHeartbeatsLock = new Semaphore((numberOfCores > numLogicalProcessors) ? 
        		numberOfCores : numLogicalProcessors);
    	isAliveLock = new Semaphore((numberOfCores > numLogicalProcessors) ? 
        		numberOfCores : numLogicalProcessors);
    	connectedLock = new Semaphore((numberOfCores > numLogicalProcessors) ? 
        		numberOfCores : numLogicalProcessors);
    	
    	this.sockChannel = sockChannel;
    	this.processingBuffer = new ProcessingBuffer(new ProcessedPacketDelegate()
    	{
    		public void onProcessedPacket(Packet packet)
    		{
                if (packet.getID() == (byte)ParloIDs.SGoodbye.ordinal())
                {
                    onServerDisconnected(NetworkClient.this);
                    return;
                }
                //Client notified server of disconnection.
                if (packet.getID() == (byte)ParloIDs.CGoodbye.ordinal())
                {
                    onClientDisconnected(NetworkClient.this);
                    return;
                }
                if (packet.getID() == (byte)ParloIDs.Heartbeat.ordinal())
                {
                	//isAlive and missedHeartbeats will be updated asynchronously,
                	//but it shouldn't matter in this case because the proceeding
                	//code doesn't depend on them.
                    SemaphoreUtils.waitAsync(isAliveLock).thenRun(() -> {
                        isAlive = true;
                        isAliveLock.release();
                    });

                    SemaphoreUtils.waitAsync(missedHeartbeatsLock).thenRun(() -> {
                        missedHeartbeats = 0;
                        missedHeartbeatsLock.release();
                    });

                    HeartbeatPacket Heartbeat = HeartbeatPacket.byteArrayToObject(packet.getData());
                    // Calculate the duration between now and the timestamp from the Heartbeat packet
                    Duration duration = Duration.between(Instant.now(), Heartbeat.getSentTimestamp());
                    lastRTT = (int)(duration.toMillis() + Heartbeat.getTimeSinceLast().toMillis());
                    
                    onReceivedHeartbeat(NetworkClient.this);

                    return;
                }

                if (packet.getIsCompressed() == 1)
                {
                	try
                	{
	                    byte[] DecompressedData = decompressData(packet.getData());
	                    onReceivedData(NetworkClient.this, new Packet(packet.getID(), 
	                    		DecompressedData, false));
                	}
                	catch(IOException exception)
                	{
                		Logger.log("Received badly compressed data!", LogLevel.error);
                	}
                	catch(InterruptedException exception)
                	{
                		Logger.log("Thread was interrupted: " + exception.getMessage(), 
                				LogLevel.error);
                	}
                	catch(ExecutionException exception)
                	{
                		Logger.log("ExecutionException: " + exception.getMessage(), 
                				LogLevel.error);
                	}
                }
                else
                {
                	try
                	{
                		onReceivedData(NetworkClient.this, packet);
                	}
                	catch(InterruptedException exception)
                	{
                		Logger.log("Thread was interrupted: " + exception.getMessage(), 
                				LogLevel.error);
                	}
                	catch(ExecutionException exception)
                	{
                		Logger.log("ExecutionException: " + exception.getMessage(), 
                				LogLevel.error);
                	}
                }
    		}
    	});
    	
    	recvBuf = ByteBuffer.wrap(new byte[ProcessingBuffer.MAX_PACKET_SIZE]);
    }
    
    /**
     * Creates a new NetworkClient instance that listens for data.
     * Consumed by the Listener class.
     * @param SockChannel The IAsyncSocketChannel to use for sending and receiving data.
     * @param Server The Listener instance that accepted this client.
     * @param MaxPacketSize The maximum packet size.
     */
    public NetworkClient(IAsyncSocketChannel sockChannel, Listener server)
    {
        int numberOfCores = PhysicalCores.physicalCoreCount();
        int numLogicalProcessors = Runtime.getRuntime().availableProcessors();
    	
    	missedHeartbeatsLock = new Semaphore((numberOfCores > numLogicalProcessors) ? 
        		numberOfCores : numLogicalProcessors);
    	isAliveLock = new Semaphore((numberOfCores > numLogicalProcessors) ? 
        		numberOfCores : numLogicalProcessors);
    	connectedLock = new Semaphore((numberOfCores > numLogicalProcessors) ? 
        		numberOfCores : numLogicalProcessors);
    	
    	this.server = server;
    	this.sockChannel = sockChannel;
    	this.processingBuffer = new ProcessingBuffer(new ProcessedPacketDelegate()
    	{
    		public void onProcessedPacket(Packet packet)
    		{
                if (packet.getID() == (byte)ParloIDs.SGoodbye.ordinal())
                {
                    onServerDisconnected(NetworkClient.this);
                    return;
                }
                //Client notified server of disconnection.
                if (packet.getID() == (byte)ParloIDs.CGoodbye.ordinal())
                {
                    onClientDisconnected(NetworkClient.this);
                    return;
                }
                if (packet.getID() == (byte)ParloIDs.Heartbeat.ordinal())
                {
                	//isAlive and missedHeartbeats will be updated asynchronously,
                	//but it shouldn't matter in this case because the proceeding
                	//code doesn't depend on them.
                    SemaphoreUtils.waitAsync(isAliveLock).thenRun(() -> {
                        isAlive = true;
                        isAliveLock.release();
                    });

                    SemaphoreUtils.waitAsync(missedHeartbeatsLock).thenRun(() -> {
                        missedHeartbeats = 0;
                        missedHeartbeatsLock.release();
                    });

                    HeartbeatPacket Heartbeat = HeartbeatPacket.byteArrayToObject(packet.getData());
                    // Calculate the duration between now and the timestamp from the Heartbeat packet
                    Duration duration = Duration.between(Instant.now(), Heartbeat.getSentTimestamp());
                    lastRTT = (int)(duration.toMillis() + Heartbeat.getTimeSinceLast().toMillis());
                    
                    onReceivedHeartbeat(NetworkClient.this);

                    return;
                }

                if (packet.getIsCompressed() == 1)
                {
                	try
                	{
	                    byte[] DecompressedData = decompressData(packet.getData());
	                    onReceivedData(NetworkClient.this, new Packet(packet.getID(), 
	                    		DecompressedData, false));
                	}
                	catch(IOException exception)
                	{
                		Logger.log("Received badly compressed data!", LogLevel.error);
                	}
                	catch(InterruptedException exception)
                	{
                		Logger.log("Thread was interrupted: " + exception.getMessage(), 
                				LogLevel.error);
                	}
                	catch(ExecutionException exception)
                	{
                		Logger.log("ExecutionException: " + exception.getMessage(), 
                				LogLevel.error);
                	}
                }
                else
                {
                	try
                	{
                		onReceivedData(NetworkClient.this, packet);
                	}
                	catch(InterruptedException exception)
                	{
                		Logger.log("Thread was interrupted: " + exception.getMessage(), 
                				LogLevel.error);
                	}
                	catch(ExecutionException exception)
                	{
                		Logger.log("ExecutionException: " + exception.getMessage(), 
                				LogLevel.error);
                	}
                }
    		}
    	});
    	
        recvBuf = ByteBuffer.wrap(new byte[ProcessingBuffer.MAX_PACKET_SIZE]);
        
        try
        {
	        connectedLock.acquire();
	        connected = true;
	        connectedLock.release();
        }
        catch(InterruptedException exception)
        {
        	Logger.log("Thread was interrupted while acquiring lock", LogLevel.error);
        	//TODO: Implement callback...
        }
        
        receiveAsync();
        checkForMissedHeartbeats();
    }
    
    private boolean shouldCompressData(byte[] Data, int RTT)
    {
        if(Data == null)
            throw new IllegalArgumentException("Data");
        
        if(!applyCompression)
        	return false;
        
        if(Data.length < compressionThreshold)
        	return false;
        
        if(RTT > RTTcompressionThreshold)
        	return true;
        
        return false;
    }
    
    /**
     * Connects to a server. Starts receiving data if the
     * connection was successful.
     * If an exception occurs, the onNetworkError callback is invoked.
     * @param args Arguments used for login.
     */
    public void connectAsync(LoginArgsContainer args)
    {
    	if(args == null)
    		throw new IllegalArgumentException("args");
    	
    	if(!sockChannel.isOpen())
    	{
    		localEP = InetSocketAddress.createUnresolved(args.Address, args.Port);
    		sockChannel.connect(localEP, null, new CompletionHandler<Void, Void>()
    		{
    			public void completed(Void result, Void attachment)
    			{
    				receiveAsync();
    				sendHeartbeatAsync();
    				onConnected(NetworkClient.this);
    			}
    			public void failed(Throwable t, Void attachment)
    			{
    				onNetworkError((Exception)t);
    			}
    		});
    	}
    }

    /**
     * Compresses data with GZip.
     *
     * @param data The data to compress.
     * @return The compressed data as an array of bytes.
     * @throws IllegalArgumentException Thrown if data is null.
     */
    private byte[] compressData(byte[] data) throws IOException 
    {
        if (data == null) {
            throw new IllegalArgumentException("Data must not be null.");
        }

        try (ByteArrayOutputStream compressedStream = new ByteArrayOutputStream()) {
            try (GZIPOutputStream gzipStream = new GZIPOutputStream(compressedStream)) {
                int offset = 3;
                gzipStream.write(data, offset, data.length - offset);
            }

            return compressedStream.toByteArray();
        }
    }

    /**
     * Decompresses data with GZip.
     *
     * @param data The data to decompress.
     * @return The decompressed data as an array of bytes.
     * @throws IllegalArgumentException Thrown if data is null.
     */
    private byte[] decompressData(byte[] data) throws IOException 
    {
        if (data == null) {
            throw new IllegalArgumentException("Data must not be null.");
        }

        try (ByteArrayInputStream compressedStream = new ByteArrayInputStream(data)) {
            try (GZIPInputStream gzipStream = new GZIPInputStream(compressedStream)) {
                try (ByteArrayOutputStream decompressedStream = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[1024];
                    int len;
                    while ((len = gzipStream.read(buffer)) > 0) {
                        decompressedStream.write(buffer, 0, len);
                    }

                    return decompressedStream.toByteArray();
                }
            }
        }
    }
    
    /**
     * Asynchronously sends data to a connected client or server.
     * @param data The data to send.
     * @throws BufferOverflowException Thrown if the size of data
     * is larger than ProcessingBuffer.MAX_PACKET_SIZE.
     */
    public void sendAsync(byte[] data) throws BufferOverflowException
    {
    	byte[] compressedData;
    	
    	if(data == null || data.length < 1)
    		throw new IllegalArgumentException("Data must not be null.");
        if (data.length > ProcessingBuffer.MAX_PACKET_SIZE)
        	//Houston, we have a problem - ABANDON SHIP!
            throw new BufferOverflowException("Tried to send a packet larger than ProcessingBuffer.MAX_PACKET_SIZE!");
        
        try
        {
	        if(connected)
	        {
	        	if(shouldCompressData(data, lastRTT))
	        	{
	        		compressedData = compressData(data);
	        		Packet compressedPacket = new Packet(data[0], compressedData, true);
	        		sockChannel.write(ByteBuffer.wrap(compressedData), null, new CompletionHandler<Integer, Void>()
	        		{
	        			public void completed(Integer bytesSent, Void attachment)
	        			{
	        				//No need to do anything here...
	        			}
	        			
	        			public void failed(Throwable t, Void attachment)
	        			{
	        				NetworkClient.this.onNetworkError((Exception)t);
	        			}
	        		});
	        		compressedData = null;
	        	}
	        	else
	        	{
	        		sockChannel.write(ByteBuffer.wrap(data), null, new CompletionHandler<Integer, Void>()
	        		{
	        			public void completed(Integer bytesSent, Void attachment)
	        			{
	        				//No need to do anything here...
	        			}
	        			
	        			public void failed(Throwable t, Void attachment)
	        			{
	        				NetworkClient.this.onNetworkError((Exception)t);
	        			}
	        		});
	        	}
	        }
	        else
	        	throw new SocketException("NetworkClient: Tried sending data while not connected!");
        }
        catch(Exception exception)
        {
            Logger.log("Error sending data: " + exception.getMessage(), LogLevel.error);

            //Disconnect without sending the disconnect message to prevent recursion.
            disconnectAsync(false);
        }
    }
    
    /**
     * Asynchronously receives data.
     */
    private void receiveAsync()
    {    	
        CompletableFuture<?> future = CompletableFuture.runAsync(() ->
        {
        	byte[] tmpBuf;
        	
        	while(connected)
    		{
    			if(sockChannel == null || !sockChannel.isOpen())
    			return;
    		
    			try
    			{
    				int bytesRead = sockChannel.read(recvBuf).get();
    			
    				if(bytesRead > 0)
    				{
    					tmpBuf = new byte[bytesRead];
    					System.arraycopy(recvBuf, 0, tmpBuf, 0, bytesRead);
    					//Clear, to make sure this buffer is always fresh.
    					recvBuf.clear();
    				
                    	try
                    	{
                        	//Keep shoveling shit into the buffer as fast as we can.
                        	processingBuffer.addData(tmpBuf); //Hence the Shoveling Shit Algorithm (SSA).
                    	}
                    	catch(BufferOverflowException bufferOverflowException)
                    	{
                        	Logger.log("Tried adding too much data into ProcessingBuffer!", LogLevel.warn);
                        	//This should never happen, so we don't need to do anything here.
                    	}
    				}
                	else //Can't do anything with this!
                	{
                    	disconnectAsync(false);
                    	return;
                	}
    			
    				Thread.sleep(10); //STOP HOGGING THE PROCESSOR!!
    			}
    			catch(Exception exception)
    			{
                	Logger.log("Exception in NetworkClient.ReceiveAsync: " + exception.getMessage(), 
                			LogLevel.error);
                	disconnectAsync(false);
                	return;
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
    
    /**
     *  Disconnects this NetworkClient instance and stops
        all sending and receiving of data.
     * @param sendDisconnectMessage Should a DisconnectMessage be sent?
     */
    private void disconnectAsync(boolean sendDisconnectMessage)
    {
    	try
    	{
    		if(connected && sockChannel != null)
    		{
    			if(sendDisconnectMessage)
    			{
                    //Set the timeout to five seconds by default for clients,
                    //even though it's not really important for clients.
                    GoodbyePacket ByePacket = new GoodbyePacket(ParloDefaultTimeouts.Client.ordinal());
                    byte[] ByeData = ByePacket.ToByteArray();
                    Packet Goodbye = new Packet((byte)ParloIDs.CGoodbye.ordinal(), ByeData, false);
                    sendAsync(Goodbye.buildPacket());
    			}
    		}
    		
            if (sockChannel.isOpen())
            {
                // Shutdown and disconnect the socket.
            	sockChannel.shutdownInput();
            	sockChannel.shutdownOutput();
            	sockChannel.close();
            }

            SemaphoreUtils.waitAsync(connectedLock);
            connected = false;
            connectedLock.release();
    	}
    	catch(SocketException exception)
    	{
    		Logger.log("SocketException happened during NetworkClient.disconnectAsync():" + exception.getMessage(), 
    				LogLevel.error);
    	}
    	catch(IOException exception)
    	{
    		Logger.log("Couldn't shutdown socket in NetworkClient.DisconnectAsync():" + exception.getMessage(), 
    				LogLevel.error);
    	}
    	catch(BufferOverflowException exception)
    	{
    		Logger.log("NetworkClient.disconnectAsync() tried sending a Goodbye packet that was too large:" + exception.getMessage(), 
    				LogLevel.error);
    	}
    }
    
    private void sendHeartbeatAsync()
    {
    	CompletableFuture<?> future = CompletableFuture.runAsync(() ->
    	{
    		while(true)
    		{
    			try
    			{
    				HeartbeatPacket heartbeat;
    				if (Instant.now().isAfter(lastHeartbeatSent)) 
    				{
    				    Duration difference = Duration.between(lastHeartbeatSent, Instant.now());
    				    heartbeat = new HeartbeatPacket(difference);
    				}
    				else 
    				{
    				    Duration difference = Duration.between(Instant.now(), lastHeartbeatSent);
    				    heartbeat = new HeartbeatPacket(difference);
    				}
    				
    				lastHeartbeatSent = Instant.now();
                    byte[] heartbeatData = heartbeat.toByteArray();
                    Packet Pulse = new Packet((byte)ParloIDs.Heartbeat.ordinal(), 
                    		heartbeatData, false);
                    sendAsync(Pulse.buildPacket());
                    
                    Thread.sleep(heartbeatInterval * 1000);
    			}
    			catch(Exception exception)
    			{
    				Logger.log("Error sending heartbeat: " + exception.getMessage(), 
    						LogLevel.error);
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
    
    private void checkForMissedHeartbeats()
    {
    	CompletableFuture future = CompletableFuture.runAsync(() ->
    	{
    		try
    		{
		    	while(true)
		    	{
		    		missedHeartbeatsLock.acquire();
		    		missedHeartbeats++;
		    		missedHeartbeatsLock.release();
		    		
		    		if(missedHeartbeats > maxMissedHeartbeats)
		    		{
		    			isAliveLock.acquire();
		    			isAlive = false;
		    			isAliveLock.release();
		    			
		    			onConnectionLost(NetworkClient.this);
		    		}
		    		
		    		Thread.sleep(heartbeatInterval * 1000);
		    	}
    		}
    		catch(Exception exception)
    		{
    			Logger.log("Error while checking for missed heartbeats: " + 
    					exception.getMessage(), LogLevel.error);
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
}
