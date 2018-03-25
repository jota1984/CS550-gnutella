package japster2.tools;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.rmi.AccessException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Hashtable;
import java.util.Random;

import japster2.peer.FileLocation;
import japster2.peer.Peer;

/**
 * Creates an instance of a Peer object and provides the ability to execute
 * performance tests on it.
 * 
 * This class provides methods to create and destroy a testing environment for the 
 * enclosed peer. 
 * 
 * @author jota
 *
 */
public class PeerTester {
		
	//peer parameters
	private String peerDir; 
	private String peerAddress;
	private int peerPort;
	
	//testing environment 
	private int numberOfFiles;
	private String peerName; 
	private ArrayList<String> fileNames = null; 	
	
	// peer object 
	private Peer peer;

	//pull mode flag
	private boolean pull; 
	
	
	/**
	 * Create a new PeerTester. A name must be provided which is used to create the 
	 * testing environment for the peer. The normal parameters that are usually
	 * required to create a peer must also be provided.
	 * @param name
	 * @param fileNumber number of files to create for the peer
	 * @param peerAddress local address of the peer
	 * @param peerPort local port of the peer 
	 * @param pull run in pull mode 
	 * @throws RemoteException
	 * @throws NotBoundException
	 */
	public PeerTester(String name, 
			int fileNumber, 
			String peerAddress, 
			int peerPort, 
			boolean pull) throws RemoteException, NotBoundException {
		
		numberOfFiles = fileNumber;
		peerName = name; 
		
		this.peerAddress = peerAddress;
		this.peerPort = peerPort; 
		
		//Create share directory for the testing environment of this peer
		peerDir = "/tmp" + File.separator + peerName;
		new File(peerDir).mkdir();
		
		//create the peer
		peer = new Peer(peerAddress,peerPort,peerDir);
		//Dont print all the peer's output 
		peer.setQuiet(true);

		//enable pull mode
		this.pull = pull; 
		if ( pull ) {
			peer.setPullMode(true);
			peer.initPullMode();
		}
		
	}
	
	/*
	 * getters 
	 */
	public Peer getPeer() {
		return peer; 
	}
	
	public String getAddress() {
		return peerAddress; 
	}
	
	public int getPort() {
		return peerPort; 
	}
	
	public String getName() {
		return peerName;
	}
	
	/**
	 * Returns an array of the names of the files created by this PeerTester
	 * @return
	 */
	public ArrayList<String> getFileNames() { 
		
		if (fileNames != null )
			return fileNames;

		//Create the array if has not been created previously
		fileNames = new ArrayList<String>(numberOfFiles);
		for (int i = 0; i < numberOfFiles; i++) {
			fileNames.add( peerName + "_file" + i );  
		}
		
		return fileNames; 
	}
	
	/**
	 * Create test files for the peer. 
	 * @param size The size in bytes of each test file.
	 */
	public void createFiles(long size) { 
		
		//If we have not created the names of the files yet, create them
		if (fileNames == null)
			getFileNames();
		
		//Go the list of names and create each
		for (int i = 0; i < numberOfFiles; i++) {
			File f = new File(peerDir + File.separator + fileNames.get(i));
			if (!f.exists() ) {
				 RandomAccessFile newfile = null;
				try {
					//create the file
					newfile = new RandomAccessFile(f, "rw");
					newfile.setLength(size);
					newfile.close(); 
				} catch (IOException e) {
					System.out.println("Failed to create file");
				} 
			}
		}
		//load the files into the peer's localFiles table 
		peer.loadFiles();
	}
	
	/**
	 * Deletes the test files created by createFiles()
	 */
	private void deleteFiles() {
		File dir = new File(peerDir);
		for( File f : dir.listFiles()) {
			f.delete();
		}
	}
	
	/**
	 * Deletes the directory created for this PeerTester
	 */
	private void deleteDir() {
		File f = new File(peerDir);
		if (f.exists())
			f.delete();
	}
	
	
	/**
	 * Performs cleanup after finishing tests.
	 * 
	 */
	public void cleanup() {
		deleteFiles();
		deleteDir(); 
		peer.endPullMode();
		try {
			peer.shutdownPeerStub();
		} catch (RemoteException | NotBoundException e) {
			System.out.println( "Can't shut down peer");
		}
	}
	
