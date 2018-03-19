package japster2.peer;

import java.net.InetSocketAddress;
import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;

public interface PeerNode extends Remote {
	
	public boolean hello(InetSocketAddress peerAddress) throws RemoteException;
	
	public void query(String msgId, long ttl, String fileName, String host, int port) throws RemoteException;
	
	public void hitquery(String msgId, long ttl, String fileName, FileLocation fileLocation) throws RemoteException;

}
