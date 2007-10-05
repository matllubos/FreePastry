package org.mpisws.p2p.transport.peerreview;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.mpisws.p2p.transport.peerreview.history.Hash;
import org.mpisws.p2p.transport.peerreview.history.HashProvider;
import org.mpisws.p2p.transport.peerreview.history.IndexEntry;
import org.mpisws.p2p.transport.peerreview.history.SecureHistory;
import org.mpisws.p2p.transport.peerreview.replay.EventCallback;
import org.mpisws.p2p.transport.peerreview.replay.IdentifierSerializer;

import rice.environment.logging.Logger;
import rice.p2p.commonapi.rawserialization.InputBuffer;
import rice.p2p.util.rawserialization.SimpleInputBuffer;
import rice.p2p.util.rawserialization.SimpleOutputBuffer;

public abstract class Verifier<Identifier> implements PeerReviewEvents {

  protected Identifier localHandle;
  protected SecureHistory history;
  PeerReviewCallback app;
  int numEventCallbacks;
  long now;
  boolean foundFault;
  
  boolean haveNextEvent;
  long nextEventIndex;
  IndexEntry next;
  boolean nextEventIsHashed;
  InputBuffer nextEvent;
  
  boolean initialized;
  short signatureSizeBytes;
  short hashSizeBytes;
  int numTimers;
  int[] eventToCallback = new int[256];
  protected Logger logger;
  protected IdentifierSerializer<Identifier> serializer; // was transport in c++ impl
  protected HashProvider hashProv;
  
  // these are shortcuts in the Java impl, they would all be true in the c++ impl, but in some cases it's more efficient if we can turn them off
  boolean useSendSign = false;  // true if we're sending the signature after the message
  boolean useSenderSeq = false;
  boolean useLogHashFlag = false;
//  boolean useBeginInitialized = true;
  
  public Verifier(
      IdentifierSerializer<Identifier> serializer, 
      HashProvider hashProv,
      SecureHistory history, 
      Identifier localHandle, 
      short signatureSizeBytes, 
      short hashSizeBytes, 
      int firstEntryToReplay, 
      long initialTime,
      Logger logger) /* : ReplayWrapper() */ throws IOException {
    this.logger = logger;
    this.history = history;
//    this->app = NULL;
    this.numEventCallbacks = 0;
    this.serializer = serializer;
    this.hashProv = hashProv;
    this.localHandle = localHandle;
    this.now = initialTime;
    this.foundFault = false;
    this.haveNextEvent = false;
    this.nextEventIndex = firstEntryToReplay-1;
    this.initialized = false;
//    if (useBeginInitialized) this.initialized = true;
    this.signatureSizeBytes = signatureSizeBytes;
    this.hashSizeBytes = hashSizeBytes;
    this.numTimers = 0;
    
    for (int i=0; i<256; i++)
      eventToCallback[i] = -1;
      
    fetchNextEvent();
    if (!haveNextEvent)
      foundFault = true;
  }
  
  public boolean verifiedOK() { 
    return !foundFault; 
  };


  public IndexEntry getNextEvent() {
    return next;
  }
  
  protected abstract void receive(Identifier from, ByteBuffer msg, long timeToDeliver);
  
  /**
   * Fetch the next log entry, or set the EOF flag 
   */
  protected void fetchNextEvent() throws IOException {
    haveNextEvent = false;
    nextEventIndex++;

//    unsigned char chash[hashSizeBytes];
    next = history.statEntry(nextEventIndex);
    if (logger.level <= Logger.FINE) logger.log("fetchNextEvent():"+next);

    if (next == null)
      return;
      
    if (next.getSizeInFile()<0) {
      // make the nextEvent only the content hash
      
      nextEventIsHashed = true;
      nextEvent = new SimpleInputBuffer(next.getContentHash().getBytes());
//      nextEventSize = hashSizeBytes;
//      memcpy(nextEvent, chash, hashSizeBytes);
      if (logger.level <= Logger.FINE) logger.log("Fetched log entry #"+nextEventIndex+" (type "+next.getType()+", hashed, seq="+next.getSeq()+")");
    } else {
      // load the nextEvent from the file
      
      nextEventIsHashed = false;
//      assert(nextEventSize < (int)sizeof(nextEvent));
      nextEvent = new SimpleInputBuffer(history.getEntry(nextEventIndex, next.getSizeInFile()));
      if (logger.level <= Logger.FINE) logger.log("Fetched log entry #"+nextEventIndex+" (type "+next.getType()+", size "+next.getSizeInFile()+" bytes, seq="+next.getSeq()+")");
//      vdump(nextEvent, nextEventSize);
    }
    
    haveNextEvent = true;
  }
  
