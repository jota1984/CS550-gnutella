package japster2.peer;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.rmi.AccessException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Hashtable;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

/**
 * Implements the peer program for the P2P application.
 * <br>
 * The Peer class implements the Remote interface PeerNode, which can be used by 
 * other peers to call different methods on this peer. Each peer creates its own registry which is used
 * by other peers to obtain a PeerNode stub for this peer
 * <br>
 * The main method of this class creates a Peer object using the arguments read from the
 * command line and creates a PeerConsole object which is used by the user to control the Peer object 
 * through a command line interface.
 *  
 * @author jota
 *
 */
public class Peer implements PeerNode {
	
	
	//Address and port where the registry for this peer will be created
	private String localAddress;
	private int localPort; 
	
	//Directory where shared files will be read from and downloaded files will be stored on
	private String fileDirectoryName;
	
	//Used to create unique message ids for each message. works as a counter initialized at 0
	private int msgIdSeq; 
	
	//Stores the time at which the peer was started. This number is sent as part of the msgId 
	//so that if a peer is closed and then run again the other peers will not discard the messages that have the 
	//same sequence as in the first time the peer was run
	private long peerId; 
	
	//True if running in pull mode
	private boolean pullMode; 
	
	//TTR value assigned to files stored on this peer
	private int defaultTtr; 

	//Table of neighbors. For each neighbor we store a PeerNode stub that is used to communicate with it
	private Hashtable<InetSocketAddress,PeerNode> neighbors;

	//Table of previously seen messages. For each seen message the PeerNode of the neighbor that sent the message is stored.
	//This table is used to propagate hitquery messages back to the proper neighbor and to drop query messages that have been 
	//processed already
	private Hashtable<String,PeerNode> seenMessages;
	
	//List of FileLocations of files that were stored on this server before the process was run
	//These files can be updated and will always be valid on this peer so their TTR value does not get updated. 
	private ArrayList<FileLocation> localFiles;
	//List of FileLocations of files that have been downloaded by this peer. They keep the address of the peer where they 
	//were downloaded from. Their TTR is updated periodically and can be invalidated
	private ArrayList<FileLocation> remoteFiles; 
	//List of FileLocations received as search results. Can be used to initiate new downloads. 
	private ArrayList<FileLocation> searchResults;
	
	//Registry and stub created by this peer and used by other peers to contact this peer
	private Registry registry;
	private PeerNode peerStub;
	
	//Propagation delay (used for performance tests) 
	private boolean simulateDelay = false;
	private int delayValue; 
	
	//Quiet flag used for performance test
	private boolean quiet = false; 
	
	//pull mode thread for updating TTR and polling when required 
	private UpdateTtrThread updateTtrThr; 
	
	//Command line options
	private static Options options;
	
	/**
	 * Create a new Peer object 
	 * @param localAddress String representing the address where the registry will be created and the PeerNode exposed
	 * @param localPort int representing the port where the registry will be created and the PeerNode exposed
	 * @param fileDirectory This is the directory containing the files that will be shared and where new files
	 * will be created. 
	 */
	public Peer(String localAddress, 
			int localPort, 
			String fileDirectory ) { 
		
		this.localAddress = localAddress;
		this.localPort = localPort;
		this.fileDirectoryName = fileDirectory;
		
		neighbors = new Hashtable <InetSocketAddress,PeerNode>();
		seenMessages = new Hashtable <String,PeerNode>();
		searchResults = new ArrayList <FileLocation>();
		localFiles = new ArrayList<FileLocation>();
		remoteFiles = new ArrayList<FileLocation>();
		
		//work in push mode by default
		pullMode = false; 
		
		defaultTtr = Const.DEFAULT_TTR;
		
		msgIdSeq = 0; 
		
		//used to distinguish between multiple runs of the same peer, does not have to be unique among peers
		peerId = System.currentTimeMillis();
	}
	
	public ArrayList<FileLocation> getRemoteFiles() {
		return remoteFiles;
	}
	
	public synchronized void addRemoteFile(FileLocation loc) {
		remoteFiles.add(loc);
	}
	
	public ArrayList<FileLocation> getLocalFiles() {
		return localFiles;
	}
	
	public ArrayList<FileLocation> getSearchResults() {
		return searchResults;
	}
	
