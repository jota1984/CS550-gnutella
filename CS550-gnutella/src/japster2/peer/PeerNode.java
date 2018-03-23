package japster2.peer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Interface of the remote object exposed by each peer
 * @author jota
 *
 */
public interface PeerNode extends Remote {
	
	/**
	 * Called when a peer wants to notify another peer that it has established neighbor relationship with. 
	 * The purpose of this call is so that the receiving peer can establish a neighbor relationship back to the 
	 * peer who made the call.
	 * 
	 * @param peerAddress Address of the peer that made the call, the receiving peer will add this address as a neighbor
	 * @return
	 * @throws RemoteException
	 */
	public boolean hello(InetSocketAddress peerAddress) throws RemoteException;
	
	/**
	 * Query searching for a file. Upon receiving this call the peer will broadcast it to all of its neighbors. If the receiving peer
	 * has the requested file it will also send hitquery with the FileLocation of the file. 
	 * 
	 * @param msgId 
	 * @param ttl 
	 * @param fileName
	 * @param host address of the peer that is making the query. Used by the receiving peer to avoid sending the query back to the sender
	 * and to know where to send the hitquery back. 
	 * @param port port of the peer that is making the query.Used by the receiving peer to avoid sending the query back to the sender
	 * and to know where to send the hitquery back.  
	 * @throws RemoteException
	 */
	public void query(String msgId, long ttl, String fileName, String host, int port) throws RemoteException;
	
	/**
	 * Response sent when a file is found after receiving a query. 
	 * @param msgId
	 * @param ttl
	 * @param fileName
	 * @param fileLocation FileLocation where the file can be downloaded from 
	 * @throws RemoteException
	 */
	public void hitquery(String msgId, long ttl, String fileName, FileLocation fileLocation) throws RemoteException;

	/**
	 * Called to notify the receiving peer that a FileLocation is no longer valid. 
	 * @param msgId
	 * @param ttl
	 * @param fileName
	 * @param fileLocation
	 * @param address address of the peer that is sending the message. Used by the receiving peer to avoid sending the message back to the sender.
	 * @param port port of the peer that is sending the message. Used by the receiving peer to avoid sending the message back to the sender.
	 * @throws RemoteException
	 */
	public void invalidate(String msgId, long ttl, String fileName, FileLocation fileLocation, String host, int port) throws RemoteException;

	
	/**
	 * Called to retrieve a FileLocation for a file on the peer. The retrieved FileLocation is used by other peers
	 * to see if their copies of the file is up to date. 
	 * @param fileName
	 * @return
	 * @throws RemoteException
	 */
	public FileLocation poll(String fileName) throws RemoteException; 
	
	
	/**
	 * Called when a peer wants to download a file from the receiving peer. The receiving peer opens a Listening socket and 
	 * returns the port where the socket is listening to the calling peer. 
	 * @param name
	 * @return 
	 * @throws RemoteException
	 * @throws IOException
	 */
	public int obtain(String name) throws RemoteException, IOException;
}
