package japster2.peer;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;

/**
 * A FileLocator keeps track of the number of different peers that have registered a file.
 * 
 * The name of the file is kept as a String and the peer information is kept on 
 * an ArrayList of FileLocations. 
 * @author jota
 *
 */
public class FileLocator implements Serializable{
	

	private static final long serialVersionUID = 1L;
	
	private String fileName;
	private ArrayList<FileLocation> locationList;
	
	public FileLocator(String name) {
		this.fileName = name;
		this.locationList = new ArrayList<FileLocation>();
	}
	
	public String getFileName() {
		return fileName;
	}

	public ArrayList<FileLocation> getLocationList() {
		return locationList;
	}

	/**
	 * Adds a new FileLocation to this FileLocator. If the FileLocation is already
	 * present in this FileLocator then it only calls refresh on the FileLocation to
	 * update its timestamp
	 * @param location
	 */
	public synchronized void addLocation(FileLocation location) {
		int idx = locationList.indexOf(location);
		if (idx == -1) 
			locationList.add(location);
		else {
			locationList.get(idx).refresh();
		}
	}
	
	/**
	 * Removes a FileLocation from the FileLocator
	 * @param location
	 * @return
	 */
	public synchronized boolean removeLocation(FileLocation location) {
		return locationList.remove(location);
	}
	
	/**
	 * Get the number of FileLocations currently stored on this FileLocator
	 * @return
	 */
	public synchronized int getLocationCount() {
		return locationList.size();
	}
	
	/**
	 * Remove FileLocations older than INDEX_TIMEOUT milliseconds
	 */
	public synchronized void purge() { 
		Date oldest = new Date(System.currentTimeMillis() - Const.INDEX_TIMEOUT);
		ArrayList<FileLocation> purgeList = new ArrayList<FileLocation>();
		for(FileLocation location : locationList ) {
			Date date = location.getRefreshDate();
			if (date.before(oldest)) {
				purgeList.add(location);
			}
		}
		for(FileLocation location : purgeList ) {
			System.out.println("Removing location from FileLocator (" +
					location.toString() + ")");
			removeLocation(location);
		}
	}

	@Override
	public String toString() {
		String locationStr = "";
		for (FileLocation loc : locationList) {
			locationStr += loc.toString() + "|";
		}
		return fileName + "->" + locationStr ;
	}
}
