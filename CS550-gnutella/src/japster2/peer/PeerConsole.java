package japster2.peer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.NoSuchElementException;
import java.util.Scanner;

/**
 * Implements command line interface for Peer class. Runs on different thread so the program will not block waiting 
 * for input
 * <br>
 * Supports the following commands
 * <br>
 *	- hello IP:PORT:  Establish a new neighbor
 * <br>
 *	- neighbors: Print current neighbor list
 * <br>	
 *	- search FILENAME: Sends a query to neighbors
 * <br>
 *	- results: View results of previous search operations
 * <br>
 *  - download I: Download a file from the result list
 * <br>
 *  - viewlocal: print list of local files i.e files that were stored in the peer before the process was run
 * <br>
 *  - update I: increases the version number of a file from the viewlocal list
 * <br>
 *  - viewremote: print list of files that have been downloaded from other peers.
 * <br> 
 *  - refresh: redownload files from the viewremote list that are invalid
 * <br> 
 *	- quit: Exit program
 * 
 * @author jota
 *
 */
public class PeerConsole extends Thread {
	
	private Peer peer; 
	
	public PeerConsole(Peer peer) { 
		this.peer = peer; 
		start(); 
	}

	@Override
	public void run() {
		
		BufferedReader cin = new BufferedReader( new InputStreamReader(System.in));
        String line;
        
        ArrayList<FileLocation> remoteFiles; 
        ArrayList<FileLocation> localFiles;
        ArrayList<FileLocation> results;
        try {
        	
        		//Wait for user input 
			while ( (line = cin.readLine()) != null) {

				line = line.trim();

				//empty line
				if (line.length() == 0)
					continue;

				Scanner s = new Scanner(line);
				
				//Read first word of input
				String cmd = s.next();
				
				switch(cmd) {
				//hello <ip>:<port> Establish a new neighbor
				case "hello": 
					//parse arguments <ip>:<port>  
					s.useDelimiter(":");
					String host = s.next().trim();
					int port = s.nextInt();
					
					System.out.println("Connecting to " + host + ":" + port);
					try {
						peer.sayHello(new InetSocketAddress( host, port) );
					} catch (Exception e1) {
						System.out.println("Failed to contact peer");
						e1.printStackTrace();
					}
					break;
				
				//Print current neighbor list
				case "neighbors": 
					for( InetSocketAddress neighbor : peer.getNeighbors().keySet() ) {
						System.out.println(neighbor);
					}
					break;
				
				//search <filename> Sends a query to neighbors
				case "search": 
					
					//Parse the rest of the line as the argument
					s.useDelimiter("$");
					String query = s.next().trim();
					
					System.out.println("Searching for \"" + query + "\"");
					try {
						peer.search(query);
					} catch( RemoteException e) {
						System.out.println("Search failed");
					} 
					break;
					
				//View results of previous search operations
				case "results":
					results = peer.getSearchResults();
					System.out.println("Type \"download <i>\" to download a file from the following list");
					for( int i = 0; i < results.size(); i++) {
						System.out.println("" + i + "->"  + results.get(i))
;					}
					break;
				
				//download <i> Download a file from the results. <i> is the index of the result as printed
				//by the "results"command
				case "download": 
					try { 
						//parse the argument
						int i = s.nextInt();
						
						//get results from peer
						results = peer.getSearchResults();
						
						//check if argument is valid 
						if( i < 0 || i >= results.size() ) { 
							System.out.println("Invalid index");
							break;
						}
							
						//Get the desired FileLocation and try to download from it 
						FileLocation loc = results.get(i);
						System.out.println("Attempting to download " + loc);
						try {
							peer.download(loc, false);
						} catch (NotBoundException|IOException e) {
							System.out.println("Download failed. " + e.getMessage());
						} 
					} catch( NoSuchElementException e) { 
						System.out.println("Must specify index");
					}
					break;
					
				//viewlocal print list of local files i.e files that were stored in the peer before the process was run
				//These are the files that can be updated by the peer 
				case "viewlocal": 
					localFiles = peer.getLocalFiles();
					System.out.println("Type \"update <i>\" to update the version of a file from the following list");
					for( int i = 0; i < localFiles.size(); i++) {
						System.out.println("" + i + "->"  + localFiles.get(i))
;					}
					break;
				//viewremote print list of files that have been downloaded from other peers. 
				//These files cannot be updated since they belong to other peers, but they will be redownloaded 
				//if they are expired and a refresh command is issued
				case "viewremote": 
					remoteFiles = peer.getRemoteFiles();
					System.out.println("Downloaded files:");
					for( int i = 0; i < remoteFiles.size(); i++) {
						System.out.println("" + i + "->"  + remoteFiles.get(i))
;					}
					break;
				//update <i> increase the version of a local file, <i> is an index as printed by the "viewlocal"
				//command
				case "update":
					try { 
						//parse the argument 
						int i = s.nextInt();
						
						//get localFiles from peer object
						localFiles = peer.getLocalFiles();
						
						//check if argument is valid 
						if( i < 0 || i >= localFiles.size() ) { 
							System.out.println("Invalid index");
							break;
						}
						
						//update the file
						FileLocation file = localFiles.get(i);
						file.touch();
						
						System.out.println("Updated -> " + file );
						
						//if in push mode also broadcast an invalidate message
						if( !peer.getPullMode() ) {
							peer.sendInvalidate(file);
						}
						
					} catch( NoSuchElementException e) { 
						System.out.println("Must specify index");
					}
					break;
				//redownload all invalid remote files 
				case "refresh": 
					peer.refreshFiles();
					break;
				//exit the program 
				case "quit":
			        System.out.println("quitting");
					System.exit(0);				
				}
				
			}
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(0);
		}
	}
}
