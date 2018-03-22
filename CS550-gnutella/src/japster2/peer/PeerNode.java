package japster2.peer;

import java.net.InetSocketAddress;
import java.rmi.Remote;
import java.rmi.RemoteException;

public interface PeerNode extends Remote {
	
	public boolean hello(InetSocketAddress peerAddress) throws RemoteException;
	
	public void query(String msgId, long ttl, String fileName, String host, int port) throws RemoteException;
	
	public void hitquery(String msgId, long ttl, String fileName, FileLocation fileLocation) throws RemoteException;

	public void invalidate(String msgId, long ttl, String fileName, FileLocation fileLocation, String host, int port) throws RemoteException;

	public int poll(String fileName, int version) throws RemoteException; 
}
