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
package org.mpisws.p2p.transport.peerreview.commitment;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;

import org.mpisws.p2p.transport.peerreview.PeerReview;
import org.mpisws.p2p.transport.peerreview.PeerReviewCallback;
import org.mpisws.p2p.transport.peerreview.PeerReviewEvents;
import org.mpisws.p2p.transport.peerreview.history.IndexEntry;
import org.mpisws.p2p.transport.peerreview.history.SecureHistory;
import org.mpisws.p2p.transport.peerreview.infostore.PeerInfoStore;
import org.mpisws.p2p.transport.peerreview.misbehavior.Misbehavior;
import org.mpisws.p2p.transport.signature.CertificateTransportLayer;

import rice.p2p.util.rawserialization.SimpleInputBuffer;
import rice.p2p.util.tuples.Tuple;
import rice.selector.TimerTask;

public class CommitmentProtocolImpl<Identifier> implements CommitmentProtocol {
  public int MAX_PEERS = 250;
  public int INITIAL_TIMEOUT_MICROS = 1000000;
  public int RETRANSMIT_TIMEOUT_MICROS = 1000000;
  public int RECEIVE_CACHE_SIZE = 100;
  public int MAX_RETRANSMISSIONS = 2;
  public int TI_PROGRESS = 1;
  public int PROGRESS_INTERVAL_MICROS = 1000000;

  /**
   * We need to keep some state for each peer, including separate transmit and
   * receive queues
   */
  Map<Identifier, PeerInfo> peer = new HashMap<Identifier, PeerInfo>();
  
  /**
   * We cache a few recently received messages, so we can recognize duplicates.
   * We also remember the location of the corresponding RECV entry in the log,
   * so we can reproduce the matching acknowledgment
   */
  Map<Tuple<Identifier, Long>, ReceiveInfo<Identifier>> receiveCache;
  
  AuthenticatorStore<Identifier> authStore;
  SecureHistory history;
  PeerReviewCallback app;
  PeerReview<Identifier> peerreview;
  PeerInfoStore infoStore;
  CertificateTransportLayer<Identifier, ByteBuffer> transport;
  Identifier myHandle;
  Misbehavior misbehavior;
  long timeToleranceMicros;
  int nextReceiveCacheEntry;
  int signatureSizeBytes;
  int hashSizeBytes;
  
  TimerTask makeProgressTask;
//  int numPeers;
  
  public CommitmentProtocolImpl(PeerReview<Identifier> peerreview,
      CertificateTransportLayer<Identifier, ByteBuffer> transport,
      PeerInfoStore infoStore, AuthenticatorStore authStore,
      SecureHistory history, PeerReviewCallback app, Misbehavior misbehavior,
      long timeToleranceMicros) throws IOException {
    this.peerreview = peerreview;
    this.myHandle = transport.getLocalIdentifier();
//    this.signatureSizeBytes = transport.getSignatureSizeBytes();
//    this.hashSizeBytes = transport.getHashSizeBytes();
    this.transport = transport;
    this.infoStore = infoStore;
    this.authStore = authStore;
    this.history = history;
    this.app = app;
    this.misbehavior = misbehavior;
    this.nextReceiveCacheEntry = 0;
//    this.numPeers = 0;
    this.timeToleranceMicros = timeToleranceMicros;
    
//    for (int i=0; i<RECEIVE_CACHE_SIZE; i++) {
//      receiveCache[i].sender = NULL;
//      receiveCache[i].senderSeq = 0;
//      receiveCache[i].indexInLocalHistory = 0;
//    }
      
    initReceiveCache();
    makeProgressTask = new TimerTask(){    
      @Override
      public void run() {        
        makeProgressAllPeers();
      }    
    };
    
    peerreview.getEnvironment().getSelectorManager().schedule(makeProgressTask, PROGRESS_INTERVAL_MICROS, PROGRESS_INTERVAL_MICROS);    
  }
  
  /**
   * Load the last events from the history into the cache
   */
  void initReceiveCache() throws IOException {
    receiveCache = new LinkedHashMap<Tuple<Identifier, Long>, ReceiveInfo<Identifier>>(RECEIVE_CACHE_SIZE, 0.75f, true) {
      protected boolean removeEldestEntry(Map.Entry eldest) {
        return size() > RECEIVE_CACHE_SIZE;
      }
    };
    
    for (long i=history.getNumEntries()-1; (i>=1) && (receiveCache.size() < RECEIVE_CACHE_SIZE); i--) {
      IndexEntry hIndex = history.statEntry(i);
      if (hIndex.getType() == PeerReviewEvents.EVT_RECV) {
        // NOTE: this could be more efficient, because we don't need the whole thing
        SimpleInputBuffer sib = new SimpleInputBuffer(history.getEntry(hIndex, hIndex.getSizeInFile()));
        Identifier thisSender = peerreview.getIdSerializer().deserialize(sib);
        
        // NOTE: the message better start with the sender seq, but this is done within this protocol
        addToReceiveCache(thisSender, sib.readLong(), i);
      }
    }
  }

  void addToReceiveCache(Identifier id, long senderSeq, long indexInLocalHistory) {
    receiveCache.put(new Tuple<Identifier, Long>(id,senderSeq), new ReceiveInfo<Identifier>(id, senderSeq, indexInLocalHistory));
  }
  
  PeerInfo lookupPeer(Identifier handle) {
    PeerInfo ret = peer.get(handle);
    if (ret != null) return ret;
    
    ret = new PeerInfo(); 
    peer.put(handle, ret);
    return ret;
  }
  
  void notifyCertificateAvailable(Identifier id) {
    makeProgress(id);
  }
  

  /**
   * Checks whether an incoming message is already in the log (which can happen with duplicates).
   * If not, it adds the message to the log. 
   * @return The ack message and whether it was already logged.
   */
  Tuple<ByteBuffer, Boolean> logMessageIfNew(ByteBuffer message) throws IOException {
    // NOTE: sib does not molester the the message, it only goes after the backing array, but doesn't write to it
    SimpleInputBuffer sib = new SimpleInputBuffer(message);
    long seq = sib.readLong();
    Identifier handle = peerreview.getIdSerializer().deserialize(sib);
    
    throw new RuntimeException("implement me.");    
  }
  
  void notifyStatusChange(Identifier id, int newStatus) {
    makeProgressAllPeers();
  }

  private void makeProgressAllPeers() {
    for (Identifier i : peer.keySet()) {
      makeProgress(i);
    }
  }

  /**
   * Tries to make progress on the message queue of the specified peer, e.g. after that peer
   * has become TRUSTED, or after it has sent us an acknowledgment 
   */

  void makeProgress(Identifier idx) {
    throw new RuntimeException("todo: implement.");
  }
}
