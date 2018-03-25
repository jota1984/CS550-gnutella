package japster2.tools;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.util.ArrayList;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import japster2.peer.FileLocation;

/**
 * Simulates a linear network topology, where a number of peers perform updates and refreshes on their files and
 * another peer performs queries operations. 
 * 
 * The number of inconsistent results (i.e. Files that a have a lower version than their origin copy but are not marked invalid) 
 * is measured.  
 * 
 * @author jota
 *
 */
public class InvalidQueryTest {
	
	public static final int FILE_SIZE = 1024; 
	public static final int FIRST_PORT = 9900; 
	
	public static final int QUERY_PERIOD = 200; 
	public static final int DOWNLOAD_TIME = 2000;
	
	public static final int PROPAGATION_DELAY = 50; 
	
	public static final int NUMBER_OF_QUERIES = 200; 
	
	
	//List of peers used to simulate updates and refresh operations
	private PeerTester[] peerTesters;
	
	//peer used to generate search queries
	private PeerTester peerQuerier; 
	
	//list of names and FileLocations for all files created in test network
	private ArrayList<String> fileNames; 
	private ArrayList<FileLocation> fileLocations; 
	
	//value used to simulate propagation delay between each node
	private int propagationDelay;
	
	//Command line options
	private static Options options;
	
	
	public InvalidQueryTest(int delay) {
		propagationDelay = delay; 
	}
	
	/**
	 * Creates the following topology 
	 * 
	 * peer_0 <-> peer_1 <-> peer_2 <-> ... <-> peer_n-1 <-> peer_n 
	 * 
	 * Where peers peer_0 through peer_n-1 will create dummy files and share all files amongst themselves. 
	 * 
	 * peer_n is designated as Querier peer which will issue search queries while the rest of the peers simulate updates 
	 * on the files 
	 *  
	 * @param nodes
	 * @param fileNumber
	 * @throws RemoteException
	 * @throws NotBoundException
	 */
	public void createTopology(int nodes, int fileNumber, int ttr, boolean pullMode) throws RemoteException, NotBoundException {
		
		fileNames = new ArrayList<String>(nodes*fileNumber);
		fileLocations = new ArrayList<FileLocation>(nodes*fileNumber);
		peerTesters = new PeerTester[nodes];
		
		//Create peers and link them together in a linear topology 
		// peer_0 <-> peer_1 <-> ... peer_n-1
		for ( int i = 0 ; i < nodes; i++ ) {
			String peerName = "peer_" + i;
			PeerTester pt;
			
			System.out.println("Creating " + peerName); 
			pt = new PeerTester(
					peerName,
					fileNumber,
					"127.0.0.1", 
					FIRST_PORT+i,
					pullMode);
			pt.exportPeerStub();
			
			//Configure TTR and propagation delay
			pt.getPeer().setPropagationDelay(propagationDelay);
			pt.getPeer().setDefaultTtr(ttr);
			
			//create dummy files for each peer 
			pt.createFiles(FILE_SIZE);
			
			//keep track of the created files so they can be used to issue queries
			for( String name : pt.getFileNames() )
				fileNames.add(name);
			
			//keep track of created FileLocations so they can be used to issue downloads
			fileLocations.addAll(pt.getPeer().getLocalFiles());
			
			//For each node except the first one (peer_0) we establish a neighbor relationship with the previous node
			if ( i > 0 ) {
				PeerTester neighbor = peerTesters[i-1];
				String addr = neighbor.getAddress();
				int port = neighbor.getPort(); 
				
				System.out.println("Adding link " + peerName + "<->" + neighbor.getName() );
				pt.establishNeighbor(new InetSocketAddress(addr,port));	
			}
			

			
			//Add peer to peer array
			peerTesters[i] = pt;
		}
		
		//Now create the peer that is going to issue the search queries
		String peerName = "peer_" + nodes;
		
		System.out.println("Creating Querier peer "+ peerName );
		peerQuerier = new PeerTester(
				peerName,
				fileNumber,
				"127.0.0.1", 
				FIRST_PORT+nodes,
				pullMode);
		peerQuerier.exportPeerStub();
		
		//Establish relationship with peer_0 (127.0.0.1:FIRST_PORT) 
		System.out.println("Adding link " + peerName + "<-> peer_0" );
		peerQuerier.establishNeighbor(new InetSocketAddress("127.0.0.1",FIRST_PORT));
	}
	
	/**
	 * Share all files amongst peers peer_0 through peer_n-1
	 */
	public void shareFiles() {
		//go through each peer 
		for ( PeerTester pt : peerTesters) {
			//For each peer try to download from all FileLocations created during topology creation
			for ( FileLocation loc : fileLocations ) {
				
				try {
					//if this file belongs to this peer dont try to download it 
					if (loc.getLocationAddress().getPort() == pt.getPort())
						continue;
					
					//Download the file 
					System.out.println("Peer " + pt.getName() + " downloading " + loc.getName());
					pt.getPeer().download( new FileLocation(loc.getLocationAddress(),
									loc.getName(),
									loc.getSize(),
									loc.getVersion(),
									loc.getTtr()),
							true);
					
				} catch (NotBoundException | IOException e) {
					System.out.println("Failed to download file");
				}				
			}
		}
	}
	
