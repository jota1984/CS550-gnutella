package japster2.peer;

/**
 * Periodically calls the tickTtr method of a peer, which in turn calls the tickTtr methods of all of its 
 * remote FileLocations. After updating the TTR of each file calls sendPolls() to poll for any FileLocations
 * which have expired.
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
			long currentTime = System.currentTimeMillis();
			long nextRunTime = currentTime + Const.UPDATE_TTR_PERIOD;
			peer.tickTtr();
			peer.sendPolls();
			currentTime = System.currentTimeMillis();
			long waitTime = nextRunTime - currentTime; 
			try {
				if (waitTime > 0)
					sleep(waitTime);
			} catch (InterruptedException e) {
				return; 
			}
		}
		return; 
	}
}
