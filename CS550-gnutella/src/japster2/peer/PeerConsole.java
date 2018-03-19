package japster2.peer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.util.ArrayList;
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
        
        //Used to store a FileLocation object obtained after a search command
        FileLocation location = null;
        //Used to store fileName of file searched using the search command
        String fileName = null;
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
					ArrayList<FileLocation> results = peer.getFileLocations();
					System.out.println("Type \"download <i>\" to download a file from the following list");
					for( int i = 0; i < results.size(); i++) {
						System.out.println("" + i + "->"  + results.get(i))
;					}
					break;
				case "download": 
					if (location != null && fileName != null ) {
						try {
							System.out.println("Attempting to download " + fileName +
									" from " + location);
							peer.download(fileName,location,false);
						} catch (NotBoundException|IOException e) {
							System.out.println("Download failed." + e.getMessage());
						} 
					} else {
						System.out.println("Must search a file first");
					}
					break;
				case "export": 
					peer.exportFileServer();
					System.out.println("FileServer object exported");
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
