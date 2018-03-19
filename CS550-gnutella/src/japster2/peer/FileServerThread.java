package japster2.peer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;

import japster2.peer.Const;

/**
 * Listens for a TCP connection and sends a file to the client once the connection
 * has been established
 * @author jota
 *
 */
public class FileServerThread extends Thread {

	private ServerSocket serverSocket;
	private FileInputStream input;
	private Socket clientSocket;
	private OutputStream output;
	private String fileName;
	
	public FileServerThread( String fileName) throws IOException {
		
		this.fileName = fileName;
		
		if (!new File(fileName).exists())
			throw new FileNotFoundException();
		
		
		serverSocket = new ServerSocket(0);
		serverSocket.setSoTimeout(Const.FILE_SERVER_WAIT_TIME);

		input = null; 
		clientSocket = null;
		output = null;
	}
	
	/**
	 * Get the port where the ServerSocket was bound.
	 * @return int representing the port number
	 */
	public int getPort() {
		return serverSocket.getLocalPort();
	}
	
	/**
	 * Close all resources used by the thread.
	 */
	public void cleanup() {
		if( serverSocket != null ) {
			try {
				serverSocket.close();
			} catch (IOException e) {
				System.out.println("Error closing resource");
			}
		}
		if( clientSocket != null ) {
			try {
				clientSocket.close();
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

	@Override
	public void run() {
		try {

			clientSocket = serverSocket.accept();
			
			output = clientSocket.getOutputStream();
			input = new FileInputStream(new File (fileName));

			byte buffer[] = new byte[Const.BUFFER_SIZE];

			int len = input.read(buffer);
			while(len>0) {
				output.write(buffer,0,len);
				len = input.read(buffer);
			}
			System.out.println("File Transfer success (" + fileName + ")");
		} catch (SocketTimeoutException e) {
			System.out.println("File Transfer timed out waiting for client connection (" + fileName + ")");				

		} catch (IOException e) {
			System.out.println("File Transfer failed(" + fileName + ")");
			e.printStackTrace();

		} finally {
			cleanup();
		}
	}
}
