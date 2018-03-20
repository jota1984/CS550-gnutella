package japster2.peer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;

import japster2.peer.Const;

/**
 * Creates a thread that will attempt to connect to a another Peer and download
 * a file from it. 
 * @author jota
 *
 */
public class FileDownloaderThread extends Thread {
	
	private String fileName;
	private float fileSize; 
	private String address; 
	private int port; 
	private boolean quiet; 
	
	private Peer peer; 
	
	private FileLocation location;
	
	private FileOutputStream output = null;
	private Socket socket = null;
	private InputStream input = null;
	
	/**
	 * Creates a new FileDownloaderThread object
	 * @param fileName String representing the full name that will be used to create the file on this peer 
	 * @param address String representation of the peer that will provide the file
	 * @param port int representing the port where the remote peer is serving the file
	 * @param quiet Wont print progress if true
	 */
	public FileDownloaderThread(Peer peer, String newFileName, int port, FileLocation location, boolean quiet) { 
		this.fileName = newFileName;
		this.fileSize = location.getSize(); 
		this.address = location.getLocationAddress().getHostString(); 
		this.port = port;
		this.location = location;
		this.quiet = quiet; 
		this.peer = peer; 
	}
	
	/**
	 * Close all resources used by the thread.
	 */
	public void cleanup() {
		if( socket != null ) {
			try {
				socket.close();
			} catch (IOException e) {
				System.out.println("Error closing resource");
			}
		}
		if( output != null ){
			try {
				output.close();
			} catch (IOException e) {
				System.out.println("Error closing resource");
			}
		}
		if( input != null ){
			try {
				input.close();
			} catch (IOException e) {
				System.out.println("Error closing resource");
			}
		} 	
	}
	
	private long progress;
	
	/**
	 * Print download progress in 5% increments
	 * @param downloaded
	 */
	public void printProgress(long downloaded) { 
		long currentProgress = 5*Math.round( (downloaded * 100) / (fileSize*5) ) ;
		if (!quiet && currentProgress > progress) {
			progress = currentProgress;
			String name = new File(fileName).getName();
			System.out.println("Downloading " + name + ": " + progress + "% downloaded");
		}
	}

	@Override
	public void run() {
		try {
			output = new FileOutputStream(new File(fileName));
			socket = new Socket(address,port);

			input = socket.getInputStream();

			byte buffer[] = new byte[Const.BUFFER_SIZE];

			int len = input.read(buffer);
			long downloaded = 0; 
			progress = -1;
			printProgress(downloaded);
			while(len>0) {
				output.write(buffer,0,len);
				downloaded += len;
				printProgress(downloaded);
				//System.out.println("" + downloaded + "bytes downloaded");
				len = input.read(buffer);
			}
		
			output.flush();
			if( !quiet)
				System.out.println("Download success (" + fileName + ")");
			peer.addRemoteFile(location);
		} catch (IOException e) {
			System.out.println("Download failed (" + fileName + ")");
			e.printStackTrace();
		} finally {
			cleanup();
		}
	}
}
