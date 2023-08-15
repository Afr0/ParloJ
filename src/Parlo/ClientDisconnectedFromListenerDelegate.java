package Parlo;

import java.util.concurrent.*;

public interface ClientDisconnectedFromListenerDelegate 
{
	void onClientDisconnected(NetworkClient client);
}
