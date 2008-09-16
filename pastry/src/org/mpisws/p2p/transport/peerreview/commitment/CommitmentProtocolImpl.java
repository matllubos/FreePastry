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
import java.security.SignatureException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;

import org.mpisws.p2p.transport.peerreview.PeerReview;
import org.mpisws.p2p.transport.peerreview.PeerReviewCallback;
import org.mpisws.p2p.transport.peerreview.PeerReviewConstants;
import org.mpisws.p2p.transport.peerreview.history.HashProvider;
import org.mpisws.p2p.transport.peerreview.history.HashSeq;
import org.mpisws.p2p.transport.peerreview.history.IndexEntry;
import org.mpisws.p2p.transport.peerreview.history.SecureHistory;
import org.mpisws.p2p.transport.peerreview.history.logentry.EvtAck;
import org.mpisws.p2p.transport.peerreview.history.logentry.EvtRecv;
import org.mpisws.p2p.transport.peerreview.history.logentry.EvtSend;
import org.mpisws.p2p.transport.peerreview.history.logentry.EvtSign;
import org.mpisws.p2p.transport.peerreview.identity.IdentityTransport;
import org.mpisws.p2p.transport.peerreview.infostore.PeerInfoStore;
import org.mpisws.p2p.transport.peerreview.message.AckMessage;
import org.mpisws.p2p.transport.peerreview.message.UserDataMessage;
import org.mpisws.p2p.transport.peerreview.misbehavior.Misbehavior;

import rice.environment.logging.Logger;
import rice.p2p.commonapi.rawserialization.RawSerializable;
import rice.p2p.util.MathUtils;
import rice.p2p.util.rawserialization.SimpleInputBuffer;
import rice.p2p.util.rawserialization.SimpleOutputBuffer;
import rice.p2p.util.tuples.Tuple;
import rice.selector.TimerTask;

public class CommitmentProtocolImpl<Handle extends RawSerializable, Identifier extends RawSerializable> implements CommitmentProtocol<Handle, Identifier>, PeerReviewConstants {
  public int MAX_PEERS = 250;
  public int INITIAL_TIMEOUT_MICROS = 1000000;
  public int RETRANSMIT_TIMEOUT_MICROS = 1000000;
  public int RECEIVE_CACHE_SIZE = 100;
  public int MAX_RETRANSMISSIONS = 2;
  public int TI_PROGRESS = 1;
  public int PROGRESS_INTERVAL_MICROS = 1000000;
  public int MAX_ENTRIES_PER_MS = 1000000;      /* Max number of entries per millisecond */

  /**
   * We need to keep some state for each peer, including separate transmit and
   * receive queues
   */
  Map<Identifier, PeerInfo<Handle>> peer = new HashMap<Identifier, PeerInfo<Handle>>();
  
  /**
   * We cache a few recently received messages, so we can recognize duplicates.
   * We also remember the location of the corresponding RECV entry in the log,
   * so we can reproduce the matching acknowledgment
   */
  Map<Tuple<Identifier, Long>, ReceiveInfo<Identifier>> receiveCache;
  
  AuthenticatorStore<Identifier> authStore;
  SecureHistory history;
  PeerReviewCallback app;
  PeerReview<Handle, Identifier> peerreview;
  PeerInfoStore<Handle, Identifier> infoStore;
  IdentityTransport<Handle, Identifier> transport;
  HashProvider hasher;
  Handle myHandle;
  Misbehavior<Handle> misbehavior;
  /**
   * If the time is more different than this from a peer, we discard the message
   */
  long timeToleranceMillis;
  int nextReceiveCacheEntry;
  int signatureSizeBytes;
  int hashSizeBytes;
  
  TimerTask makeProgressTask;
  Logger logger;
//  int numPeers;
  