	/**
	 * Establish a neighbor relationship 
	 * @param address
	 */
	public void establishNeighbor( InetSocketAddress address) {
		try {
			peer.sayHello(address);
		} catch (RemoteException | NotBoundException e) {
			System.out.println("Cant contact peer");
		}
	}
	
	/**
	 * Runs a number of queries sequentially. The names for the queries are taken randomly from the fileNames
	 * parameter
	 * @param queryNumber
	 * @param fileNames
	 * @param period
	 * @return
	 * @throws RemoteException
	 */
	public int testInvalidQueries( int queryNumber, ArrayList<String> fileNames, long period ) throws RemoteException { 
		
		//Use the same seed to generate queries to get consistent results across different runs 
		Random rand = new Random(1);
		
		//run the queries
		for(int i = 0; i < queryNumber; i++) {
			//pick a random name from the fileNames list
			int idx = rand.nextInt(fileNames.size());
			String name = fileNames.get(idx);
			
			//caclulate next query time 
			long currentTime = System.currentTimeMillis();
			long nextTime = currentTime + period; 
			
			//do the query 
			System.out.println("Searching for " + name);
			peer.search(name);
			
			//calculate time to wait before next query
			currentTime = System.currentTimeMillis();
			long waitTime = nextTime - currentTime; 
			
			try {
				if (waitTime > 0 )
					Thread.sleep(waitTime);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		
		//wait some time for all queries to finish 
		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		//Compute results
		System.out.println("*****Computing results");
		ArrayList<FileLocation> results = peer.getSearchResults();
		//sort results by timestamp 
		results.sort( new Comparator<FileLocation>() {
			@Override
			public int compare(FileLocation arg0, FileLocation arg1) {
				if ( arg0.getTimeStamp() > arg1.getTimeStamp() ) 
					return 1;
				else if ( arg0.getTimeStamp() == arg1.getTimeStamp() )
					return 0;
				else
					return -1;
			}
		});
		
		//Traverse results to find inconsistent results
		//Because results will be ordered by timestamp and because the origin node of a file
		//is the only one that can increase its version we dont need to distinguish between results from the origin node or
		//copies. If the version of a file increases the increase must have come from the origin node, all subsequent results
		//that have a lower version must be results from inconsisten copies
		int invalidCount = 0; 
		int highestVersion = 0; 
		Hashtable <String, Integer> highestVersions = new Hashtable <String,Integer>();
		//Go through results
		for ( FileLocation result : results ) {
			String fileName = result.getName();
			int version = result.getVersion();
			
			//If this is the first result we see for this file store its version as the highest and continue
			if (!highestVersions.containsKey(fileName)) {
				highestVersions.put(fileName, version);
				System.out.println( result );
				continue;
			} 
			
			//get the highest version we've seen for this file
			highestVersion = highestVersions.get(fileName);
			
			//compare current version with highest version, if lower then count as invalid 
			if (version < highestVersion ) {
				invalidCount++; 
				System.out.println( result  + " <- Inconsistent result! current version is " + highestVersion);
			} else 
				System.out.println( result );
			
			//update the highest version we've seen for this file 
			highestVersion = Math.max(version, highestVersion); 
			highestVersions.replace(fileName, highestVersion);
		}
		return invalidCount; 
	}
	
	/**
	 * Update all of this peer's localFiles
	 * @throws RemoteException
	 */
	public void updateFiles() throws RemoteException {
		ArrayList<FileLocation> files = peer.getLocalFiles();
		for ( FileLocation file : files ) {
			file.touch(); 
			if( !pull ) {
				peer.sendInvalidate(file);
			}
		}
	}
	
	public void exportPeerStub() throws RemoteException {
		peer.exportPeerStub();
	}
	
	public void shutdownPeerStub() throws AccessException, RemoteException, NotBoundException {
		peer.shutdownPeerStub();
	}
}
