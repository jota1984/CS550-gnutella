package japster2.peer;

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
