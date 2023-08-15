package Parlo;

import java.net.*;
import java.nio.channels.*;
import java.nio.ByteBuffer;
import java.util.concurrent.Future;
import java.io.IOException;

public interface IAsyncSocketChannel 
{
	/**
	 * Binds the channel's socket to a local address.
	 * @param address The local address to bind to.
	 */
	abstract void bind(InetSocketAddress local) throws IOException;
	
	/**
	 * Accepts a connection.
	 * @return A Future of type IAsyncSocketChannel.
	 */
	abstract Future<IAsyncSocketChannel> accept();
	
	/**
	 * Connects this channel.
	 * @param remote The remote address to connect to.
	 * @param completionHandler 
	 * @param object 
	 * @return A Future of type Void.
	 */
	abstract <A> void connect(SocketAddress remote, A attachment, 
			CompletionHandler<Void,? super A> handler);
	
	/**
	 * Returns the socket address that this channel's socket is bound to.
	 * @return The socket address that this channel's socket is bound to.
	 */
	abstract SocketAddress getLocalAddress() throws IOException;
	
	/**
	 * Returns the remote address to which this channel's socket is connected.
	 * @return The remote address to which this channel's socket is connected.
	 */
	abstract SocketAddress getRemoteAddress() throws IOException;
	
	/**
	 * Reads a sequence of bytes from this channel into the given buffer.
	 * @param dst The destination to read into.
	 * @return A Future of type Integer.
	 */
	abstract Future<Integer> read(ByteBuffer dst);

	/**
	 * Writes a sequence of bytes to this channel from the given buffer.
	 * @param src The source to write from.
	 * @return A Future of type Integer.
	 */
	abstract <A> void write(ByteBuffer src, A attachment, 
			CompletionHandler<Integer,? super A> handler);
	
	/**
	 * Sets the value of a socket option.
	 * @param name The name of the socket option to set.
	 * @param value The value of the socket option to set.
	 * @return An IAsyncSocketChannel instance with the option set.
	 */
	abstract <T> void setOption(SocketOption<T> name, T value) throws IOException;
	
	/**
	 * Tells whether or not this channels is open.
	 * @return True if, and only if, this channel is open.
	 */
	abstract boolean isOpen();
	
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
	abstract void shutdownInput() throws IOException;
	
	/**
	 * Shutdown the connection for writing without closing the channel. 
	 * Once shutdown for writing then further attempts to write to the 
	 * channel will throw ClosedChannelException. If the output side of 
	 * the connection is already shutdown then invoking this method has no 
	 * effect. The effect on an outstanding write operation is system dependent 
	 * and therefore not specified.
	 */
	abstract void shutdownOutput() throws IOException;
	
	/**
	 * Closes this channel. 
	 * Any outstanding asynchronous operations upon this channel will 
	 * complete with the exception AsynchronousCloseException. After a 
	 * channel is closed, further attempts to initiate asynchronous I/O 
	 * operations complete immediately with cause ClosedChannelException. 
	 */
	abstract void close() throws IOException;
}
