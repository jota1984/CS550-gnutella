package japster2.peer;

import java.io.Serializable;
import java.net.InetSocketAddress;
import java.util.Date;

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

	
	/**
	 * Creates a FileLocation object
	 * @param address InetSocketAddress pointing to the Peer registering the file
	 * @param name String representing the name of the file
	 * @param size long representing the size of the file on the peer
	 */
	public FileLocation(InetSocketAddress address, String name, long size, int version) {
		locationAddress = address;
		this.fileName = name;
		this.fileSize = size; 
		this.version = version;
		valid = true; 
	}
	
	public String getName() {
		return fileName; 
	}
	
	public int getVersion() {
		return version;
	}
	
	public void touch() {
		version++;
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

	@Override
	public String toString() {
		String str = fileName + "@" +
				locationAddress.getHostString() + ":" +
				locationAddress.getPort() +
				"(version " + version + ")" + 
				"(" + fileSize + "bytes)";
		if (!valid ) {
			str += "(INVALID)";
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
