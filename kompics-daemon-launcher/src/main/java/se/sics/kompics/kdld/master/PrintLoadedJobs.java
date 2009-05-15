package se.sics.kompics.kdld.master;

import se.sics.kompics.Request;

public class PrintLoadedJobs extends Request {

	private final int daemonId;
	
	public PrintLoadedJobs(int daemonId) {
		this.daemonId = daemonId;		
	}
	
	public int getDaemonId() {
		return daemonId;
	}
}
