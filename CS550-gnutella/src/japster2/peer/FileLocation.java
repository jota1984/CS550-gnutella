package japster2.peer;

import java.io.Serializable;
import java.net.InetSocketAddress;

/**
 * A FileLocation stores a InetSocketAddress object with the host and port
 * of a peer that is serving a file. It keeps track of when the FileLocation was 
 * created on a Date object. This date can be updated by calling the refresh method.
 * 
 * FileLocation also keeps track of the size of the file on a given location
 * @author jota
 *
 */
public class FileLocation implements Serializable{
	
	private static final long serialVersionUID = 1L;
	
	private InetSocketAddress locationAddress;
	private String fileName;
	private long fileSize; 
	private int version; 
	private boolean valid;
	private int ttr;
	private boolean expired;
	
	private long timeStamp; 
	
	public int getTtr() {
		return ttr;
	}
	
	

	public void setTtr(int ttr) {
		this.ttr = ttr;
		expired = false;
	}

	public void tickTtr() {
		if( expired )
			return;
		ttr -= Const.EXPIRATION_WATCH_PERIOD/1000;
		
		if( ttr <= 0 ) {
			expired = true;
			return;
		}
		expired = false;
	}
	
	public boolean isExpired() {
		return expired; 
	}

	/**
	 * Creates a FileLocation object
	 * @param address InetSocketAddress pointing to the Peer registering the file
	 * @param name String representing the name of the file
	 * @param size long representing the size of the file on the peer
	 */
	public FileLocation(InetSocketAddress address, String name, long size, int version, int ttr ) {
		locationAddress = address;
		this.fileName = name;
		this.fileSize = size; 
		this.version = version;
		this.ttr = ttr;
		expired = false; 
		valid = true; 
		
		updateTimeStamp();
	}
	
	public String getName() {
		return fileName; 
	}
	
	public int getVersion() {
		return version;
	}
	
	public void touch() {
		version++;
		updateTimeStamp();
	}
	
	public void invalidate() { 
		valid = false; 
	}
	
	public boolean isValid() {
		return valid;
	}
	
	public long getSize() {
		return fileSize;
	}
	
	public InetSocketAddress getLocationAddress() {
		return locationAddress;
	}
	
	public long getTimeStamp() { 
		return timeStamp;
	}
	
	public void updateTimeStamp() {
		timeStamp = System.currentTimeMillis();
	}

	@Override
	public String toString() {
		String str = fileName + "@" +
				locationAddress.getHostString() + ":" +
				locationAddress.getPort() +
				"(version " + version + ")" + 
				"(" + fileSize + "bytes)";
		str += "(TTR " + ttr + ")";
		if (!valid ) {
			str += "(INVALID)";
		} else if ( isExpired() ) {
			str += "(TTR EXPIRED)";
		}
		return str; 
	}
	
	@Override
	public boolean equals(Object arg0) {
		if (!(arg0 instanceof FileLocation))
			return false;
		return locationAddress.equals(((FileLocation)arg0).getLocationAddress()) && fileName.equals(((FileLocation) arg0).getName());
	}
	
}
