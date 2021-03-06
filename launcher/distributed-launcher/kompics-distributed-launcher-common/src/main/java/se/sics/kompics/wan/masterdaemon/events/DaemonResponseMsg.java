package se.sics.kompics.wan.masterdaemon.events;

import se.sics.kompics.address.Address;
import se.sics.kompics.network.Message;


/**
 * The <code>DaemonReplyMessage</code> class.
 * 
 */
public abstract class DaemonResponseMsg extends Message {

	private static final long serialVersionUID = -6170070116165741869L;
	private final int daemonId;

	public DaemonResponseMsg(DaemonAddress source, Address destination) {
		super(source.getPeerAddress(), destination);
		this.daemonId = source.getDaemonId();
	}

	public int getDaemonId() {
		return daemonId;
	} 

}
