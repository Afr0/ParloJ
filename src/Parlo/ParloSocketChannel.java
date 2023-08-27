/*This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
If a copy of the MPL was not distributed with this file, You can obtain one at
http://mozilla.org/MPL/2.0/.

The Original Code is the Parlo library.

The Initial Developer of the Original Code is
Mats 'Afr0' Vederhus. All Rights Reserved.

Contributor(s): ______________________________________.
*/

package Parlo;

import java.io.IOException;
import java.net.*;
import java.nio.channels.*;
import java.nio.ByteBuffer;
import java.util.concurrent.Future;
import java.util.concurrent.*;

/**
 * A wrapper for the AsynchronousSocketChannel and AsynchronousServerSocketChannel that
 * implements IAsyncSocketChannel.
 */
public class ParloSocketChannel implements IAsyncSocketChannel
{

    private final Semaphore socketSemaphore;

    private AsynchronousSocketChannel channel;
    private AsynchronousServerSocketChannel serverChannel;
    private SocketAddress address;
    
    private boolean isServer = false;

    /**
     * Constructor used for constructing a new instance from accept().
     * @param ch The channel from which to construct a new instance.
     */
    public ParloSocketChannel(AsynchronousSocketChannel ch)
    {
    	channel = ch;
    	
    	int numLogicalProcessors = Runtime.getRuntime().availableProcessors();
        int numberOfCores = PhysicalCores.physicalCoreCount();
        socketSemaphore = new Semaphore((numberOfCores > numLogicalProcessors) ? 
        		numberOfCores : numLogicalProcessors);
    }
    
    public ParloSocketChannel(boolean server, boolean keepAlive) throws IOException 
    {
    	int numLogicalProcessors = Runtime.getRuntime().availableProcessors();
        int numberOfCores = PhysicalCores.physicalCoreCount();
        socketSemaphore = new Semaphore((numberOfCores > numLogicalProcessors) ? 
        		numberOfCores : numLogicalProcessors);
        
        isServer = server;
        
        if(!server)
        {
        	channel = AsynchronousSocketChannel.open();
        	channel.setOption(SocketOption.class.cast(SocketOptions.SO_KEEPALIVE), keepAlive);
        }
        else
        {
        	serverChannel = AsynchronousServerSocketChannel.open();
        	serverChannel.setOption(SocketOption.class.cast(SocketOptions.SO_KEEPALIVE), keepAlive);
        }
    }
	
	/**
	 * Binds the channel's socket to a local address.
	 * @param address The local address to bind to.
	 */
    public void bind(InetSocketAddress local) throws IOException 
    {
        try 
        {
            address = local;

            if(!isServer)
            	channel.bind(local);
            else
            	serverChannel.bind(local);
        } 
        catch (SocketException ex) 
        {
            Logger.log("SocketException in ParloSocketChannel.bind: " + ex.getMessage(), LogLevel.error);
            throw ex;
        }
    }
    
	/**
	 * Sets the value of a socket option.
	 * @param name The name of the socket option to set.
	 * @param value The value of the socket option to set.
	 */
    public <T> void setOption(SocketOption<T> name, T value) throws IOException
    {
    	socketSemaphore.acquireUninterruptibly();
    	
    	if(!isServer)
    		channel = channel.setOption(name, value);
    	else
    		serverChannel = serverChannel.setOption(name, value);
    	
    	socketSemaphore.release();
    }
    
	/**
	 * Accepts a connection.
	 * @return A Future of type IAsyncSocketChannel.
	 */
	public Future<IAsyncSocketChannel> accept()
	{
		socketSemaphore.acquireUninterruptibly();
		
	    if (isServer) 
	    {
	    	try
	    	{
		        Future<AsynchronousSocketChannel> futureChannel = serverChannel.accept();
		        return new Future<IAsyncSocketChannel>() 
		        {
		            @Override
		            public boolean cancel(boolean mayInterruptIfRunning) {
		                return futureChannel.cancel(mayInterruptIfRunning);
		            }
	
		            @Override
		            public IAsyncSocketChannel get() throws InterruptedException, ExecutionException 
		            {
		                return new ParloSocketChannel(futureChannel.get());
		            }
	
		            @Override
		            public IAsyncSocketChannel get(long timeout, TimeUnit unit)
		                    throws InterruptedException, ExecutionException, TimeoutException 
		            {
		                return new ParloSocketChannel(futureChannel.get(timeout, unit));
		            }
	
		            @Override
		            public boolean isCancelled() 
		            {
		                return futureChannel.isCancelled();
		            }
	
		            @Override
		            public boolean isDone() 
		            {
		                return futureChannel.isDone();
		            }
		        };
	    	}
	    	finally
	    	{
	    		socketSemaphore.release();
	    	}
	    }
	    
	    return null;
	}
	
