package japster2.peer;

/**
 * Periodically calls the tickTtr method of a peer, which in turn calls the tickTtr methods of all of its 
 * remote FileLocations. 
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
				sleep(Const.UPDATE_TTR_PERIOD);
			} catch (InterruptedException e) {
				return; 
			} 
		}
		return; 
	}
}