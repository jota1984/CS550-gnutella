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
 * The Peer class implements the Remote interface FileServer, which can be used by 
 * other peers to call the obtain method on this peer. The obtain method is used
 * to download files from this peer.
 * <br>
 * The main method of this class creates a Peer object using the arguments read from the
 * command line and creates a Peer object and a PeerConsole object which is used 
 * by the user to control the Peer object through a command line interface.
 * <br>
 * The Peer object can do the following 
 * <br>
 * 	- Create a remote RMI object exposing the FileServer interface which can be used to request files to the Peer
 * <br>
 *  - Register all the files of the directory associated with the Peer on the IndexServer
 * <br>
 *  - Search for a file on the IndexServer and get a FileLocator for that file if found
 * <br>
 *  - Send a request to another Peer's FileServer to download a file
 *  
 * @author jota
 *
 */
public class Peer implements FileServer, PeerNode {
	
	private String localAddress;
	private int localPort; 
	private String fileDirectoryName;
	private int msgIdSeq; 
	
	
	private Hashtable<InetSocketAddress,PeerNode> neighbors;
	public Hashtable<InetSocketAddress,PeerNode> getNeighbors() {
		return neighbors;
	}

	private Hashtable<String,PeerNode> seenMessages;
	
	private ArrayList<FileLocation> localFiles; 
	
	public ArrayList<FileLocation> getLocalFiles() {
		return localFiles;
	}

	private ArrayList<FileLocation> fileLocations;
	
	public ArrayList<FileLocation> getFileLocations() {
		return fileLocations;
	}

	private static Options options;
	
	/**
	 * Create a new Peer object 
	 * @param indexAddress String representing the address of the IndexServer
	 * @param localAddress String representing the address used to expose the FileServer remote object by creating 
	 * a registry on this address and binding the remote object to it. This is the address that will be advertised to the IndexServer
	 * @param localPort int representing the port used to create the registry where the FileServer remote object
	 * will be bound. This is the port that will be advertised to the IndexServer.
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
		fileLocations = new ArrayList <FileLocation>();
		
		localFiles = new ArrayList<FileLocation>();
		msgIdSeq = 0; 
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



			System.out.println("Exporting FileServer interface");
			try {
				peer.exportFileServer();
			} catch (RemoteException e) {
				System.out.println("Error exporting FileServer interface");
				System.exit(0);
			}
			
			//Load neighbors from command line
			if( cmd.hasOption("N") ) {
				String neighbor_seed = cmd.getOptionValue("N");
				for(String n : neighbor_seed.split(",") ) {
					String[] ninfo = n.split(":");
					String host = ninfo[0];
					int port = Integer.parseInt(ninfo[1]);
					try {
						peer.sayHello(new InetSocketAddress(host, port));
					} catch (RemoteException | NotBoundException e) {
						System.out.println("Failed to contact neighbor " + n);
					}
				}
			}
			
			//Load files from local dir
			System.out.println("loading local files");
			peer.loadFiles();
			
			
			//System.out.println("Starting DirWatcherThread");
			//new DirWatcherThread(peer).start();

			//Create a new PeerConsole attached to the Peer object
			new PeerConsole(peer);
		} catch (ParseException e) {
			System.out.println("Error parsing arguments");
		} 
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
		options.addOption(localAddress);
		options.addOption(localPort);
		options.addOption(directory);
		options.addOption(help);
	}
	
	public void loadFiles() {
		
		File fileDir = new File(fileDirectoryName);
		File[] files = fileDir.listFiles();
		long fileSize; 
		String fileName; 

		for (int i = 0; i < files.length; i++) {
			if (files[i].isFile() && !files[i].isHidden()) {
				fileSize = files[i].length();
				fileName = files[i].getName();
				FileLocation location = new FileLocation(new InetSocketAddress(localAddress, localPort), fileName, fileSize);
				localFiles.add(location);
			} 
		}
	}
	
	/**
	 * Uses the Index stub to execute the search method on the IdexServer remote
	 * object.
	 * @param name String representing the name of the file to be searched
	 * @return
	 * @throws RemoteException 
	 */
	public void search(String name) throws RemoteException {
		String msgId = localAddress + ":" + localPort + "_" + msgIdSeq++; 
		seenMessages.put(msgId, this);
		for ( PeerNode neighbor : neighbors.values() ) {
			neighbor.query(msgId, Const.TTL, name, localAddress, localPort);
		}
	}
	
	public boolean sayHello(InetSocketAddress addr) throws RemoteException, NotBoundException {
		String address = addr.getHostString();
		int port = addr.getPort();
		
		//Query the Peer's registry to obtain its PeerNode remote object
		Registry registry = LocateRegistry.getRegistry(address, port);
		PeerNode neighbor = (PeerNode) registry.lookup(Const.PEER_SERVICE_NAME);
		
		InetSocketAddress localAddr= new InetSocketAddress(localAddress, localPort);
		if (neighbor.hello( localAddr)) {
			neighbors.put(addr,neighbor);
			System.out.println("Added neighbor successfully");
			return true;
		}
		
		return false; 
	}
	
	private Registry registry;
	private FileServer serverStub;
	