	public int getDefaultTtr() {
		return defaultTtr;
	}
	public void setDefaultTtr(int defaultTtr) {
		this.defaultTtr = defaultTtr;
	}
	public boolean getPullMode() {
		return pullMode; 
	}
	public void setPullMode(boolean pullMode) {
		this.pullMode = pullMode;
	}

	public Hashtable<InetSocketAddress,PeerNode> getNeighbors() {
		return neighbors;
	}
	
	public void setPropagationDelay(int value) {
		delayValue = value;
		simulateDelay = true;
	}
	
	public void setQuiet(boolean value ) {
		quiet = value;
	}
	
	public static void main(String[] args) {
		//create and parse options
		createOptions();

		CommandLine cmd;
		try {
			cmd = (new DefaultParser()).parse( options, args);
			
			if (!cmd.hasOption("L") || !cmd.hasOption("D") || 
					!cmd.hasOption("P") || cmd.hasOption("h")) {
				HelpFormatter formatter = new HelpFormatter();
				formatter.printHelp( "Peer", options );
				System.exit(0);
			}

			//Required for RMI exportObject()
			System.setProperty("java.rmi.server.hostname",cmd.getOptionValue("L"));

			//Create a new Peer object using command line arguments
			Peer peer = new Peer(cmd.getOptionValue("L"),
					Integer.parseInt(cmd.getOptionValue("P")),
					cmd.getOptionValue("D"));

			//Export PeerNode interface using RMI
			System.out.println("Exporting PeerNode interface");
			try {
				peer.exportPeerStub();
			} catch (RemoteException e) {
				System.out.println("Error exporting PeerNode interface");
				System.exit(0);
			}
			
			//Load neighbors from command line
			if( cmd.hasOption("N") ) {
				String neighbor_seed = cmd.getOptionValue("N");
				//parse neighbors separated by commas
				for(String n : neighbor_seed.split(",") ) {
					//parse host and port seprated by : 
					String[] ninfo = n.split(":");
					String host = ninfo[0];
					int port = Integer.parseInt(ninfo[1]);
					try {
						//Establish neighbor
						peer.sayHello(new InetSocketAddress(host, port));
					} catch (RemoteException | NotBoundException e) {
						System.out.println("Failed to contact neighbor " + n);
					}
				}
			}
			
			//If running in pull mode
			if( cmd.hasOption("p")) {
				peer.setPullMode(true);
				
				//Read TTR from command line 
				if( cmd.hasOption("t")) {
					peer.setDefaultTtr(Integer.parseInt(cmd.getOptionValue("t")));
				}
				
				//Start pull mode threads
				peer.initPullMode();
			}
			
			//Load files from local dir
			System.out.println("loading local files");
			peer.loadFiles();
			
			//Create a new PeerConsole attached to the Peer object
			new PeerConsole(peer);
		} catch (ParseException e) {
			System.out.println("Error parsing arguments" + e.getMessage());
			
		} 
	}
	
	
	/**
	 * Start TTR refresh thread
	 */
	public void initPullMode() {
		updateTtrThr = new UpdateTtrThread(this);
		updateTtrThr.start();
	}
	
	/**
	 * Stop TTR refresh thread
	 */
	public void endPullMode() {
		//stop TTR refresh thread
		if (updateTtrThr != null) 
			updateTtrThr.interrupt();
	}
	
	/**
	 * Create command line options
	 */
	private static void createOptions() {
		options = new Options();
		
		Option localAddress   = Option.builder("L")
				.argName( "ip_address" )
                .hasArg()
                .desc(  "use provided ip address as Local address to listen for other peer connections" )
                .longOpt("local-address")
                .build();		
		Option ttrValue   = Option.builder("t")
				.argName( "ttr" )
                .hasArg()
                .desc(  "ttr value in seconds of pull based consistency" )
                .build();
		Option pullFlag   = Option.builder("p")
				.argName( "ip_address" )
                .desc(  "Use pull approach for file consistency" )
                .build();		
		Option localPort   = Option.builder("P")
				.argName( "port" )
                .hasArg()
                .desc(  "use provided port to listen for other peer connections" )
                .longOpt("local-port")
                .build();		
		Option directory   = Option.builder("D")
				.argName( "dir-name" )
                .hasArg()
                .desc(  "use provided directory to read shared files and store downloaded files" )
                .longOpt("dir")
                .build();
		Option neighbors   = Option.builder("N")
				.argName( "neighbor-list" )
                .hasArg()
                .desc(  "provide a list of neighbors in format <ip1>:<port1>,<ip2>:<port2>..." )
                .longOpt("neighbors")
                .build();
		Option help   = Option.builder("h")
                .desc(  "print this help" )
                .longOpt("help")
                .build();	

		options.addOption(neighbors);
		options.addOption(pullFlag);
		options.addOption(localAddress);
		options.addOption(localPort);
		options.addOption(directory);
		options.addOption(help);
		options.addOption(ttrValue);
	}
	