	/**
	 * Runs the actual test. Starts threads simulate file updates and refreshes. While 
	 * this threads are running the peerQuerier is used to each sequential queries and check the
	 * responses for inconsistent results
	 * @param count number of queries
	 * @throws RemoteException
	 */
	public void doQueries(int count) throws RemoteException { 
		//updaterThr simulates file updates periodically on each peer 
		Thread updaterThr = new FileUpdaterThread();
		//refresherThr triggers refresh operations periodically on each to try to propagate file updates through the network 
		//Thread refresherThr = new FileRefresherThread(REFRESH_PERIOD);
		
		//refresherThr.start();
		updaterThr.start();
		
		//Issue seuential queries 
		int invalidResults = peerQuerier.testInvalidQueries(count ,  fileNames, QUERY_PERIOD);
		int totalResults = peerQuerier.getPeer().getSearchResults().size();
		int percentage = invalidResults * 100 / totalResults;
		System.out.println( "Obtained " + invalidResults + " invalid results out of " + totalResults + " results (" + percentage + "%)" );
		
		//Stop threads
		//refresherThr.interrupt();
		updaterThr.interrupt();
	}

	
	/**
	 * Simulates file updates and refreshes until stopped
	 * @author jota
	 *
	 */
	class FileUpdaterThread extends Thread {

		private int version; 
		public FileUpdaterThread() {
			version = 1; 
		}

		@Override
		public void run() {
			//Each loop will
			// 1. update all files of all peers
			// 2. issue a refresh operation periodically on each peer until all peers have the same version of all files
			// We must check consistency manually by checking the version of each file since both push mode and pull mode
			// will have undetected inconsistencies during certain periods (those are in fact the inconsistencies we are trying
			// to measure) 
			while( !Thread.interrupted() ) {
				
				System.out.println("updating files");
				//go through each peer and update all of its files
				for ( PeerTester pt : peerTesters ) {
					try {
						pt.updateFiles();
					} catch (RemoteException e) {
						System.out.println("File update failed");
					}
				}
				//keep track of current version so we can check for consistency across the network 
				version++;
				try {
					//propagate the update through the network until all nodes have the same versions
					// we must use a loop to call refreshFiles() multiple times since refreshFiles depends
					// on the consistency information provided by the pull/push mechanisms. If the pull/push 
					//mechanism has not identified a file as INVALID that file will not get refreshed
					//By checking consistency manually we ensure by the end of the loop all files will have the same version
					boolean inconsistent = true; 
					while(inconsistent) {
						//try to refresh files
						System.out.println("refreshing files");
						for ( PeerTester pt : peerTesters ) {
							pt.getPeer().refreshFiles();
						}						
						//sleep enough time to have the update propagate through the nodes 
						//i.e give enough time for all peers to download new copies of each file
						sleep( DOWNLOAD_TIME );
						
						//check consistency
						inconsistent = false; 
						//go through reach remote file of each peer and see if they have the current version 
						for( PeerTester pt : peerTesters ) {
							for ( FileLocation file : pt.getPeer().getRemoteFiles() ) {
								if (file.getVersion() != version)
									inconsistent = true;
							}
						}
					}

				} catch (InterruptedException e) {
					return;
				}
			}
		}
	}
	

	/**
	 * Perform cleanup
	 */
	public void cleanup() {
		for( PeerTester pt: peerTesters ) {
			pt.cleanup();
		}
		peerQuerier.cleanup();
	}
	

	/**
	 * Create command line options
	 */
	private static void createOptions() {
		options = new Options();
		
		Option ttrValue   = Option.builder("t")
				.argName( "ttr" )
                .hasArg()
                .desc(  "ttr value in seconds of pull based consistency" )
                .build();
		Option pullFlag   = Option.builder("p")
				.argName( "ip_address" )
                .desc(  "Use pull approach for file consistency" )
                .build();		
		Option size   = Option.builder("s")
				.argName( "size" )
                .hasArg()
                .desc(  "Number of peers in the network" )
                .build();		

		options.addOption(pullFlag);
		options.addOption(size);
		options.addOption(ttrValue);
	}
	
	public static void main(String[] args) {
		
		//create and parse options
		createOptions();
		CommandLine cmd;
		
		int size, ttr;
		boolean pull;
		try {
			cmd = (new DefaultParser()).parse( options, args);
			
			//read pull mode 
			pull = false;
			if(cmd.hasOption("p"))
				pull = true;
			//read TTR 
			ttr = Integer.parseInt(cmd.getOptionValue("t","8"));
			//read size
			size = Integer.parseInt(cmd.getOptionValue("s","10"));
			
			
		} catch ( ParseException e ) {
			System.out.println("Error parsing arguments" + e.getMessage());
			return; 
		}
		
		InvalidQueryTest tst = new InvalidQueryTest(PROPAGATION_DELAY); 
		try {
			System.out.println("******Creating topology");
			tst.createTopology(size, 1, ttr, pull);
			System.out.println("******Topology created");
			Thread.sleep(1000);
			System.out.println("******Sharing files among peers");
			tst.shareFiles();
			System.out.println("*******Files Shared");
			Thread.sleep(3000);
			System.out.println("*******Doing queries");
			tst.doQueries(NUMBER_OF_QUERIES);
			System.out.println("*******Queries finished");
			Thread.sleep(5000);
			System.out.println("*******Doing cleanup");
			tst.cleanup(); 
			
			System.exit(0);
		} catch (RemoteException | NotBoundException | InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

}
