package japster2.peer;

/**
 * Thread that periodically calls the sendPolls method of a peer. 
 * 
 * Every time the sendPolls method is called all the FileLocations which are about to get expired get polled. (i.e. Their origins are
 * contacted to see if they are still valid) 
 * @author jota
 *
 */
public class PollerThread extends Thread {
	private Peer peer; 
	
	public PollerThread( Peer peer ) {
		this.peer = peer; 
	}

	@Override
	public void run() {
		while(!Thread.interrupted()) {
			peer.sendPolls();
			try {
				sleep(Const.POLL_PERIOD);
			} catch (InterruptedException e) {
				return; 
			} 
		}
		return; 
	}
}