	/**
	 * Read files from shared directory and create their local FileLocations 
	 */
	public void loadFiles() {
		
		File fileDir = new File(fileDirectoryName);
		File[] files = fileDir.listFiles();
		long fileSize; 
		String fileName; 

		// Go through each file 
		for (int i = 0; i < files.length; i++) {
			if (files[i].isFile() && !files[i].isHidden()) {
				fileSize = files[i].length();
				fileName = files[i].getName();
				//Create FileLocation for each file found
				FileLocation location = new FileLocation(
						new InetSocketAddress(localAddress, localPort), //use this peer's address and port
						fileName, 
						fileSize,
						1, //all files start with version 1 
						defaultTtr); 
				//Add FileLocation to localFiles list
				localFiles.add(location);
			} 
		}
	}
	
	/**
	 * Send a query message to all the peer's neighbors
	 * @param name String representing the name of the file to be searched
	 * @return
	 * @throws RemoteException 
	 */
	public void search(String name) throws RemoteException {
		//generate msgId 
		//localAddress and localPort ensure that message is unique among peers
		//peerId is used to distinguish between different runs of the same peer (i.e. same address and port) 
		//msgIdSeq is used to distinguish between different messages from the same peer
		String msgId = localAddress + ":" + localPort + "_" + peerId + "_" + msgIdSeq++;
		//Add message to list of seen messages in case there is a loop in the topology 
		seenMessages.put(msgId, this);
		
		//Broadcast message to all neighbors
		for ( PeerNode neighbor : neighbors.values() ) {
			neighbor.query(msgId, Const.TTL, name, localAddress, localPort);
		}
	}
	
		
	/**
	 * Used in push mode to notify all nodes that a file has been updated 
	 * @param location FileLocation of the file that was updated 
	 * @throws RemoteException
	 */
	public void sendInvalidate(FileLocation location) throws RemoteException {
		
		//Don't do anything if running in pull mode
		if( pullMode )
			return;
		
		//generate msgId 
		//localAddress and localPort ensure that message is unique among peers
		//peerId is used to distinguish between different runs of the same peer (i.e. same address and port) 
		//msgIdSeq is used to distinguish between different messages from the same peer
		String msgId = localAddress + ":" + localPort + "_" + peerId + "_" + msgIdSeq++; 
		//Add message to list of seen messages in case there is a loop in the topology 
		seenMessages.put(msgId, this);
		
		//Broadcast message to all neighbors
		for ( PeerNode neighbor : neighbors.values() ) {
			neighbor.invalidate(msgId, Const.TTL, location.getName(), location, localAddress, localPort);
		}
	}
	
	/**
	 * Establish a neighbor relationship with another peer. Obtains the peer's stub and saves it on the neighbor table
	 * and sends a hello message to the peer so that it can also establish a neighbor relationship to this peer.
	 * @param addr InetSocketAddress of the peer that we want to add as a neighbor
	 * @return
	 * @throws RemoteException
	 * @throws NotBoundException
	 */
	public boolean sayHello(InetSocketAddress addr) throws RemoteException, NotBoundException {
		String address = addr.getHostString();
		int port = addr.getPort();
		
		//Query the Peer's registry to obtain its PeerNode remote object
		Registry registry = LocateRegistry.getRegistry(address, port);
		PeerNode neighbor = (PeerNode) registry.lookup(Const.PEER_SERVICE_NAME);
		
		//Save our address
		InetSocketAddress localAddr= new InetSocketAddress(localAddress, localPort);
		//Say hello to peer inform them of our address so they can add us as a neighbor
		if (neighbor.hello( localAddr)) {
			//Add peer to our neighbor table
			neighbors.put(addr,neighbor);
			System.out.println("Added neighbor successfully");
			return true;
		}
		
		return false; 
	}
	
