package japster2.peer;

import java.io.Serializable;
import java.net.InetSocketAddress;

/**
 * A FileLocation represents a file which is part of the P2P system. It stores an InetSocketAddress with the information
 * of the peer that is serving the file. A FileLocation keeps track of file version as well as the consistency of the file 
 * (i.e. expired, valid, etc) 
 * 
 * Each peer keeps three lists of FileLocation objects: 
 * - Local FileLocations: for each local file (i.e. Files that were stored on the peer before the process was started). These FileLocations
 * are sent when a query for one of these local files is received. The address stored on these FileLocations is always the local address
 * of the peer
 * - Remote FileLocations: for each remote file it has downloaded (i.e. Files that have been downloaded from other peers).  These FileLocations 
 * are received by peers when they download a file from another peer. They point to the peer that provided the file. 
 * - Result FileLocatons: for each result that the peer has received in response of a query. 
 * @author jota
 *
 */
public class FileLocation implements Serializable {
	
	private static final long serialVersionUID = 1L;
	
	//Address of a peer where the file can be located, specifically contains the address and port of the registry 
	//of the peer that is serving the file
	private InetSocketAddress locationAddress;
	
	//File Attributes 
	private String fileName;
	private long fileSize; 
	private int version; 
	
	//Consistency attributes
	private boolean valid;
	private int ttr;
	private boolean expired;
	
	//Timestamp used for performance tests
	private long timeStamp; 
	
	/**
	 * Creates a FileLocation object
	 * @param address InetSocketAddress pointing to the Peer registering the file
	 * @param name String representing the name of the file
	 * @param size long representing the size of the file on the peer
	 * @param version int representing the verion of the file
	 * @param ttr int representing the TTR of the file 
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
	
	public int getTtr() {
		return ttr;
	}

	public void setTtr(int ttr) {
		this.ttr = ttr;
		expired = false;
	}

	/**
	 * Decreases the value of TTR. It should be called periodically every UPDATE_TTR_PERIOD seconds
	 * 
	 *  This should never be called for Local FileLocations since a file is considered to always
	 *  be up to date at its origin peer 
	 */
	public void tickTtr() {
		//No need to update TTR if already expired
		if( expired )
			return;
		
		//Decrease TTR 
		ttr -= Const.UPDATE_TTR_PERIOD/1000;
		
		//Check if we are expired 
		if( ttr <= 0 ) {
			expired = true;
		}
	}
	
	public boolean isExpired() {
		return expired; 
	}
	
	public String getName() {
		return fileName; 
	}
	
	public int getVersion() {
		return version;
	}
	
	
	/**
	 * Increase the version of the file associated with this FileLocation. 
	 * 
	 * This method should only be called for Local FileLocations, since files can only be updated by 
	 * their origin peers 
	 */
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
