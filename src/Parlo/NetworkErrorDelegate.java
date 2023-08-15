package Parlo;

import java.net.SocketException;

public interface NetworkErrorDelegate 
{
	void onNetworkError(Exception exception);
}
