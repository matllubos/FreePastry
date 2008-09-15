/*******************************************************************************

"FreePastry" Peer-to-Peer Application Development Substrate

Copyright 2002-2007, Rice University. Copyright 2006-2007, Max Planck Institute 
for Software Systems.  All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

- Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

- Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

- Neither the name of Rice  University (RICE), Max Planck Institute for Software 
Systems (MPI-SWS) nor the names of its contributors may be used to endorse or 
promote products derived from this software without specific prior written 
permission.

This software is provided by RICE, MPI-SWS and the contributors on an "as is" 
basis, without any representations or warranties of any kind, express or implied 
including, but not limited to, representations or warranties of 
non-infringement, merchantability or fitness for a particular purpose. In no 
event shall RICE, MPI-SWS or contributors be liable for any direct, indirect, 
incidental, special, exemplary, or consequential damages (including, but not 
limited to, procurement of substitute goods or services; loss of use, data, or 
profits; or business interruption) however caused and on any theory of 
liability, whether in contract, strict liability, or tort (including negligence
or otherwise) arising in any way out of the use of this software, even if 
advised of the possibility of such damage.

*******************************************************************************/ 
package org.mpisws.p2p.transport.peerreview.history.logentry;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.mpisws.p2p.transport.peerreview.PeerReviewConstants;
import org.mpisws.p2p.transport.peerreview.history.HashProvider;

import rice.p2p.commonapi.rawserialization.OutputBuffer;
import rice.p2p.commonapi.rawserialization.RawSerializable;

public class EvtRecv<Handle extends RawSerializable> implements PeerReviewConstants {
  Handle senderHandle;
  long senderSeq;
  byte[] payload; // may be full, or just relevant
  byte[] hash; // null if the whole payload is there (hashed == false)
  
  public EvtRecv(Handle senderHandle, long topSeq, ByteBuffer payload) {
    this.senderHandle = senderHandle;
    this.senderSeq = topSeq;
    this.payload = new byte[payload.remaining()];
    System.arraycopy(payload.array(), payload.position(), this.payload, 0, payload.remaining());    
  }
  
  public EvtRecv(Handle senderHandle, long topSeq, ByteBuffer payload,
      short relevantLen, HashProvider hasher) {
    this.senderHandle = senderHandle;
    this.senderSeq = topSeq;
    this.payload = new byte[payload.remaining()];
    System.arraycopy(payload.array(), payload.position(), this.payload, 0, relevantLen);
    hash = hasher.hash(ByteBuffer.wrap(this.payload));
  }

  public short getType() {
    return EVT_RECV;
  }

  public void serialize(OutputBuffer buf) throws IOException {
    senderHandle.serialize(buf);
    buf.writeLong(senderSeq);
    buf.writeBoolean(hash != null);  
    buf.write(payload, 0, payload.length);
    if (hash != null) buf.write(hash, 0, hash.length);
  }
}
