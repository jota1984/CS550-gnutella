package japster2.peer;

import java.io.IOException;
import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Interface exposed by Peer remote object. Peers use this interface to
 * to obtain files from other peers  
 * @author jota
 *
 */
public interface FileServer extends Remote {
	/**
	 * Starts a thread serving a file 
	 * @param name Name of file to serve
	 * @return The port number where the file is being served
	 */
	public int obtain(String name) throws RemoteException, IOException;
}
