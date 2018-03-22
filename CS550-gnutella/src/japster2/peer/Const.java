package japster2.peer;


public class Const {
	private Const() {};
	
	public static final String INDEX_SERVICE_NAME = "IndexServer";
	public static final int INDEX_SERVICE_PORT = 34992;
	public static final int INDEX_REGISTRY_PORT = 1099;
	public static final int INDEX_TIMEOUT = 35000;
	
	public static final int BUFFER_SIZE = 1024*1024;
	public static final int FILE_SERVER_WAIT_TIME = 20000;
	
	public static final String PEER_SERVICE_NAME = "PeerFileServer";
	public static final int PEER_SERVICE_PORT = 34993;
	public static final int PEER_REGISTRY_PORT = 1098;
	
	public static final int TTL = 10; 
	public static final int DEFAULT_TTR = 30; 
	
	public static final int POLL_PERIOD = 5000;
	public static final int EXPIRATION_WATCH_PERIOD = 2000;

}