  public CommitmentProtocolImpl(PeerReview<Handle,Identifier> peerreview,
      IdentityTransport<Handle, Identifier> transport, HashProvider hasher,
      PeerInfoStore<Handle, Identifier> infoStore, AuthenticatorStore<Identifier> authStore,
      SecureHistory history, PeerReviewCallback app, Misbehavior<Handle> misbehavior,
      long timeToleranceMillis) throws IOException {
    this.peerreview = peerreview;
    this.myHandle = transport.getLocalIdentifier();
    this.hasher = hasher;
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
    this.timeToleranceMillis = timeToleranceMillis;
    
    this.logger = peerreview.getEnvironment().getLogManager().getLogger(CommitmentProtocolImpl.class, null);

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
      if (hIndex.getType() == PeerReviewConstants.EVT_RECV) {
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
  
  PeerInfo<Handle> lookupPeer(Handle handle) {
    PeerInfo<Handle> ret = peer.get(peerreview.getIdentifierExtractor().extractIdentifier(handle));
    if (ret != null) return ret;
    
    ret = new PeerInfo<Handle>(handle); 
    peer.put(peerreview.getIdentifierExtractor().extractIdentifier(handle), ret);
    return ret;
  }
  
  void notifyCertificateAvailable(Identifier id) {
    makeProgress(id);
  }
  

  /**
   * Checks whether an incoming message is already in the log (which can happen with duplicates).
   * If not, it adds the message to the log. 
   * @return The ack message and whether it was already logged.
   * @throws SignatureException 
   */
  public Tuple<AckMessage<Identifier>,Boolean> logMessageIfNew(UserDataMessage<Handle> udm) throws IOException, SignatureException {
    boolean loggedPreviously; // part of the return statement
    long seqOfRecvEntry;
    byte[] myHashTop;
    byte[] myHashTopMinusOne;
    
//    SimpleInputBuffer sib = new SimpleInputBuffer(message);
//    UserDataMessage<Handle> udm = UserDataMessage.build(sib, peerreview.getHandleSerializer(), peerreview.getHashSizeInBytes(), peerreview.getSignatureSizeInBytes());
    
    /* Check whether the log contains a matching RECV entry, i.e. one with a message
    from the same node and with the same send sequence number */

    long indexOfRecvEntry = findRecvEntry(peerreview.getIdentifierExtractor().extractIdentifier(udm.getSenderHandle()), udm.getTopSeq());

    /* If there is no such RECV entry, we append one */

    if (indexOfRecvEntry < 0L) {
      /* Construct the RECV entry and append it to the log */

      myHashTopMinusOne = history.getTopLevelEntry().getHash();
      EvtRecv<Handle> recv = udm.getReceiveEvent(transport);
      history.appendEntry(EVT_RECV, true, recv.serialize());
            
      HashSeq foo = history.getTopLevelEntry();
      myHashTop = foo.getHash();
      seqOfRecvEntry = foo.getSeq();
      
      addToReceiveCache(peerreview.getIdentifierExtractor().extractIdentifier(udm.getSenderHandle()), 
          udm.getTopSeq(), history.getNumEntries() - 1);
      if (logger.level < Logger.FINE) logger.log("New message logged as seq#"+seqOfRecvEntry);

      /* Construct the SIGN entry and append it to the log */
      
      history.appendEntry(EVT_RECV, true, new EvtSign(udm.getHTopMinusOne(),udm.getSignature()).serialize());
      loggedPreviously = false;
    } else {
      loggedPreviously = true;
      
      /* If the RECV entry already exists, retrieve it */
      
//      unsigned char type;
//      bool ok = true;
      IndexEntry i2 = history.statEntry(indexOfRecvEntry); //, &seqOfRecvEntry, &type, NULL, NULL, myHashTop);
      IndexEntry i1 = history.statEntry(indexOfRecvEntry-1); //, NULL, NULL, NULL, NULL, myHashTopMinusOne);
      assert(i1 != null && i2 != null && i2.getType() == EVT_RECV) : "i1:"+i1+" i2:"+i2;
      seqOfRecvEntry = i2.getSeq();
      myHashTop = i2.getNodeHash();
      myHashTopMinusOne = i1.getNodeHash();
      if (logger.level < Logger.FINE) logger.log("This message has already been logged as seq#"+seqOfRecvEntry);
    }

    /* Generate ACK = (MSG_ACK, myID, remoteSeq, localSeq, myTopMinusOne, signature) */

    byte[] hToSign = transport.hash(ByteBuffer.wrap(MathUtils.longToByteArray(seqOfRecvEntry)), ByteBuffer.wrap(myHashTop));

    AckMessage<Identifier> ack = new AckMessage<Identifier>(
        peerreview.getIdentifierExtractor().extractIdentifier(myHandle),
        udm.getTopSeq(),
        seqOfRecvEntry,
        myHashTopMinusOne,
        transport.sign(hToSign));
    
    return new Tuple<AckMessage<Identifier>,Boolean>(ack, loggedPreviously);
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
  
  long findRecvEntry(Identifier id, long seq) {
    ReceiveInfo<Identifier> ret = receiveCache.get(new Tuple<Identifier, Long>(id,seq));
    if (ret == null) return -1;
    return ret.indexInLocalHistory;
  }
  
  long findAckEntry(Identifier id, long seq) {
    return -1;
  }
  
  /**
   * Handle an incoming USERDATA message 
   */
  void handleIncomingMessage(Handle source, ByteBuffer msg, Map<String, Object> options) throws IOException {
//    char buf1[256];    
    SimpleInputBuffer sib = new SimpleInputBuffer(msg);
    assert(sib.readByte() == MSG_USERDATA);

    /* Sanity checks */

    if (msg.remaining() < (17 + hashSizeBytes + signatureSizeBytes)) {
      if (logger.level <= Logger.WARNING) logger.log("Short application message from "+source+"; discarding.");
      return;
    }

    /* Check whether the timestamp (in the sequence number) is close enough to our local time.
       If not, the node may be trying to roll forward its clock, so we discard the message. */
    long seq = sib.readLong();
    long txmit = (seq / MAX_ENTRIES_PER_MS);

    if ((txmit < (peerreview.getTime()-timeToleranceMillis)) || (txmit > (peerreview.getTime()+timeToleranceMillis))) {
      if (logger.level <= Logger.WARNING) logger.log("Invalid sequence no #"+seq+" on incoming message (dt="+(txmit-peerreview.getTime())+"); discarding");
      return;
    }

    /**
     * Append a copy of the message to our receive queue. If the node is
     * trusted, the message is going to be delivered directly by makeProgress();
     * otherwise a challenge is sent.
     */
    lookupPeer(source).recvQueue.addLast(new PacketInfo(msg, options));

    makeProgress(peerreview.getIdentifierExtractor().extractIdentifier(source));
  }

  long handleOutgoingMessage(Handle target, ByteBuffer message, int relevantlen, Map<String, Object> options) throws IOException, SignatureException {
    assert(relevantlen >= 0);

    /* Append a SEND entry to our local log */
    
    byte[] hTopMinusOne, hTop, hToSign;
//    long topSeq;
    hTopMinusOne = history.getTopLevelEntry().getHash();
    EvtSend<Identifier> evtSend;
    if (relevantlen < message.remaining()) {      
      evtSend = new EvtSend<Identifier>(peerreview.getIdentifierExtractor().extractIdentifier(target),message,relevantlen,hasher);
    } else {
      evtSend = new EvtSend<Identifier>(peerreview.getIdentifierExtractor().extractIdentifier(target),message);
    }
    history.appendEntry(evtSend.getType(), true, evtSend.serialize());
    
    //  hTop, &topSeq
    HashSeq top = history.getTopLevelEntry();
    
    /* Maybe we need to do some mischief for testing? */
    
    if (misbehavior != null) {
      misbehavior.maybeChangeSeqInUserMessage(top.getSeq());
    }
    
    /* Sign the authenticator */
      
    hToSign = hasher.hash(ByteBuffer.wrap(MathUtils.longToByteArray(top.getSeq())), ByteBuffer.wrap(top.getHash()));

    byte[] signature = transport.sign(hToSign);
    
    /* Append a SENDSIGN entry */
    
    ByteBuffer relevantMsg = message;
    if (relevantlen < message.remaining()) {
      relevantMsg = ByteBuffer.wrap(message.array(), message.position(), relevantlen);
    }
    history.appendEntry(EVT_SENDSIGN, true, relevantMsg, ByteBuffer.wrap(signature));
    
    /* Maybe do some more mischief for testing? */

    if (misbehavior != null && misbehavior.dropAfterLogging(target, message, options)) {
      return top.getSeq();
    }
    
    /* Construct a USERDATA message... */
    
    assert((relevantlen == message.remaining()) || (relevantlen < 255));    

    UserDataMessage<Handle> udm = new UserDataMessage<Handle>(top.getSeq(), myHandle, hTopMinusOne, signature, message, relevantlen, options);
    
    /* ... and put it into the send queue. If the node is trusted and does not have any
       unacknowledged messages, makeProgress() will simply send it out. */
    lookupPeer(target).xmitQueue.addLast(udm);
    makeProgress(peerreview.getIdentifierExtractor().extractIdentifier(target));
    
    return top.getSeq();
  }
  /* This is called if we receive an acknowledgment from another node */

  void handleIncomingAck(Handle source, AckMessage<Identifier> ackMessage, Map<String, Object> options) throws IOException {
//  AckMessage<Identifier> ackMessage = AckMessage.build(sib, peerreview.getIdSerializer(), hasher.getHashSizeBytes(), transport.signatureSizeInBytes());

    /* Acknowledgment: Log it (if we don't have it already) and send the next message, if any */

    if (logger.level <= Logger.FINE) logger.log("Received an ACK from "+source);
    // TODO: check that ackMessage came from the source
        
    if (transport.hasCertificate(ackMessage.getNodeId())) {
      PeerInfo<Handle> p = lookupPeer(source);

      UserDataMessage<Handle> udm = p.xmitQueue.getFirst();

      /* The ACK must acknowledge the sequence number of the packet that is currently
         at the head of the send queue */

      if (ackMessage.getSendEntrySeq() == udm.getTopSeq()) {
        
        /* Now we're ready to check the signature */

        byte[] innerHash = udm.getInnerHash(peerreview.getHandleSerializer(), transport);
        
        Authenticator authenticator = peerreview.extractAuthenticator(
            ackMessage.getNodeId(), ackMessage.getRecvEntrySeq(), EVT_RECV, innerHash, 
            ackMessage.getHashTopMinusOne(), ackMessage.getSignature());
        if (authenticator != null) {

          /* Signature is okay... append an ACK entry to the log */

          if (logger.level <= Logger.FINE) logger.log("ACK is okay; logging "+ackMessage);
          
          EvtAck<Identifier> evtAck = new EvtAck<Identifier>(ackMessage.getNodeId(), ackMessage.getSendEntrySeq(), ackMessage.getRecvEntrySeq(), ackMessage.getHashTopMinusOne(), ackMessage.getSignature());
          history.appendEntry(EVT_ACK, true, evtAck.serialize());
          app.sendComplete(ackMessage.getSendEntrySeq());

          /* Remove the message from the xmit queue */

          p.xmitQueue.removeFirst();

          /* Make progress (e.g. by sending the next message) */

          makeProgress(peerreview.getIdentifierExtractor().extractIdentifier(p.getHandle()));
        } else {
          if (logger.level <= Logger.WARNING) logger.log("Invalid ACK from <"+ackMessage.getNodeId()+">; discarding");
        }
      } else {
        if (findAckEntry(ackMessage.getNodeId(), ackMessage.getSendEntrySeq()) < 0) {
          if (logger.level <= Logger.WARNING) logger.log("<"+ackMessage.getNodeId()+"> has ACKed something we haven't sent ("+ackMessage.getSendEntrySeq()+"); discarding");
        } else {
          if (logger.level <= Logger.WARNING) logger.log("Duplicate ACK from <"+ackMessage.getNodeId()+">; discarding");
        }
      }
    } else {
      if (logger.level <= Logger.WARNING) logger.log("We got an ACK from <"+ackMessage.getNodeId()+">, but we don't have the certificate; discarding");     
    }
  }
}