  /**
   * Called by the state machine when it wants to send a message 
   */
  protected void send(Identifier target, ByteBuffer message, int relevantLen) throws IOException {
    int msgLen = message.remaining();
    int pos = message.position();
    int lim = message.limit();
    
//    assert(!datagram);

    if (relevantLen < 0)
      relevantLen = message.remaining();

//    char buf1[256], buf2[256];
    if (logger.level <= Logger.FINE) logger.log("Verifier::send("+target+", "+relevantLen+"/"+message.remaining()+" bytes)");
    //vdump(message, msglen);
    
    // Sanity checks 
    
    if (!haveNextEvent) {
      if (logger.level <= Logger.WARNING) logger.log("Replay: Send event after end of segment; marking as invalid");
      foundFault = true;
      return;
    }
    
    if (next.getType() == EVT_INIT) {
      if (logger.level <= Logger.FINER) logger.log("Skipped; next event is an INIT");
      return;
    }
    
    if (next.getType() != EVT_SEND) {
      if (logger.level <= Logger.WARNING) logger.log("Replay: SEND event during replay, but next event in log is #"+next.getType()+"; marking as invalid");
      foundFault = true;
      return;
    }

    // If the SEND is hashed, simply compare it to the predicted entry
    
    if (nextEventIsHashed) {
      SimpleOutputBuffer buf = new SimpleOutputBuffer();
//      // this code serializes the target to buf
//      assert(relevantLen < 1024);
//      //unsigned char buf[MAX_ID_SIZE+1+1024+hashSizeBytes];
//      int pos = 0;
      serializer.serialize(target, buf);
//      buf.write(bb.array(), bb.position(), bb.remaining());
//      target->getIdentifier()->write(buf, &pos, sizeof(buf));
//      buf[pos++] = (relevantlen<msglen) ? 1 : 0;
      if (useLogHashFlag) buf.writeBoolean(relevantLen<msgLen);
//      if (relevantlen>0) {
//        memcpy(&buf[pos], message, relevantlen);
//        pos += relevantlen;
//      }
//      
//      // this code serializes the message
      buf.write(message.array(), message.position(), relevantLen);
      
//      assert(pos<(sizeof(buf)-hashSizeBytes));
      if (relevantLen<msgLen) {        
  // ugly; this should be an argument
//        if (msglen == (relevantlen+hashSizeBytes))
//          memcpy(&buf[pos], &message[relevantlen], hashSizeBytes);
//        else
        message.position(pos);
        message.limit(lim);
        Hash hash = hashProv.hash(message);
//          
//        pos += hashSizeBytes;
      }
//      
//      // this code serializes the contentHash
//      unsigned char chash[hashSizeBytes];
//      hash(chash, buf, pos);
      Hash cHash = hashProv.hash(ByteBuffer.wrap(buf.getBytes()));
      if (!cHash.equals(next.getContentHash())) {
//      if (memcmp(chash, nextEvent, hashSizeBytes)) {
        if (logger.level <= Logger.WARNING) logger.log("Replay: SEND is hashed, but hash of predicted SEND entry does not match hash in the log");
        foundFault = true;
        return;
      }

      if (useSendSign) {
        fetchNextEvent();
        assert(next.getType() == EVT_SENDSIGN);
      }
      fetchNextEvent();
      return;
    }

    // Are we sending to the same destination? 
    Identifier logReceiver = serializer.deserialize(nextEvent);
    if (!logReceiver.equals(target)) {
      if (logger.level <= Logger.WARNING) logger.log("Replay: SEND to "+target+" during replay, but log shows SEND to "+logReceiver+"; marking as invalid");
      foundFault = true;
      return;
    }
    
    // Check the message against the message in the log
    boolean logIsHashed = false; 
    if (useLogHashFlag) logIsHashed = nextEvent.readBoolean();

    if (logIsHashed) {
      if (relevantLen >= msgLen) {
        if (logger.level <= Logger.WARNING) logger.log("Replay: Message sent during replay is entirely relevant, but log entry is partly hashed; marking as invalid");
        foundFault = true;
        return;
      }
      
      int logRelevantLen = nextEvent.bytesRemaining() - hashSizeBytes;
      assert(logRelevantLen >= 0);
      
      if (relevantLen != logRelevantLen) {
        if (logger.level <= Logger.WARNING) logger.log("Replay: Message sent during replay has "+relevantLen+" relevant bytes, but log entry has "+logRelevantLen+"; marking as invalid");
        foundFault = true;
        return;
      }
            
      byte[] loggedMsg = new byte[logRelevantLen];
      nextEvent.read(loggedMsg);
      ByteBuffer loggedMsgBB = ByteBuffer.wrap(loggedMsg);
      if ((relevantLen > 0) && message.equals(loggedMsgBB)) {
        if (logger.level <= Logger.WARNING) logger.log("Replay: Relevant part of partly hashed message differs");
        if (logger.level <= Logger.FINE) logger.log("Expected: ["+loggedMsgBB+"]");
        if (logger.level <= Logger.FINE) logger.log("Actual:   ["+message+"]");
        foundFault = true;
        return;
      }
      
      Hash logHash = hashProv.build(nextEvent);
      byte[] msgHashBytes = message.array();
      Hash msgHash = hashProv.build(msgHashBytes, msgHashBytes.length-hashSizeBytes, hashSizeBytes);
      assert(msgLen == (relevantLen + hashSizeBytes));
      if (!msgHash.equals(logHash)) {
        if (logger.level <= Logger.WARNING) logger.log("Replay: Hashed part of partly hashed message differs");
        if (logger.level <= Logger.FINE) logger.log("Expected: ["+logHash+"]");
        if (logger.level <= Logger.FINE) logger.log("Actual:   ["+msgHash+"]");
        foundFault = true;
        return;
      }
    } else {
      if (relevantLen < msgLen) {
        if (logger.level <= Logger.WARNING) logger.log("Replay: Message sent during replay is only partly relevant, but log entry is not hashed; marking as invalid");
        foundFault = true;
        return;
      }

      int logMsglen = nextEvent.bytesRemaining();
      if (msgLen != logMsglen) {
        if (logger.level <= Logger.WARNING) logger.log("Replay: Message sent during replay has "+msgLen+" bytes, but log entry has "+logMsglen+"; marking as invalid");
        foundFault = true;
        return;
      }
      
      byte[] loggedMsg = new byte[nextEvent.bytesRemaining()];
      nextEvent.read(loggedMsg);
      ByteBuffer loggedMsgBB = ByteBuffer.wrap(loggedMsg);
      if ((msgLen > 0) && loggedMsgBB.equals(message)) {
        if (logger.level <= Logger.WARNING) logger.log("Replay: Message sent during replay differs from message in the log");
        foundFault = true;
        return;
      }
    }

    if (useSendSign) {
      fetchNextEvent();
      assert(next.getType() == EVT_SENDSIGN);
    }
    fetchNextEvent();
  }
  
