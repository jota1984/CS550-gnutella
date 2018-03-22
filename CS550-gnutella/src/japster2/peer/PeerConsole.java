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
 * Implements command line interface for Peer class. 
 * <br>
 * Supports the following commands
 * <br>
 *	- connect: gets stub from IndexServer
 * <br>
 *	- search FILENAME: Searches IndexServer for a file
 * <br>	
 *	- register: Registers all local files with IndexServer
 *	<br>
 *	- export: Exports FileServer stub so other peers can download files from this Peer
 *	<br>
 *	- quit
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
        
        //Used to store list of remoteFiles
        ArrayList<FileLocation> remoteFiles; 
        //Used to store list of local files
        ArrayList<FileLocation> localFiles;
        //Used to store search results
        ArrayList<FileLocation> results;
        try {
			while ( (line = cin.readLine()) != null) {

				line = line.trim();
				if (line.length() == 0)
					continue;
				Scanner s = new Scanner(line);
				String cmd = s.next();
				switch(cmd) {
				case "hello": 
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
				case "neighbors": 
					for( InetSocketAddress neighbor : peer.getNeighbors().keySet() ) {
						System.out.println(neighbor);
					}
					break;
				case "search": 
					s.useDelimiter("$");
					String query = s.next().trim();
					System.out.println("Searching for \"" + query + "\"");
					try {
						peer.search(query);
					} catch( RemoteException e) {
						System.out.println("Exception communicating with IndexServer");
					} 
					break;
				case "results":
					results = peer.getFileLocations();
					System.out.println("Type \"download <i>\" to download a file from the following list");
					for( int i = 0; i < results.size(); i++) {
						System.out.println("" + i + "->"  + results.get(i))
;					}
					break;
				case "download": 
					try { 
						int i = s.nextInt();
						results = peer.getFileLocations();
						if( i < 0 || i >= results.size() ) { 
							System.out.println("Invalid index");
							break;
						}
							
						FileLocation loc = results.get(i);
						System.out.println("Attempting to download " + loc);
						try {
							peer.download(loc, false);
						} catch (NotBoundException e) {
							System.out.println("Download failed. " + e.getMessage());
						} catch (IOException e) {
							System.out.println("Download failed. " + e.getMessage());
						}
					} catch( NoSuchElementException e) { 
						System.out.println("Must specify index");
					}
					break;
				case "export": 
					peer.exportFileServer();
					System.out.println("FileServer object exported");
					break;
				case "viewlocal": 
					localFiles = peer.getLocalFiles();
					System.out.println("Type \"update <i>\" to update the version of a file from the following list");
					for( int i = 0; i < localFiles.size(); i++) {
						System.out.println("" + i + "->"  + localFiles.get(i))
;					}
					break;
				case "viewremote": 
					remoteFiles = peer.getRemoteFiles();
					System.out.println("Downloaded files");
					for( int i = 0; i < remoteFiles.size(); i++) {
						System.out.println("" + i + "->"  + remoteFiles.get(i))
;					}
					break;
				case "update":
					try { 
						int i = s.nextInt();
						localFiles = peer.getLocalFiles();
						
						if( i < 0 || i >= localFiles.size() ) { 
							System.out.println("Invalid index");
							break;
						}
						
						FileLocation file = localFiles.get(i);
						file.touch();
						System.out.println("Updated -> " + file );
						if( !peer.getPullMode() ) {
							peer.sendInvalidate(file);
						}
						
					} catch( NoSuchElementException e) { 
						System.out.println("Must specify index");
					}
					break;
				case "refresh": 
					peer.refreshFiles();
					break;
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