	/**
	 * Creates an RMI registry and binds the PeerNode remote object to it.  
	 * @throws RemoteException
	 */
	public void exportPeerStub() throws RemoteException {
 		peerStub = (PeerNode) UnicastRemoteObject.exportObject(this,0);
 		registry = LocateRegistry.getRegistry(localPort);
		registry = LocateRegistry.createRegistry(localPort);
        registry.rebind(Const.PEER_SERVICE_NAME, peerStub);
	}
	
	/**
	 * Remove stub and shutdown registry
	 * @throws AccessException
	 * @throws RemoteException
	 * @throws NotBoundException
	 */
	public void shutdownPeerStub() throws AccessException, RemoteException, NotBoundException {
		registry.unbind(Const.PEER_SERVICE_NAME);
		UnicastRemoteObject.unexportObject(this, false);
		UnicastRemoteObject.unexportObject(registry, false);
	}
	
		
	/**
	 * Reduce the TTR counter of each of the files that have been downloaded from other peers
	 */
	public synchronized void tickTtr() {
		for( FileLocation loc : remoteFiles ) {
			loc.tickTtr();
		}
	}
	
	
	/**
	 * Redownloaded all invalid files
	 */
	public void refreshFiles()  {
		
		//Go through list of remote files
		for( int i = remoteFiles.size() -1; i >= 0; i--) {//Traverse list backwards to ensure safe deletion during traversal
			FileLocation loc = remoteFiles.get(i); 
			
			//Find files that have been marked as invalid
			if (!loc.isValid()) {
				
				//delete the current copy of the file
				String fileName = fileDirectoryName + File.separator + loc.getName();
				new File(fileName).delete();
				
				//Remove the FileLocation from the list of remote files 
				remoteFiles.remove(i);
				
				try {
					//obtain a PeerNode stub of the owner of the file 
					String address = loc.getLocationAddress().getHostString();
					int port = loc.getLocationAddress().getPort();
					Registry registry = LocateRegistry.getRegistry(address, port);
					PeerNode owner = (PeerNode) registry.lookup(Const.PEER_SERVICE_NAME);	
					
					//get new FileLocation with updated version and TTR from the owner 
					FileLocation newFileLocation = owner.poll(loc.getName());
					
					//download the file using the new FileLocation 
					download( newFileLocation, quiet );
				} catch (NotBoundException | IOException e) {
					System.out.println("Failed to download new copy for " + loc.getName() );
				} 
			}
		}
	}
	
	/**
	 * Go through list of remote FileLocations and poll the owners of each file that is expired
	 */
	public void sendPolls() {
		
		//Go through FileLocations of downloaded files 
		for( FileLocation loc : remoteFiles ) {
			
			//Check if they are about to expire
			if (loc.isExpired() ) {
				
				try {
					//obtain a PeerNode stub for file's owner 
					String address = loc.getLocationAddress().getHostString();
					int port = loc.getLocationAddress().getPort();
					Registry registry = LocateRegistry.getRegistry(address, port);
					PeerNode owner = (PeerNode) registry.lookup(Const.PEER_SERVICE_NAME);	
					
					//get new FileLocation from the owner 
					FileLocation newFileLocation = owner.poll(loc.getName());
					
					//compare our version with the version of the received FileLocation from the owner
					if ( newFileLocation == null || newFileLocation.getVersion() > loc.getVersion() ) {
						loc.invalidate();//invalidate our FileLocation if the owner has a new version
					} else {
						loc.setTtr(newFileLocation.getTtr());//refresh the file's TTR if our copy is up to date
					}					
				} catch (Exception e ) {
					System.out.println("Poll failed");
				}

			}
		}
	}

	
	/**
	 * Download a file represented by a FileLocation from another peer. Starts a new thread to do the download
	 * @param location FileLocation pointing to the registry of a Peer that has the file available
	 * @param quiet DownloaderThread wont print progress if true
	 * @throws NotBoundException
	 * @throws IOException 
	 */
	public Thread download(FileLocation location, boolean quiet) throws NotBoundException, IOException {
		String address = location.getLocationAddress().getHostString();
		int port = location.getLocationAddress().getPort();
		String fileName = location.getName();
		
		//check if file already exists
		String newfileName = fileDirectoryName + File.separator + fileName;
		if( new File(newfileName).exists() )
			throw new IOException("File exists");
		
		
		//Query the Peer's registry to obtain its PeerNode stub
		Registry registry = LocateRegistry.getRegistry(address, port);
		PeerNode server = (PeerNode) registry.lookup(Const.PEER_SERVICE_NAME);
		
		//Call the obtain method on the peer to get the TCP port where it will 
		//serve the requested file. 
		int downloadPort = server.obtain(fileName);
		
		//Start a downloader thread to download the file 
		FileDownloaderThread fileDownloader = 
				new FileDownloaderThread(this, newfileName, downloadPort, location, quiet);
		fileDownloader.start();
		
		return fileDownloader;
				
	}
	