  /**
   * Maps EVT_XXX -> EventCallback
   */
  Map<Short, EventCallback> eventCallback = new HashMap<Short, EventCallback>();
  
  /**
   * This binds specific event types to one of the handlers 
   */
  public void registerEvent(EventCallback callback, short... eventType) {
    for (short s : eventType) {
      registerEvent(callback, s);
    }
  }
  
  public void registerEvent(EventCallback callback, short eventType) {
    if (eventCallback.containsKey(eventType)) {
      if (callback != eventCallback.get(eventType)) throw new IllegalStateException("Event #"+eventType+" registered twice");
    }
    eventCallback.put(eventType,callback);
  }
  
  /**
   * This is called by the Audit protocol to make another replay step; it returns true
   * if further calls are necessary, and false if the replay has finished. The idea
   * is that we can stop calling this if there is more important work to do, e.g. 
   * handle foreground requests 
   */
  public boolean makeProgress() throws IOException {
    logger.log("makeProgress()");
//    if (logger.level <= Logger.FINE) logger.log("makeProgress()");
    if (foundFault || !haveNextEvent)
      return false;
      
    if (!initialized && (next.getType() != EVT_CHECKPOINT) && (next.getType() != EVT_INIT)) {
      if (logger.level <= Logger.WARNING) logger.log("Replay: No INIT or CHECKPOINT found at the beginning of the log; marking as invalid "+next);
      foundFault = true;
      return false;
    }
    
    /**
     * Handle any pending timers. Note that we have to be sure to call them in the exact same
     * order as in the main code; otherwise there can be subtle bugs and side-effects. 
     */    
    // This code is the job of the SelectorManager, it's done in the super class of ReplaySM
    
//    boolean timerProgress = true;
//    while (timerProgress) {
//      now = next.getSeq() / 1000;
//      timerProgress = false;
//
//      int best = -1;
//      for (int i=0; i<numTimers; i++) {
//        if ((timer[i].time <= now) && ((best<0) || (timer[i].time<timer[best].time) || ((timer[i].time==timer[best].time) && (timer[i].id<timer[best].id))))
//          best = i;
//      }
//
//      if (best >= 0) {
//        int id = timer[best].id;
//        TimerCallback callback = timer[best].callback;
//        now = timer[best].time;
//        timer[best] = timer[--numTimers];
//        if (logger.level <= Logger.WARNING) logger.log(2, "Verifier: Timer expired (#"+id+", now="+now+")");
//        callback.timerExpired(id);
//        timerProgress = true;
//      }
//    }

    /* If we're done with this replay, return false */

//    if (!haveNextEvent)
//      return false;  

    /* Sanity checks */

    if (logger.level <= Logger.FINE) logger.log("Replaying event #"+nextEventIndex+" (type "+next.getType()+", seq="+next.getSeq()+", now="+now+")");
      
    if (nextEventIsHashed && (next.getType() != EVT_CHECKPOINT) && (next.getType() != EVT_INIT)) {
      if (logger.level <= Logger.WARNING) logger.log("Replay: Trying to replay hashed event");
      foundFault = true;
      return false;
    }
      
    /* Replay the next event */
      
    switch (next.getType()) {
      case EVT_SEND : /* SEND events should have been handled by Verifier::send() */
      {
//        if (logger.level <= Logger.FINE) logger.log("Replay: Encountered EVT_SEND, waiting for node.");
        if (logger.level <= Logger.WARNING) logger.log("Replay: Encountered EVT_SEND; marking as invalid");
//        transport->dump(2, nextEvent, next.getSizeInFile());
        foundFault = true;
        return false;
      }
      case EVT_RECV : /* Incoming message; feed it to the state machine */
      {
        Identifier sender = serializer.deserialize(nextEvent);

        long senderSeq = 0;
        if (useSenderSeq) senderSeq = nextEvent.readLong();
        boolean hashed = false;
        if (useLogHashFlag) hashed = nextEvent.readBoolean();
        
        int msgLen = nextEvent.bytesRemaining();
        int relevantLen = hashed ? (msgLen-hashSizeBytes) : msgLen;

//        unsigned char *msgbuf = (unsigned char*) malloc(msglen);
//        memcpy(msgbuf, &nextEvent[headerSize], msglen);
        
        byte[] msgBytes = new byte[msgLen];
        nextEvent.read(msgBytes);
        ByteBuffer msgBuf = ByteBuffer.wrap(msgBytes);
        
        long receiveTime = next.getSeq()/1000000;
        /* The next event is going to be a SIGN; skip it, since it's irrelevant here */

        if (useSendSign) {
          fetchNextEvent();
          if (!haveNextEvent || (next.getType() != EVT_SIGN) || (next.getSizeInFile() != (int)(hashSizeBytes+signatureSizeBytes))) {
            if (logger.level <= Logger.WARNING) logger.log("Replay: RECV event not followed by SIGN; marking as invalid");
            foundFault = true;
            return false;
          }
        }
        
        fetchNextEvent();
        
        /* Deliver the message to the state machine */
        
//        app->receive(sender, false, msgbuf, msglen);
        receive(sender, msgBuf, receiveTime);
        break;
      }
      case EVT_SIGN : /* SIGN events should have been handled by the preceding RECV */
      {
        if (logger.level <= Logger.WARNING) logger.log("Replay: Spurious SIGN event; marking as invalid");
        foundFault = true;
        return false;
      }
      case EVT_ACK : /* Skip ACKs */
      {
  // warning there should be an upcall here
        fetchNextEvent();
        break;
      }
      case EVT_SENDSIGN : /* Skip SENDSIGN events; they are not relevant during replay */
      {
        fetchNextEvent();
        break;
      }
      case EVT_CHECKPOINT : /* Verify CHECKPOINTs */
//      {
//        if (!initialized) {
//          if (!nextEventIsHashed) {
//          
//            /* If the state machine hasn't been initialized yet, we can use this checkpoint */
//          
//            initialized = true;
//            if (!app.loadCheckpoint(nextEvent, next.getSizeInFile())) {
//              if (logger.level <= Logger.WARNING) logger.log("Cannot load checkpoint");
//              foundFault = true;
//            }
//          } else {
//            if (logger.level <= Logger.WARNING) logger.log("Replay: Initial checkpoint is hashed; marking as invalid");
//            foundFault = true;
//          }
//        } else {
//        
//          /* Ask the state machine to do a checkpoint now ... */
//        
//          int maxlen = 1048576*4;
//          unsigned char *buf = (unsigned char *)malloc(maxlen);
//          int actualCheckpointSize = app->storeCheckpoint(buf, maxlen);
//
//          /* ... and compare it to the contents of the CHECKPOINT entry */
//
//          if (!nextEventIsHashed) {
//            if (actualCheckpointSize != nextEventSize) {
//              if (logger.level <= Logger.WARNING) logger.log("Replay: Checkpoint has different size (expected %d bytes, but got %d); marking as invalid", nextEventSize, actualCheckpointSize);
//              plog(2, "Expected:");
//              transport->dump(2, nextEvent, nextEventSize);
//              plog(2, "Found:");
//              transport->dump(2, buf, actualCheckpointSize);
//              foundFault = true;
//              free(buf);
//              return false;
//            }
//          
//            if (memcmp(buf, nextEvent, nextEventSize) != 0) {
//              if (logger.level <= Logger.WARNING) logger.log("Replay: Checkpoint does not match");
//              plog(2, "Expected:");
//              transport->dump(2, nextEvent, nextEventSize);
//              plog(2, "Found:");
//              transport->dump(2, buf, nextEventSize);
//
//              foundFault = true;
//              free(buf);
//              return false;
//            }
//          } else {
//            if (nextEventSize != hashSizeBytes) {
//              if (logger.level <= Logger.WARNING) logger.log("Replay: Checkpoint is hashed but has the wrong length?!?");
//              foundFault = true;
//              free(buf);
//              return false;
//            }
//          
//            unsigned char checkpointHash[hashSizeBytes];
//            hash(checkpointHash, buf, actualCheckpointSize);
//            if (memcmp(checkpointHash, nextEvent, hashSizeBytes) != 0) {
//              if (logger.level <= Logger.WARNING) logger.log("Replay: Checkpoint is hashed, but does not match hash value in the log");
//              foundFault = true;
//              free(buf);
//              return false;
//            }
//
//            vlog(4, "Hashed checkpoint is OK");
//            history->upgradeHashedEntry(nextEventIndex, buf, actualCheckpointSize);
//          }
//          
//          free(buf);
//        }
//          
//        fetchNextEvent();
//        break;
//      }
      case EVT_INIT : /* State machine is reinitialized; issue upcall */
      {
        initialized = true;
//        app->init();
        fetchNextEvent();
        break;
      }
      default :
      {
        if (!eventCallback.containsKey(next.getType())) {
          if (logger.level <= Logger.WARNING) logger.log("Replay: Unregistered event #"+next.getType()+"; marking as invalid");
          foundFault = true;
          return false;
        }

        IndexEntry temp = next;
        InputBuffer tempEvent = nextEvent;
        fetchNextEvent();
        eventCallback.get(temp.getType()).replayEvent(temp.getType(), tempEvent);
        break;
      }
    }
    
    return true;
  }

  public long getNextEventTime() {
    return next.getSeq()/1000000;
  }

}
