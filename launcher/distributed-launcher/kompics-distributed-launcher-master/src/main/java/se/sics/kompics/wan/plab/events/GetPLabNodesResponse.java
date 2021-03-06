package se.sics.kompics.wan.plab.events;

import java.util.HashSet;
import java.util.Set;

import se.sics.kompics.Response;
import se.sics.kompics.wan.plab.PLabHost;
import se.sics.kompics.wan.ssh.Host;

public class GetPLabNodesResponse extends Response {

	private final Set<PLabHost> hosts;

	public GetPLabNodesResponse(GetPLabNodesRequest request, Set<PLabHost> hosts) {
		super(request);
		this.hosts = new HashSet<PLabHost>();
		for (Host h : hosts) {
			this.hosts.add(new PLabHost(h));
		}
	}
	/**
	 * @return the nodeIds
	 */
	public Set<PLabHost> getHosts() {
		return hosts;
	}
}
