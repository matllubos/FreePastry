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
import org.mpisws.p2p.transport.peerreview.history.logentry.EvtRecv;
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
  PeerReview<Handle, Identifier> peerreview;
  PeerInfoStore<Handle, Identifier> infoStore;
  IdentityTransport<Handle, Identifier> transport;
  HashProvider hasher;
  Handle myHandle;
  Misbehavior<Identifier> misbehavior;
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
      SecureHistory history, PeerReviewCallback app, Misbehavior<Identifier> misbehavior,
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

      EvtRecv<Handle> recv;
      myHashTopMinusOne = history.getTopLevelEntry().getHash();
      if (udm.getRelevantLen() < udm.getPayload().remaining()) {
        recv = new EvtRecv<Handle>(udm.getSenderHandle(), udm.getTopSeq(), udm.getPayload(), udm.getRelevantLen(), transport);
      } else {
        recv = new EvtRecv<Handle>(udm.getSenderHandle(), udm.getTopSeq(), udm.getPayload());
      }
      SimpleOutputBuffer sob = new SimpleOutputBuffer();
      recv.serialize(sob);
      history.appendEntry(EVT_RECV, true, sob.getByteBuffer());
            
      HashSeq foo = history.getTopLevelEntry();
      myHashTop = foo.getHash();
      seqOfRecvEntry = foo.getSeq();
      
      addToReceiveCache(peerreview.getIdentifierExtractor().extractIdentifier(udm.getSenderHandle()), 
          udm.getTopSeq(), history.getNumEntries() - 1);
      if (logger.level < Logger.FINE) logger.log("New message logged as seq#"+seqOfRecvEntry);

      /* Construct the SIGN entry and append it to the log */

      
      sob = new SimpleOutputBuffer();
      new EvtSign(udm.getHTopMinusOne(),udm.getSignature()).serialize(sob);
      history.appendEntry(EVT_RECV, true, sob.getByteBuffer());
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
  
  /**
   * Handle an incoming USERDATA message 
   */
  void handleIncomingMessage(Identifier source, ByteBuffer msg, Map<String, Object> options) throws IOException {
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

    makeProgress(source);
  }

  long handleOutgoingMessage(Identifier target, ByteBuffer message, int relevantlen, Map<String, Object> options) throws IOException, SignatureException {
    assert(relevantlen >= 0);

    /* Append a SEND entry to our local log */
    
    byte[] hTopMinusOne, hTop, hToSign;
//    long topSeq;
    hTopMinusOne = history.getTopLevelEntry().getHash();
    if (relevantlen < message.remaining()) {
//      int logEntryMaxlen = MAX_ID_SIZE + 1 + relevantlen + hashSizeBytes;
//      unsigned char *logEntry = (unsigned char*) malloc(logEntryMaxlen);
//      unsigned int logEntryLen = 0;
//      target->getIdentifier()->write(logEntry, &logEntryLen, logEntryMaxlen);
//      writeByte(logEntry, &logEntryLen, 1);
//      if (relevantlen > 0) {
//        memcpy(&logEntry[logEntryLen], message, relevantlen);
//        logEntryLen += relevantlen;
//      }
//      transport->hash(&logEntry[logEntryLen], &message[relevantlen], msglen - relevantlen);
//      logEntryLen += hashSizeBytes;
//      
//      history->appendEntry(EVT_SEND, true, logEntry, logEntryLen);
//      free(logEntry);
//    } else {
//      unsigned char header[MAX_ID_SIZE+1];
//      unsigned int headerSize = 0;
//      target->getIdentifier()->write(header, &headerSize, sizeof(header));
//      writeByte(header, &headerSize, 0);
//      history->appendEntry(EVT_SEND, true, message, msglen, header, headerSize);
    }
    
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
//    byte relevantCode = (relevantlen == message.remaining()) ? (byte)0xFF : (byte)relevantlen;
//    unsigned int maxLen = 1 + sizeof(topSeq) + MAX_HANDLE_SIZE + hashSizeBytes + signatureSizeBytes + sizeof(relevantCode) + msglen;
//    unsigned char *buf = (unsigned char *)malloc(maxLen);
//    unsigned int totalLen = 0;

    UserDataMessage<Handle> udm = new UserDataMessage<Handle>(top.getSeq(), myHandle, hTopMinusOne, signature, message, relevantlen);
//    SimpleOutputBuffer sob = new SimpleOutputBuffer();
//    sob.writeByte(MSG_USERDATA);
//    sob.writeLong(top.getSeq());
//    peerreview.getIdSerializer().serialize(myHandle, sob);
//    sob.write(hTopMinusOne);
//    sob.write(signature);
//    sob.writeByte(relevantCode);
//    sob.write(message.array(), message.position(), message.remaining());
//    assert(totalLen <= maxLen);
    
    /* ... and put it into the send queue. If the node is trusted and does not have any
       unacknowledged messages, makeProgress() will simply send it out. */
    SimpleOutputBuffer sob = new SimpleOutputBuffer();
    udm.serialize(sob);
    lookupPeer(target).xmitQueue.addLast(new PacketInfo(sob.getByteBuffer(),options));
    makeProgress(target);
    
    return top.getSeq();
  }
  /* This is called if we receive an acknowledgment from another node */

  void handleIncomingAck(Identifier source, ByteBuffer message, Map<String, Object> options) throws IOException {
//    char buf1[256];
    SimpleInputBuffer sib = new SimpleInputBuffer(message);
    assert(sib.readByte() == MSG_ACK);
    
    /* Sanity check */    
//    if (msglen < (17 + peerreview.getIdentifierSizeBytes() + hashSizeBytes + signatureSizeBytes)) {
//      return;
//    }
        
    /* Acknowledgment: Log it (if we don't have it already) and send the next message, if any */

    if (logger.level <= Logger.FINE) logger.log("Received an ACK from "+source);
    /**
    MSG_ACK
    byte type = MSG_ACK
    nodeID recipientID
    long long sendEntrySeq
    long long recvEntrySeq
    hash hashTopMinusOne
    signature sig
    */
    
    Identifier remoteId = peerreview.getIdSerializer().deserialize(sib);
    long ackedSeq = sib.readLong();
    long hisSeq = sib.readLong();    
    byte[] hTopMinusOne = new byte[hasher.getHashSizeBytes()];
    sib.read(hTopMinusOne);
    byte[] signature = new byte[transport.signatureSizeInBytes()];
    sib.read(signature);
    
    if (transport.hasCertificate(remoteId)) {
      PeerInfo p = lookupPeer(source);
      /**
      MSG_USERDATA
      byte type = MSG_USERDATA
      long long topSeq   
      handle senderHandle
      hash hTopMinusOne
      signature sig
      byte relevantCode          // 0xFF = fully, otherwise length in bytes
      [payload bytes follow]
       */
      
      SimpleInputBuffer xmittedMsg = new SimpleInputBuffer(p.xmitQueue.getFirst().msg);
      xmittedMsg.readByte();
      long sendSeq = xmittedMsg.readLong();

      /* The ACK must acknowledge the sequence number of the packet that is currently
         at the head of the send queue */

      if (ackedSeq == sendSeq) {
        Identifier sendHandle = peerreview.getIdSerializer().deserialize(xmittedMsg);
        // skip the hTopMinusOne
        xmittedMsg.read(new byte[hasher.getHashSizeBytes()+transport.signatureSizeInBytes()]);
        int relevantCode = MathUtils.uByteToInt(xmittedMsg.readByte());
        
        int payloadLen = sib.bytesRemaining();
        byte[] payload = new byte[payloadLen];
        sib.read(payload);
        int relevantLen = (relevantCode == 0xFF) ? payloadLen : relevantCode;

        /* The peer will have logged a RECV entry, and the signature is calculated over that
           entry. To verify the signature, we must reconstruct that RECV entry locally */

        SimpleOutputBuffer sob = new SimpleOutputBuffer();
        peerreview.getIdSerializer().serialize(sendHandle, sob);
        sob.writeLong(sendSeq);
        sob.writeByte((relevantLen < payloadLen) ? 1 : 0);
        ByteBuffer recvEntryHeader = sob.getByteBuffer();
        
        byte[] innerHash;
        if (relevantLen < payloadLen) {
          byte[] irrelevantHash = hasher.hash(ByteBuffer.wrap(payload, relevantLen, payloadLen - relevantLen));
          innerHash = hasher.hash(recvEntryHeader, ByteBuffer.wrap(payload, 0, relevantLen), ByteBuffer.wrap(irrelevantHash));
        } else {
          innerHash = hasher.hash(recvEntryHeader, ByteBuffer.wrap(payload));
        }

        /* Now we're ready to check the signature */

//        unsigned char authenticator[sizeof(long long) + hashSizeBytes + signatureSizeBytes];
//        if (peerreview->extractAuthenticator(remoteId, hisSeq, EVT_RECV, innerHash, hTopMinusOne, signature, authenticator)) {
//
//          /* Signature is okay... append an ACK entry to the log */
//
//          dlog(2, "ACK is okay; logging");
//          unsigned char entry[2*sizeof(long long) + MAX_ID_SIZE + hashSizeBytes + signatureSizeBytes];
//          unsigned int pos = 0;
//          remoteId->write(entry, &pos, sizeof(entry));
//          writeLongLong(entry, &pos, ackedSeq);
//          writeLongLong(entry, &pos, hisSeq);
//          writeBytes(entry, &pos, hTopMinusOne, hashSizeBytes);
//          writeBytes(entry, &pos, signature, signatureSizeBytes);
//          history->appendEntry(EVT_ACK, true, entry, pos);
//          app->sendComplete(ackedSeq);
//
//          /* Remove the message from the xmit queue */
//
//          struct packetInfo *pi = peer[idx].xmitQueue;
//          peer[idx].xmitQueue = peer[idx].xmitQueue->next;
//          peer[idx].numOutstandingPackets --;
//          free(pi->message);
//          free(pi);
//
//          /* Make progress (e.g. by sending the next message) */
//
//          makeProgress(idx);
//        } else {
//          warning("Invalid ACK from <%s>; discarding", remoteId->render(buf1));
//        }
//      } else {
//        if (findAckEntry(remoteId, ackedSeq) < 0) {
//          warning("<%s> has ACKed something we haven't sent (%lld); discarding", remoteId->render(buf1), ackedSeq);
//        } else {
//          warning("Duplicate ACK from <%s>; discarding", remoteId->render(buf1));
//        }
//      }
//    } else {
//      warning("We got an ACK from <%s>, but we don't have the certificate; discarding", remoteId->render(buf1));
      }
    }
  }
}