	/**
	 * Connects this channel.
	 * @param remote The remote address to connect to.
	 * @return A Future of type Void, or null if this is a server socket channel.
	 */
	public <A> void connect(SocketAddress remote, A attachment, CompletionHandler<Void,? super A> handler)
	{
		socketSemaphore.acquireUninterruptibly();
		
		try
		{
			if(!isServer)
				channel.connect(remote, attachment, handler);
		}
		finally
		{
			socketSemaphore.release();
		}
	}
	
	/**
	 * Returns the remote address to which this channel's socket is connected.
	 * @return The remote address to which this channel's socket is connected,
	 * or null if this is a server socket channel.
	 */
	public SocketAddress getRemoteAddress() throws IOException
	{
		if(!isServer)
			return channel.getRemoteAddress();
		else
			return null;
	}
	
	/**
	 * Returns the socket address that this channel's socket is bound to.
	 * @return The socket address that this channel's socket is bound to.
	 */
	public SocketAddress getLocalAddress() throws IOException
	{
		if(!isServer)
			return channel.getLocalAddress();
		else
			return serverChannel.getLocalAddress();
	}
	
	/**
	 * Reads a sequence of bytes from this channel into the given buffer.
	 * @param dst The destination to read into.
	 * @return A Future of type Integer, or null if this is a server socket channel.
	 */
	public Future<Integer> read(ByteBuffer dst)
	{
		socketSemaphore.acquireUninterruptibly();
		
		try
		{
			if(!isServer)
				return channel.read(dst);
			else
				return null;
		}
		finally
		{
			socketSemaphore.release();
		}
	}
	
	/**
	 * Writes a sequence of bytes to this channel from the given buffer.
	 * @param src The source to write from.
	 * @return A Future of type Integer, or null if this is a server socket channel.
	 */
	public <A> void write(ByteBuffer src, A attachment, 
			CompletionHandler<Integer,? super A> handler)
	{
		if(!isServer)
			channel.write(src, attachment, handler);
	}

	/**
	 * Tells whether or not this channels is open.
	 * @return True if, and only if, this channel is open.
	 */
	public boolean isOpen() 
	{
		if(isServer)
			return serverChannel.isOpen();
		else
			return channel.isOpen();
	}

	/**
	 * Shutdown the connection for reading without closing the channel. 
	 * Once shutdown for reading then further reads on the channel will 
	 * return -1, the end-of-stream indication. 
	 * If the input side of the connection is already shutdown then invoking 
	 * this method has no effect.The effect on an outstanding read operation 
	 * is system dependent and therefore not specified. The effect, if any, 
	 * when there is data in the socket receive buffer that has not been read, 
	 * or data arrives subsequently,is also system dependent.
	 */
	public void shutdownInput() throws IOException
	{
		if(!isServer)
			channel = channel.shutdownInput();
	}

	/**
	 * Shutdown the connection for writing without closing the channel. 
	 * Once shutdown for writing then further attempts to write to the 
	 * channel will throw ClosedChannelException. If the output side of 
	 * the connection is already shutdown then invoking this method has no 
	 * effect. The effect on an outstanding write operation is system dependent 
	 * and therefore not specified.
	 */
	public void shutdownOutput() throws IOException 
	{
		if(!isServer)
			channel = channel.shutdownOutput();
		
	}

	/**
	 * Closes this channel. 
	 * Any outstanding asynchronous operations upon this channel will 
	 * complete with the exception AsynchronousCloseException. After a 
	 * channel is closed, further attempts to initiate asynchronous I/O 
	 * operations complete immediately with cause ClosedChannelException. 
	 */
	public void close() throws IOException
	{
		if(isServer)
			serverChannel.close();
		else
			channel.close();
	}
}