package se.sics.kompics.wan.master;

import java.util.UUID;
import se.sics.kompics.wan.masterdaemon.events.DaemonAddress;



public final class DaemonEntry implements Comparable<DaemonEntry> {

	private final DaemonAddress daemonAddress;

	private long refreshedAt;

	private UUID evictionTimerId;

	private final long addedAt;
	
	private final int groupId;

	
	public DaemonEntry(DaemonAddress daemonAddress, long now, long addedAt) {
		this(daemonAddress, now, addedAt, 0);
	}
	
	public DaemonEntry(DaemonAddress daemonAddress, long now, long addedAt, int groupId) {
		this.daemonAddress = daemonAddress;
		this.refreshedAt = now;
		this.addedAt = addedAt;
		this.groupId = groupId;
	}

	public DaemonAddress getDaemonAddress() {
		return daemonAddress;
	}

	public long getRefreshedAt() {
		return refreshedAt;
	}

	public void setRefreshedAt(long refreshedAt) {
		this.refreshedAt = refreshedAt;
	}

	public long getAddedAt() {
		return addedAt;
	}

	public UUID getEvictionTimerId() {
		return evictionTimerId;
	}

	public void setEvictionTimerId(UUID evictionTimerId) {
		this.evictionTimerId = evictionTimerId;
	}

	@Override
	public int compareTo(DaemonEntry that) {
		if (this.groupId > that.groupId)
		{
			return -1;
		}
		if (this.groupId < that.groupId)
		{
			return 1;
		}
		
		// more recent entries are lower than older entries
		if (this.refreshedAt > that.refreshedAt)
			return -1;
		if (this.refreshedAt < that.refreshedAt)
			return 1;		
		return 0;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result
				+ ((daemonAddress == null) ? 0 : daemonAddress.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		DaemonEntry other = (DaemonEntry) obj;
		if (daemonAddress == null) {
			if (other.daemonAddress != null)
				return false;
		} else if (!daemonAddress.equals(other.daemonAddress))
			return false;
		return true;
	}
	
	@Override
	public String toString() {
		
		return daemonAddress.toString();
	}
	
	public String getHostname()
	{
		return daemonAddress.getPeerAddress().getIp().getCanonicalHostName();
	}
	
	public int getGroupId() {
		return groupId;
	}
}