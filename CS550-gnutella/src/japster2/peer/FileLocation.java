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
	private Date refreshDate;
	private String fileName;
	private long fileSize; 

	
	/**
	 * Creates a FileLocation object
	 * @param address InetSocketAddress pointing to the Peer registering the file
	 * @param name String representing the name of the file
	 * @param size long representing the size of the file on the peer
	 */
	public FileLocation(InetSocketAddress address, String name, long size) {
		locationAddress = address;
		this.fileName = name;
		this.fileSize = size; 
		refresh();  
	}
	
	public void refresh() {
		refreshDate = new Date(); 
	}
	
	public String getName() {
		return fileName; 
	}
	
	public Date getRefreshDate() { 
		return refreshDate; 
	}
	
	public long getSize() {
		return fileSize;
	}
	
	public InetSocketAddress getLocationAddress() {
		return locationAddress;
	}

	@Override
	public String toString() {
		return "" + fileName + locationAddress.toString() + 
				"(" + refreshDate + ")" +
				"(" + fileSize + "bytes)";
	}
	
	@Override
	public boolean equals(Object arg0) {
		if (!(arg0 instanceof FileLocation))
			return false;
		return locationAddress.equals(((FileLocation)arg0).getLocationAddress()) && fileName.equals(((FileLocation) arg0).getName());
	}
	
}
