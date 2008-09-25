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
package org.mpisws.p2p.transport.peerreview.infostore;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.mpisws.p2p.transport.peerreview.identity.IdentityTransport;

/**
 * In this class, the PeerReview library keeps information about its peers.
 * Specifically, it stores the last checked authenticator plus any challenges,
 * responses or proofs that are known about the peer.
 */
public class PeerInfoStoreImpl<Handle, Identifier> implements
    PeerInfoStore<Handle, Identifier> {
  IdentityTransport<Handle, Identifier> transport;

  File directory;
  // so far this should be a Map<Identifier,PeerInfoRecord>
  List<PeerInfoRecord<Handle, Identifier>> peerInfoRecords;
  int authenticatorSizeBytes;
  StatusChangeListener<Identifier> listener;
  boolean notificationEnabled;

  
  public PeerInfoStoreImpl(IdentityTransport<Handle, Identifier> transport) {
    this.authenticatorSizeBytes = -1;
    this.peerInfoRecords = new ArrayList<PeerInfoRecord<Handle,Identifier>>();
    this.directory = null;
    this.notificationEnabled = true;
    this.transport = transport;
    this.listener = null;
  }
  
  public static boolean isProof(Evidence e) {
    switch (e.getType()) {
      case CHAL_AUDIT:
        return false;
      case CHAL_SEND:
        return false;
      case PROOF_INCONSISTENT:
        return true;
      case PROOF_NONCONFORMANT:
        return true;
      default:
        throw new IllegalArgumentException("Cannot evaluate isProof("+e+"):"+e.getType());
//        panic("Cannot evaluate isProof("+e.getType()+")");
    }    
  }
  

  
  public void setStatusChangeListener(StatusChangeListener<Identifier> listener) {
    this.listener = listener;
  }

  public void setAuthenticatorSize(int sizeBytes) {
    assert((authenticatorSizeBytes < 0) && (sizeBytes > 0));
    authenticatorSizeBytes = sizeBytes; 
  }

  /* Locates evidence, or creates a new entry if 'create' is set to true */

  public EvidenceRecord<Handle, Identifier> findEvidence(Identifier originator, Identifier subject, long timestamp, boolean create) {
    PeerInfoRecord<Handle, Identifier> rec = find(subject, create);
    if (rec == null)
      return null;
      
    for (EvidenceRecord<Handle, Identifier> er : rec.evidence) {      
      if ((er.originator.equals(originator)) && (er.timestamp == timestamp)) {
        return er;
      }
    }

    if (!create)
      return null;
      
    EvidenceRecord<Handle, Identifier> evi = new EvidenceRecord<Handle, Identifier>(originator, timestamp,false,false,-1,null);
//    evi->next = rec->evidence;
//    evi->nextUnanswered = rec->unansweredEvidence;
//    evi->prevUnanswered = NULL;

//    if (rec.unansweredEvidence != null) {
//      assert(rec.unansweredEvidence.prevUnanswered == NULL);
      rec.unansweredEvidence.add(evi);
//    }
    
    rec.evidence.add(evi);
//    rec->unansweredEvidence = evi;
    
    return evi;
  }
  
  PeerInfoRecord<Handle, Identifier> find(Identifier id, boolean create) {
    for (PeerInfoRecord<Handle, Identifier> r : peerInfoRecords) {
      if (r.id.equals(id)) {
        return r;
      }
    }
    
    if (create) {
      PeerInfoRecord<Handle, Identifier> rec = new PeerInfoRecord<Handle, Identifier>(id);
      peerInfoRecords.add(rec);
      return rec;
    } else {
      return null;
    }
  }

  
  public void addEvidence(Identifier localIdentifier, Identifier subject,
      long evidenceSeq, Evidence evidence) {
    throw new RuntimeException("todo: implement");
  }

  public int getStatus(Identifier id) {
    return STATUS_TRUSTED;
  }

}