	/**
	 * Creates an RMI registry and binds the FileServer remote object to it.  
	 * @throws RemoteException
	 */
	public void exportFileServer() throws RemoteException {
 		serverStub = (FileServer) UnicastRemoteObject.exportObject(this,0);
 		registry = LocateRegistry.getRegistry(localPort);
		registry = LocateRegistry.createRegistry(localPort);
        registry.rebind(Const.PEER_SERVICE_NAME, serverStub);
	}
	
	public void shutdownFileServer() throws AccessException, RemoteException, NotBoundException {
		registry.unbind(Const.PEER_SERVICE_NAME);
		UnicastRemoteObject.unexportObject(this, false);
		UnicastRemoteObject.unexportObject(registry, false);
	}

	
	/**
	 * Download a file from another peer represented by a FileLocation
	 * @param fileName String representing the name of the file
	 * @param location FileLocation pointing to the registry of a Peer that has the file available
	 * @param quiet DownloaderThread wont print progress if true
	 * @throws NotBoundException
	 * @throws IOException 
	 */
	public Thread download(FileLocation location, boolean quiet) throws NotBoundException, IOException {
		String address = location.getLocationAddress().getHostString();
		int port = location.getLocationAddress().getPort();
		String fileName = location.getName();
		long size = location.getSize();
		
		//check if file already exists
		String newfileName = fileDirectoryName + File.separator + fileName;
		if( new File(newfileName).exists() )
			throw new IOException("File exists");
		
		
		//Query the Peer's registry to obtain its FileServer remote object
		Registry registry = LocateRegistry.getRegistry(address, port);
		FileServer server = (FileServer) registry.lookup(Const.PEER_SERVICE_NAME);
		
		//Call the obtain method on the peer to get the TCP port where it will 
		//server the requested file. 
		int downloadPort = server.obtain(fileName);
		
		//Start a downloader thread to download the file 
		FileDownloaderThread fileDownloader = 
				new FileDownloaderThread(newfileName, size, address, downloadPort, quiet);
		fileDownloader.start();
		
		return fileDownloader;
				
	}
	
	@Override
	public int obtain(String name) throws RemoteException, IOException  {
		String fileName = fileDirectoryName + File.separator + name;
		FileServerThread serverThread = null; 
		int port = 0;
//		try {
			//Create  a FileServerThread to serve the file
			serverThread = new FileServerThread(fileName);
			port = serverThread.getPort();
			serverThread.start();
//		} catch (IOException e) {
//			System.out.println("File Not found: " + fileName);
//			throw new 
//		} //catch (IOException e) {
//			System.out.println("Could not create server thread");
//		}
		return port;
	}

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

	@Override
	public void query(String msgId, long ttl, String fileName, String host, int port) throws RemoteException {

		final long newttl = ttl-1;
		new Thread() {
			public void run() { 

				System.out.println("Query received " + msgId + ":"+ ttl +":" + fileName + " from " + host + ":" + port);
				if (seenMessages.containsKey(msgId)) {
					System.out.println("Duped message: do nothing");
					return;
				} else {
					
					//Get PeerNode object of sender
					PeerNode sender = null;
					Registry senderReg; 
					try {
						senderReg = LocateRegistry.getRegistry(host, port);
						sender = (PeerNode) senderReg.lookup(Const.PEER_SERVICE_NAME);
					} catch (RemoteException | NotBoundException e) {
						System.out.println("Cant contact sender");
						return;
					}
					
					seenMessages.put(msgId, sender);
					if (newttl > 0) {
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

					
					File file = new File(fileDirectoryName +  File.separator + fileName );
					System.out.println(file);
					if( file.exists() ) {
						System.out.println("File found");
						FileLocation fileLocation = new FileLocation(new InetSocketAddress(localAddress,localPort),
								fileName,
								file.length()
								);
								
						try {
							sender.hitquery(msgId, Const.TTL, fileName, fileLocation);
						} catch (RemoteException e) {
							System.out.println("failed send back reponse");
						}
					} else {
						System.out.println("File not found");
					}
					
				}
				
			}
		}.start();
	}

	@Override
	public void hitquery(String msgId, long ttl, String fileName, FileLocation fileLocation) throws RemoteException{
		long newttl = ttl - 1;
		PeerNode localPeer = this; 
		new Thread() {
			public void run() {
				String host = fileLocation.getLocationAddress().getHostString();
				int port = fileLocation.getLocationAddress().getPort();
				System.out.println("Hitquery received " + msgId + ":"+ ttl +":" + fileName + " from " + host + ":" + port);
				if(!seenMessages.containsKey(msgId)) {
					System.out.println("Unknown query");
					return;
				} else { 
					PeerNode upstream = seenMessages.get(msgId);
					if(upstream == localPeer) { //If query was initiated by this peer
						System.out.println("File found, Type \"results\" to view result");
						if( !fileLocations.contains(fileLocation) )
							fileLocations.add(fileLocation);
						return;
					} else if (newttl > 0){
						System.out.println("Forwarding hitquery upstream");
						try {
							upstream.hitquery(msgId, newttl, fileName, fileLocation);
						} catch (RemoteException e) {
							System.out.println("Failed to send back hitquery");
						}
					}
				}
			}
		}.start();		
	}
}
