package japster2.peer;

/**
 * 
 * @author jota
 *
 */
public class UpdateTtrThread extends Thread {
	
	private Peer peer; 
	
	public UpdateTtrThread( Peer peer ) {
		this.peer = peer; 
	}

	@Override
	public void run() {
		while(!Thread.interrupted()) {
			peer.tickTtr();
			try {
				sleep(Const.EXPIRATION_WATCH_PERIOD);
			} catch (InterruptedException e) {
				return; 
			} 
		}
		return; 
	}
}
