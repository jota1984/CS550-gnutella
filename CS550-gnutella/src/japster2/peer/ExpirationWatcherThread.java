package japster2.peer;

public class ExpirationWatcherThread extends Thread {
	
	private Peer peer; 
	
	public ExpirationWatcherThread( Peer peer ) {
		this.peer = peer; 
	}

	@Override
	public void run() {
		while(!Thread.interrupted()) {
			peer.checkExpired();
			try {
				sleep(Const.EXPIRATION_WATCH_PERIOD);
			} catch (InterruptedException e) {
				return; 
			} 
		}
		return; 
	}
}