	/*
	 * Implementation of PeerNode Interface 
	 */
	@Override
	public int obtain(String name) throws RemoteException, IOException  {
		
		//generate full name of file to be served
		String fileName = fileDirectoryName + File.separator + name;
		
		FileServerThread serverThread = null; 
		int port = 0;
		
		//Create  a FileServerThread to serve the file
		serverThread = new FileServerThread(fileName);
		port = serverThread.getPort();
		
		serverThread.start();

		//return the port to the caller so they can connect to the socket serving the file 
		return port;
	}
	
	/*
	 * Implementation of PeerNode Interface 
	 */
	@Override
	public boolean hello(InetSocketAddress peerAddress)  {
		
		String address = peerAddress.getHostString();
		int port = peerAddress.getPort();
		
		//Query the Peer's registry to obtain its FileServer remote object
		Registry registry;
		try {
			registry = LocateRegistry.getRegistry(address, port);
			PeerNode neighbor = (PeerNode) registry.lookup(Const.PEER_SERVICE_NAME);
			
			neighbors.put(peerAddress,neighbor);
		} catch (RemoteException | NotBoundException e) {
			return false;
		}
		System.out.println("Added neighbor successfully");

		return true;
	}

	/*
	 * Implementation of PeerNode Interface 
	 */
	@Override
	public void query(String msgId, long ttl, String fileName, String host, int port) throws RemoteException {
		
		//Delay simulation for performance tests
		if(simulateDelay) {
			try {
				Thread.sleep(delayValue);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		//decrease TTL 
		final long newttl = ttl-1;
		
		//Create a new thread to broadcast the message, this prevents the caller from being blocked while
		//the message propagates through the whole network of peers
		new Thread() {
			public void run() { 

				//Ignore duplicates 
				if (seenMessages.containsKey(msgId)) {
					System.out.println("Duped message: do nothing");
					return;
				} 
					
				//Get PeerNode object of sender
				PeerNode sender = null;
				sender = neighbors.get(new InetSocketAddress(host, port));

				//record message as seen
				seenMessages.put(msgId, sender);
				
				//If TTL hasnt expired broadcast message to neighbors
				if (newttl > 0) {
					// go through each neighbor 
					for ( PeerNode neighbor : neighbors.values()) {
						try { 
							//Dont send query back to sender
							if ( sender.equals(neighbor) )
								continue;
							neighbor.query(msgId, ttl, fileName, localAddress, localPort);
						} catch(RemoteException e) {
							System.out.println("Failed to contact neighbor");
						}
					}						
				}
				
				//Now handle the query at this peer
				
				//build full filename
				File file = new File(fileDirectoryName +  File.separator + fileName );
				if( file.exists() ) {
					//Find FileLocation on local file table
					FileLocation fileLocation = null;
					for ( FileLocation loc : localFiles ) {
						if (loc.getName().equals(fileName) ) {
							fileLocation = loc; 
						}
					}

					//Find FileLocation on remote file table if not found on local table
					if ( fileLocation == null ) {
						for( FileLocation loc : remoteFiles ) {
							if (loc.getName().equals(fileName) ) {
								//check if this location is invalid or expired 
								if (loc.isValid() && !loc.isExpired() ) {
									//Create new file location (We cannot use location stored on the remote table since that 
									//one points to the original copy, we want to answer the query with a FileLocation that 
									//points to us)
									fileLocation = new FileLocation(new InetSocketAddress(localAddress,localPort),
											fileName,
											file.length(), 
											loc.getVersion(),
											getDefaultTtr()
											);								
								} else { //if file invalid or expired do nothing
									return;
								}
	
							}
						}
					}

					try {
						//send hitquery with result
						sender.hitquery(msgId, Const.TTL, fileName, fileLocation);
					} catch (RemoteException e) {
						System.out.println("failed send back reponse");
					}
				} 
			}
		}.start();
	}

	/*
	 * Implementation of PeerNode Interface 
	 */
	@Override
	public void hitquery(String msgId, long ttl, String fileName, FileLocation fileLocation) throws RemoteException{
		
		//Delay simulation for performance tests
		if(simulateDelay) {
			try {
				Thread.sleep(delayValue);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		
		//Decrease TTL
		long newttl = ttl - 1;
		PeerNode localPeer = this; 
		//Process message on different thread to prevent caller from blocking while the message propagates
		new Thread() {
			public void run() {

				//drop message if it belongs to unknown query 
				if(!seenMessages.containsKey(msgId)) {
					System.out.println("Unknown query");
					return;
				} 
				
				//get path back to origin 
				PeerNode upstream = seenMessages.get(msgId);
				
				//Check if query was initiated by us
				if(upstream == localPeer) { 
					//Notify file was found and add result to result list
					if ( !quiet) {
						System.out.println("File found, Type \"results\" to view result");						
					}
					searchResults.add(fileLocation);
					return;
				//if message is not for us and the TTL hasnt expired propagate to origin  
				} else if (newttl > 0){
					try {
						upstream.hitquery(msgId, newttl, fileName, fileLocation);
					} catch (RemoteException e) {
						System.out.println("Failed to send back hitquery");
					}
				}
			}
		}.start();		
	}

	/*
	 * Implementation of PeerNode Interface 
	 */
	@Override
	public void invalidate(String msgId, long ttl, String fileName, FileLocation fileLocation, String host, int port)
			throws RemoteException {
		
		//Delay simulation for performance tests
		if(simulateDelay) {
			try {
				Thread.sleep(delayValue);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		
		//Ignore messge if running in pull mode
		if(pullMode) {
			return; 
		}
		
		//decrease TTL
		long newttl = ttl - 1;
		
		//process message if different thread to avoid caller from blocking while message propagates
		new Thread() { 
			public void run() { 
				
				//Drop message if it is a duplicate
				if (seenMessages.containsKey(msgId)) {
					System.out.println("Duped message: do nothing");
					return;
				} 

				//Get PeerNode object of sender
				PeerNode sender = null;
				sender = neighbors.get(new InetSocketAddress(host, port));

				//record message as seen
				seenMessages.put(msgId, sender);

				//broadcast message if not expired 
				if (newttl > 0) {
					//go through neighbors
					for ( PeerNode neighbor : neighbors.values()) {
						try { 
							//Dont send query back to sender
							if ( sender.equals(neighbor) )
								continue;
							neighbor.invalidate(msgId, ttl, fileName, fileLocation, localAddress, localPort);
						} catch(RemoteException e) {
							System.out.println("Failed to contact neighbor");
						}
					}						
				}
				
				//Now process the invalidate message

				//Find out if we have downloaded that file and mark as invalid if found
				for( FileLocation loc : remoteFiles ) {
					if (loc.getName().equals(fileName) ) {
						if( !quiet ) {
							System.out.println("Received invalidate message for " +
									fileName + "; New version is " + fileLocation.getVersion() );							
						}
						loc.invalidate();
					}
				}
			}
		}.start();
	}
	
	/*
	 * Implementation of PeerNode Interface 
	 */
	@Override
	public FileLocation poll(String fileName) throws RemoteException {
		
		//Delay simulation for performance tests
		if(simulateDelay) {
			try {
				Thread.sleep(delayValue);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		
		//Find file on local file table 
		FileLocation fileLocation = null;
		for ( FileLocation loc : localFiles ) {
			if (loc.getName().equals(fileName) ) {
				fileLocation = loc;
			}
		}
		
		//Find FileLocation on remote file table if not found on local
		if ( fileLocation == null ) {
			for( FileLocation loc : remoteFiles ) {
				if (loc.getName().equals(fileName) ) {
					fileLocation = new FileLocation(new InetSocketAddress(localAddress,localPort),
							fileName,
							loc.getSize(), 
							loc.getVersion(),
							getDefaultTtr()
							);
				}
			}
		}
		
		//Return fileLocation to caller so it can compare it with its own FileLocation instance 
		return fileLocation;
	}
}
