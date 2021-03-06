/**
 * This file is part of the Kompics P2P Framework.
 * 
 * Copyright (C) 2009 Swedish Institute of Computer Science (SICS)
 * Copyright (C) 2009 Royal Institute of Technology (KTH)
 *
 * Kompics is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package se.sics.kompics.p2p.overlay.chord;

import se.sics.kompics.Response;
import se.sics.kompics.p2p.overlay.key.NumericRingKey;

/**
 * The <code>ChordLookupResponse</code> class.
 * 
 * @author Cosmin Arad <cosmin@sics.se>
 * @version $Id$
 */
public final class ChordLookupResponse extends Response {

	/**
	 * The <code>ChordLookupStatus</code> class.
	 * 
	 * @author Cosmin Arad <cosmin@sics.se>
	 * @version $Id$
	 */
	public static enum ChordLookupStatus {
		SUCCESS, FAILURE;
	};

	private final NumericRingKey key;

	private final ChordAddress responsible;

	private final Object attachment;

	private final LookupInfo lookupInfo;

	private final ChordLookupStatus status;
	
	public ChordLookupResponse(ChordLookupRequest request, NumericRingKey key,
			ChordAddress responsible, Object attachment, LookupInfo lookupInfo) {
		super(request);
		this.key = key;
		this.responsible = responsible;
		this.attachment = attachment;
		this.lookupInfo = lookupInfo;
		this.status = ChordLookupStatus.SUCCESS;
	}

	public ChordLookupResponse(ChordLookupRequest request, NumericRingKey key,
			Object attachment, ChordAddress suspectedPeer) {
		super(request);
		this.key = key;
		this.attachment = attachment;
		this.responsible = suspectedPeer;
		this.lookupInfo = null;
		this.status = ChordLookupStatus.FAILURE;
	}

	public NumericRingKey getKey() {
		return key;
	}

	public ChordAddress getResponsible() {
		return responsible;
	}

	public Object getAttachment() {
		return attachment;
	}

	public LookupInfo getLookupInfo() {
		return lookupInfo;
	}
	
	public ChordLookupStatus getStatus() {
		return status;
	}
}
