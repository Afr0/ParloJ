package Parlo;
import Parlo.Packets.*;

public interface OnReceivedHeartbeatDelegate {
	void onReceivedHeartbeat(NetworkClient client);
}
