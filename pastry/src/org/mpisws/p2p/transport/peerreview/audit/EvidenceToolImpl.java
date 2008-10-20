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
package org.mpisws.p2p.transport.peerreview.audit;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import org.mpisws.p2p.transport.peerreview.PeerReview;
import org.mpisws.p2p.transport.peerreview.commitment.AuthenticatorStore;
import org.mpisws.p2p.transport.peerreview.commitment.CommitmentProtocol;
import org.mpisws.p2p.transport.peerreview.history.IndexEntry;
import org.mpisws.p2p.transport.peerreview.history.SecureHistory;
import org.mpisws.p2p.transport.peerreview.history.logentry.EvtAck;
import org.mpisws.p2p.transport.peerreview.history.logentry.EvtInit;
import org.mpisws.p2p.transport.peerreview.history.logentry.EvtRecv;
import org.mpisws.p2p.transport.peerreview.history.logentry.EvtSend;
import org.mpisws.p2p.transport.peerreview.history.logentry.EvtSign;
import org.mpisws.p2p.transport.util.Serializer;

import rice.environment.logging.LogManager;
import rice.environment.logging.Logger;
import rice.p2p.commonapi.rawserialization.RawSerializable;
import rice.p2p.util.rawserialization.SimpleInputBuffer;
import rice.p2p.util.tuples.Tuple;

public class EvidenceToolImpl<Handle extends RawSerializable, Identifier extends RawSerializable> implements EvidenceTool<Handle, Identifier> {

  Logger logger;
  Serializer<Identifier> idSerializer;
  Serializer<Handle> handleSerializer;
  
  int hashSize;
  int signatureSize;
  private PeerReview<Handle, Identifier> peerreview;
  
  public EvidenceToolImpl(PeerReview<Handle, Identifier> peerreview, Serializer<Handle> handleSerializer, Serializer<Identifier> idSerializer, int hashSize, int signatureSize) {
    this.peerreview = peerreview;
    this.logger = peerreview.getEnvironment().getLogManager().getLogger(EvidenceToolImpl.class, null);
    this.handleSerializer = handleSerializer;
    this.idSerializer = idSerializer;
    this.hashSize = hashSize;
    this.signatureSize = signatureSize;
  }
  
  /**
   * 1) is the log snippet well-formed, i.e. are the entries of the correct length, and do they have the correct format?
   * 2) do we locally have the node certificate for each signature that occurs in the snippet?
   * 
   * if the former doesn't hold, it returns INVALID
   * if the latter doesn't hold, it returns CERT_MISSING, and returns the nodeID of the node whose certificate we need to request
   */
  public Tuple<Integer, Identifier> checkSnippet(LogSnippet snippet) {
    for (SnippetEntry entry : snippet.entries) {
      if (entry.isHash && entry.type != EVT_CHECKPOINT && entry.type != EVT_SENDSIGN && entry.type != EVT_SEND) {
        if (logger.level <= Logger.WARNING) logger.log("Malformed statement: Entry of type #"+entry.type+" is hashed");
        return new Tuple<Integer, Identifier>(INVALID,null);
      }
      
      /* Further processing depends on the entry type */
      if (logger.level <= Logger.FINER) logger.log("Entry type "+entry.type+", size="+entry.content.length+" "+(entry.isHash ? " (hashed)" : ""));

      try {
        SimpleInputBuffer sib = new SimpleInputBuffer(entry.content);
        switch (entry.type) {
          case EVT_SEND : /* No certificates needed; just do syntax checking */
            if (!entry.isHash) {
              new EvtSend<Identifier>(sib, idSerializer, hashSize);
            }
            break;
          case EVT_RECV : {/* We may need the certificate for the sender */          
            EvtRecv<Handle> recv = new EvtRecv<Handle>(sib,handleSerializer,hashSize);
            Identifier id = peerreview.getIdentifierExtractor().extractIdentifier(recv.getSenderHandle());
            if (!peerreview.hasCertificate(id)) {
              if (logger.level <= Logger.FINE) logger.log("AUDIT RESPONSE contains RECV from "+id+"; certificate needed");
              return new Tuple<Integer, Identifier>(CERT_MISSING, id);
            }
            break;
          }
          case EVT_SIGN : /* No certificates needed */
            new EvtSign(sib, signatureSize, hashSize);
            break;
          case EVT_ACK : /* We may need the certificate for the sender */
            EvtAck<Identifier> evtAck = new EvtAck<Identifier>(sib,idSerializer,hashSize,signatureSize);
            Identifier id = evtAck.getRemoteId();
            if (!peerreview.hasCertificate(id)) {
              if (logger.level <= Logger.FINE) logger.log("AUDIT RESPONSE contains RECV from "+id+"; certificate needed");
              return new Tuple<Integer, Identifier>(CERT_MISSING, id);
            }
            break;
          case EVT_CHECKPOINT : /* No certificates needed */
          case EVT_VRF :
          case EVT_CHOOSE_Q :
          case EVT_CHOOSE_RAND :
            break;
          case EVT_INIT: {/* No certificates needed */          
            new EvtInit<Handle>(sib,handleSerializer);
            break;
          }
          case EVT_SENDSIGN : /* No certificates needed */
            break;
          default : /* No certificates needed */
            assert(entry.type > EVT_MAX_RESERVED); 
            break;
        }
      } catch (IOException ioe) {
        if (logger.level <= Logger.WARNING) logger.log("Malformed entry:"+entry);
        return new Tuple<Integer, Identifier>(INVALID,null);
      }
    }
    
    return new Tuple<Integer, Identifier>(VALID,null);
  }

  static class SendEntryRecord {
    long seq;
    int hashedPlusPayloadIndex;
    int hashedPlusPayloadLen;
  }

  
  /**
   * The following method does several things: It verifies all the signatures in
   * a log snippet, it extracts all the authenticators for later forwarding to
   * the corresponding witnesses, and it delivers any new messages to the local
   * node that may be in the snippet (e.g. after an investigation)
   */
  public boolean checkSnippetSignatures(LogSnippet snippet,
      Handle subjectHandle, AuthenticatorStore<Identifier> authStoreOrNull,
      byte flags, CommitmentProtocol<Handle, Identifier> commitmentProtocol, 
      byte[] keyNodeHash, long keyNodeMaxSeq) {
    boolean startWithCheckpoint = (flags&FLAG_INCLUDE_CHECKPOINT) == FLAG_INCLUDE_CHECKPOINT;
//    const int signatureSizeBytes = transport.getSignatureSizeBytes();
//    const int hashSizeBytes = transport.getHashSizeBytes();
//    unsigned int identifierSizeBytes = transport.getIdentifierSizeBytes();
//
//    char buf1[256];
    byte[] currentNodeHash= snippet.baseHash;
    byte[] prevNodeHash = null;
    byte[] prevPrevNodeHash = null;
    
    long currentSeq = snippet.getFirstSeq();
    
    boolean firstEntry = true;
    boolean keyNodeFound = false;
    
    SnippetEntry lastEntry = null;
//    int lastEntryType = -1;
//    int lastEntryPos = -1;
//    int lastEntrySize = -1;
//    long lastEntrySeq = -1;
//    
//    memcpy(currentNodeHash, baseHash, hashSizeBytes);
//    memset(prevNodeHash, 0, sizeof(prevNodeHash));
//    memset(prevPrevNodeHash, 0, sizeof(prevNodeHash));
    
    
//    unsigned char subjectHandleInBytes[MAX_HANDLE_SIZE];
//    unsigned int subjectHandleInBytesLen = 0;
//    subjectHandle.write(subjectHandleInBytes, &subjectHandleInBytesLen, sizeof(subjectHandleInBytes));

    /* If FLAG_FULL_MESSAGES_SENDER is set, we must get a list of all the messages we have received
       from this node, so we can later check whether it has sent any other ones */

    HashSet<Long> seqBuf = new HashSet<Long>();
    int numSeqs = 0;
    try {
      if ((commitmentProtocol != null) && (flags & FLAG_FULL_MESSAGES_SENDER) == FLAG_FULL_MESSAGES_SENDER) {
        assert(peerreview != null);
        SecureHistory history = peerreview.getHistory();
        for (long i=history.getNumEntries()-1; i>=1; i--) {
          IndexEntry entry = history.statEntry(i);
          short type = entry.getType();
          if (entry.getSeq() < (snippet.getFirstSeq() - peerreview.getTimeToleranceMillis()*1000000))
            break;
          if (type == EVT_RECV) {
  //          unsigned char buf[MAX_HANDLE_SIZE+sizeof(long long)];
  //          unsigned int pos = 0;
  
            byte[] evtBytes = history.getEntry(entry,entry.getSizeInFile());
            EvtRecv<Handle> evtRecv = new EvtRecv<Handle>(new SimpleInputBuffer(evtBytes),handleSerializer,hashSize);
            Handle thisSender = evtRecv.getSenderHandle(); // = transport.readNodeHandle(buf, &pos, sizeof(buf));
            if (thisSender.equals(subjectHandle)) {
              seqBuf.add(evtRecv.getSenderSeq()); //*(long long*)&buf[pos];
            }
  //          delete thisSender;
          }
        }
      }
    } catch (IOException ioe) {
      throw new RuntimeException(ioe);
    }
    /* We keep a cache of recent SEND entries. This is necessary because, when checking
       an ACK entry, we need to compute the message hash from the corresponding SEND entry */

//    int maxSendEntries = 80;
//    struct sendEntryRecord {
//      long long seq;
//      int hashedPlusPayloadIndex;
//      int hashedPlusPayloadLen;
//    } secache[maxSendEntries];

    Map<Long, SendEntryRecord> secache = new HashMap<Long, SendEntryRecord>();
    
    
    int numSendEntries = 0;

    if (logger.level <= Logger.FINE) logger.log("Checking snippet (flags="+flags+")");
//  #warning check for FLAG_FULL here?

    /* We read the snipped entry by entry and check the following:
           - Does the computed top-level hash value match the second authenticator in the challenge?
           - Do all the signatures in RECV/SIGN and ACK entries check out?
       If extractAuthsFromResponse is set, we also add all authenticators to the authOut store.
       Note that we do not do conformance/consistency checking here; all we care about is whether
       the snippet matches the two authenticators. */

    int readptr = 0;
    for (SnippetEntry entry : snippet.entries) {
//    while (readptr < snippetLen) {
//      unsigned char entryType = snippet[readptr++];
//      unsigned char sizeCode = snippet[readptr++];
//      unsigned int entrySize = sizeCode;
//      bool entryIsHashed = (sizeCode == 0);
//
//      if (sizeCode == 0xFF) {
//        entrySize = *(unsigned short*)&snippet[readptr];
//        readptr += 2;
//      } else if (sizeCode == 0xFE) {
//        entrySize = *(unsigned int*)&snippet[readptr];
//        readptr += 4;
//      } else if (sizeCode == 0) {
//        entrySize = hashSizeBytes;
//      }

      if (logger.level <= Logger.FINER) logger.log("Entry type "+entry.type+", size="+entry.content.length+", seq="+entry.seq+" "+(entry.isHash ? " (hashed)" : ""));

      if (lastEntry != null && (lastEntry.type == EVT_RECV) && ((entry.type != EVT_SIGN) || (currentSeq != (lastEntry.seq+1)))) {
        if (logger.level <= Logger.WARNING) logger.log("Log snipped omits the mandatory EVT_SIGN after an EVT_RECV; flagging invalid");
        return false;
      }

//      unsigned char *entry = &snippet[readptr];
      prevPrevNodeHash = prevNodeHash;
      prevNodeHash = currentNodeHash;
//      memcpy(prevPrevNodeHash, prevNodeHash, hashSizeBytes);
//      memcpy(prevNodeHash, currentNodeHash, hashSizeBytes);

      /* Update the current node hash */

      byte[] contentHash;;
      if (entry.isHash) {
        contentHash = entry.content; //memcpy(contentHash, entry, hashSizeBytes);
      } else {
        contentHash = peerreview.hash(ByteBuffer.wrap(entry.content));
      }
      
      throw new RuntimeException("implement");
      
//      transport.hash(currentNodeHash, (const unsigned char*)&currentSeq, sizeof(currentSeq), &entryType, sizeof(entryType), currentNodeHash, hashSizeBytes, contentHash, hashSizeBytes);
//
//      /* Check signatures */
//
//      switch (entryType) {
//        case EVT_SEND :
//          if (!entryIsHashed) {
//            if (numSendEntries >= maxSendEntries)
//              panic("Too many pending SENDs without an ACK");
//
//            /* Add an entry to the send-entry cache */
//
//            secache[numSendEntries].seq = currentSeq;
//            secache[numSendEntries].hashedPlusPayloadIndex = readptr + identifierSizeBytes;
//            secache[numSendEntries].hashedPlusPayloadLen = entrySize - identifierSizeBytes;
//            numSendEntries ++;
//          }
//          break;
//        case EVT_RECV :
//
//          /* Do nothing. The processing for RECV will be done when we find the SIGN entry */
//
//          break;
//        case EVT_SENDSIGN :
//          if (!entryIsHashed) {
//            assert(entrySize >= signatureSizeBytes);
//            if (!firstEntry) {
//              if (lastEntryType != EVT_SEND) {
//                if (logger.level <= Logger.WARNING) logger.log("Spurious EVT_SENDSIGN in snippet; flagging invalid");
//                return false;
//              }
//
//              bool sendWasHashed = (snippet[lastEntryPos+identifierSizeBytes]>0);
//              if (sendWasHashed) {
//                unsigned char actualHash[hashSizeBytes];
//                transport.hash(actualHash, &entry[signatureSizeBytes], entrySize - signatureSizeBytes);
//                if (memcmp(actualHash, &snippet[lastEntryPos+lastEntrySize-hashSizeBytes], hashSizeBytes)) {
//                  if (logger.level <= Logger.WARNING) logger.log("EVT_SENDSIGN content does not match hash in EVT_SEND");
//                  return false;
//                }
//              } else {
//                if (entrySize > signatureSizeBytes) {
//                  if (logger.level <= Logger.WARNING) logger.log("EVT_SENDSIGN contains extra bytes, but preceding EVT_SEND was not hashed");
//                  return false;
//                }
//              }
//
//              unsigned char authPrefix[sizeof(long long)+hashSizeBytes];
//              *(long long*)&authPrefix[0] = lastEntrySeq;
//              memcpy(&authPrefix[sizeof(long long)], prevNodeHash, hashSizeBytes);
//
//              unsigned char signedHash[hashSizeBytes];
//              transport.hash(signedHash, authPrefix, sizeof(authPrefix));
//
//              int verifyResult = transport.verify(subjectHandle.getIdentifier(), signedHash, hashSizeBytes, &entry[0]);
//              assert((verifyResult == IdentityTransport::SIGNATURE_OK) || (verifyResult == IdentityTransport::SIGNATURE_BAD));
//              if (verifyResult != IdentityTransport::SIGNATURE_OK) {
//                if (logger.level <= Logger.WARNING) logger.log("Signature in EVT_SENDSIGN does not match node hash of EVT_SEND");
//                return false;
//              }
//
//              /* If the message in the SEND entry was for the local node, we check whether we 
//                 have previously delivered that message. If not, we deliver it now. */
//
//              if (commitmentProtocol && (flags & FLAG_FULL_MESSAGES_SENDER)) {
//                unsigned int pos = 0;
//                Identifier *dest = transport.readIdentifier(&snippet[lastEntryPos], &pos, snippetLen);
//                if (dest.equals(transport.getLocalHandle().getIdentifier())) {
//                  bool previouslyReceived = false;
//                  for (int i=0; i<numSeqs; i++) {
//                    if (seqBuf[i] == lastEntrySeq)
//                      previouslyReceived = true;
//                  }
//
//                  if (!previouslyReceived) {
//                    transport.logText(SUBSYSTEM, 3, "XXX accepting new message %lld", lastEntrySeq);
//
//                    /* We're cheating a little bit here... essentially we reconstruct the original
//                       USERDATA message as feed it to the commitment protocol, pretending that
//                       it has just arrived from the network. This causes all the right things to happen. */
//
//                    int relevantBytes = lastEntrySize-(identifierSizeBytes+1+(sendWasHashed ? hashSizeBytes : 0));
//                    int irrelevantBytes = entrySize - signatureSizeBytes;
//                    unsigned int messageMaxlen = 1+sizeof(long long)+MAX_HANDLE_SIZE+hashSizeBytes+signatureSizeBytes+1+relevantBytes+irrelevantBytes;
//                    unsigned char message[messageMaxlen];
//                    unsigned int pos = 0;
//                    writeByte(message, &pos, MSG_USERDATA);
//                    writeLongLong(message, &pos, lastEntrySeq);
//                    subjectHandle.write(message, &pos, messageMaxlen);
//                    writeBytes(message, &pos, prevPrevNodeHash, hashSizeBytes);
//                    writeBytes(message, &pos, entry, signatureSizeBytes);
//                    writeByte(message, &pos, (irrelevantBytes>0) ? relevantBytes : 0xFF);
//                    if (relevantBytes)
//                      writeBytes(message, &pos, &snippet[lastEntryPos+identifierSizeBytes+1], relevantBytes);
//                    if (irrelevantBytes)
//                      writeBytes(message, &pos, &entry[signatureSizeBytes], irrelevantBytes);
//                    assert(pos <= sizeof(message));
//
//                    commitmentProtocol.handleIncomingMessage(subjectHandle, message, pos);
//                  }
//                }
//
//                delete dest;
//              }
//            }
//          }
//          break;
//        case EVT_SIGN :
//          assert(entrySize == (hashSizeBytes + signatureSizeBytes));
//          if (!firstEntry) {
//
//            /* RECV entries must ALWAYS be followed by a SIGN */
//
//            if (lastEntryType != EVT_RECV) {
//              if (logger.level <= Logger.WARNING) logger.log("Spurious EVT_SIGN in snippet; flagging invalid");
//              return false;
//            }
//
//            /* Decode all the values */
//
//            unsigned int pos = lastEntryPos;
//            NodeHandle *senderHandle = transport.readNodeHandle(snippet, &pos, snippetLen);
//            long long senderSeq = readLongLong(snippet, &pos);
//            unsigned char *hashedPlusPayload = &snippet[pos];
//            int hashedPlusPayloadLen = lastEntrySize - (pos - lastEntryPos);
//
//            unsigned int pos2 = 0;
//            unsigned char receiverAsBytes[identifierSizeBytes];
//            subjectHandle.getIdentifier().write(receiverAsBytes, &pos2, sizeof(receiverAsBytes));
//            assert(pos2 == identifierSizeBytes);
//
//            const unsigned char *senderHtopMinusOne = entry;
//            const unsigned char *senderSignature = &entry[hashSizeBytes];
//
//            /* Extract the authenticator and check it */
//
//            unsigned char senderContentHash[hashSizeBytes];
//            transport.hash(senderContentHash, receiverAsBytes, identifierSizeBytes, hashedPlusPayload, hashedPlusPayloadLen);
//
//            unsigned char senderAuth[sizeof(long long)+hashSizeBytes+signatureSizeBytes];
//            unsigned char senderType = EVT_SEND;
//            *(long long*)&senderAuth[0] = senderSeq;
//            transport.hash(&senderAuth[sizeof(long long)], (const unsigned char*)&senderSeq, sizeof(senderSeq), &senderType, sizeof(senderType), senderHtopMinusOne, hashSizeBytes, senderContentHash, hashSizeBytes);
//            memcpy(&senderAuth[sizeof(long long)+hashSizeBytes], senderSignature, signatureSizeBytes);
//
//            bool isGoodAuth = peerreview ? peerreview.addAuthenticatorIfValid(authStoreOrNull, senderHandle.getIdentifier(), senderAuth) : (isAuthenticatorValid(senderAuth, senderHandle.getIdentifier(), transport) == VALID);
//            if (!isGoodAuth) {
//              if (logger.level <= Logger.WARNING) logger.log("Snippet contains a RECV from %s whose signature does not match; flagging invalid", senderHandle.render(buf1));
//              delete senderHandle;
//              return false;
//            }
//
//            delete senderHandle;
//          }
//
//          break;
//        case EVT_ACK :
//        {
//          /* Decode all the values */
//
//          assert(entrySize == (identifierSizeBytes + 2*sizeof(long long) + hashSizeBytes + signatureSizeBytes));
//          unsigned int pos = 0;
//          Identifier *receiverID = transport.readIdentifier(entry, &pos, entrySize);
//          long long senderSeq = readLongLong(entry, &pos);
//          long long receiverSeq = readLongLong(entry, &pos);
//          const unsigned char *receiverHtopMinusOne = &entry[pos];
//          pos += hashSizeBytes;
//          const unsigned char *receiverSignature = &entry[pos];
//          pos += signatureSizeBytes;
//
//          unsigned int pos2 = 0;
//          unsigned char senderAsBytes[identifierSizeBytes];
//          subjectHandle.getIdentifier().write(senderAsBytes, &pos2, sizeof(senderAsBytes));
//
//          /* Look up the entry in the send-entry cache */
//
//          int seidx = -1;
//          for (int i=0; (seidx<0) && (i<numSendEntries); i++) {
//            if (secache[i].seq == senderSeq)
//              seidx = i;
//          }
//
//          if (seidx < 0) {
//  #if 0
//            if (logger.level <= Logger.WARNING) logger.log("Snippet contains an ACK but not a SEND; maybe look in the log?");
//  #endif          
////                return false;
//  #warning here we should probably return false
//          } else {
//            /* Extract the authenticator and check it */
//
//            const unsigned char *hashedPlusPayload = &snippet[secache[seidx].hashedPlusPayloadIndex];
//            int hashedPlusPayloadLen = secache[seidx].hashedPlusPayloadLen;
//            secache[seidx] = secache[--numSendEntries];
//
//            unsigned char receiverContentHash[hashSizeBytes];
//            transport.hash(receiverContentHash, subjectHandleInBytes, subjectHandleInBytesLen, (const unsigned char*)&senderSeq, sizeof(senderSeq), hashedPlusPayload, hashedPlusPayloadLen);
//
//            unsigned char receiverAuth[sizeof(long long)+hashSizeBytes+signatureSizeBytes];
//            unsigned char receiverType = EVT_RECV;
//            *(long long*)&receiverAuth[0] = receiverSeq;
//            transport.hash(&receiverAuth[sizeof(long long)], (const unsigned char*)&receiverSeq, sizeof(receiverSeq), &receiverType, sizeof(receiverType), receiverHtopMinusOne, hashSizeBytes, receiverContentHash, hashSizeBytes);
//            memcpy(&receiverAuth[sizeof(long long)+hashSizeBytes], receiverSignature, signatureSizeBytes);
//
//            bool isGoodAuth = peerreview ? peerreview.addAuthenticatorIfValid(authStoreOrNull, receiverID, receiverAuth) : (isAuthenticatorValid(receiverAuth, receiverID, transport) == VALID);
//            if (!isGoodAuth) {
//              if (logger.level <= Logger.WARNING) logger.log("Snippet contains an ACK from %08X whose signature does not match; flagging invalid", receiverID);
//              delete receiverID;
//              return false;
//            }
//          }
//
//          delete receiverID;
//          break;
//        }
//        case EVT_CHECKPOINT :
//        case EVT_VRF :
//        case EVT_INIT :
//        case EVT_CHOOSE_Q :
//        case EVT_CHOOSE_RAND :
//          break;
//        default :
//          assert(entryType > EVT_MAX_RESERVED); 
//          break;
//      }
//
//      /* Remember where the last RECV entry was */
//
//      lastEntryType = entryType;
//      lastEntryPos = readptr;
//      lastEntrySeq = currentSeq;
//      lastEntrySize = entrySize;
//
//      /* If this is the first entry, its hash must match the first authenticator in the challenge */
//
//      if (keyNodeHash && !keyNodeFound) {
//        if (!memcmp(currentNodeHash, keyNodeHash, hashSizeBytes))  
//          keyNodeFound = true;
//      }
//
//      if (firstEntry) {
//        if (startWithCheckpoint) {
//          if ((entryType != EVT_CHECKPOINT) && (entryType != EVT_INIT)) {
//            if (logger.level <= Logger.WARNING) logger.log("Previous checkpoint requested, but not included; flagging invalid");
//            return false;
//          }
//          if (entryIsHashed) {
//            if (logger.level <= Logger.WARNING) logger.log("Previous checkpoint requested, but only hash is included; flagging invalid");
//            return false;
//          }
//        } else {
//        }
//
//        firstEntry = false;
//      }
//
//      /* Skip ahead to the next entry in the snippet */
//
//      readptr += entrySize;
//      if (readptr == snippetLen) // legitimate end
//        break;
//
//      unsigned char dseqCode = snippet[readptr++];
//      if (dseqCode == 0xFF) {
//        currentSeq = *(long long*)&snippet[readptr];
//        readptr += sizeof(long long);
//      } else if (dseqCode == 0) {
//        currentSeq ++;
//      } else {
//        currentSeq = currentSeq - (currentSeq%1000) + (dseqCode * 1000LL);
//      }
//
//      if (currentSeq <= lastEntrySeq) {
//        if (logger.level <= Logger.WARNING) logger.log("Log snippet attempts to roll back the sequence number from %lld to %lld; flagging invalid", lastEntrySeq, currentSeq);
//        return false;
//      }
//
//      if (keyNodeHash && !keyNodeFound && (currentSeq > keyNodeMaxSeq)) {
//        if (logger.level <= Logger.WARNING) logger.log("Hash of keyNode does not appear in [%lld,%lld]; flagging invalid", firstSeq, keyNodeMaxSeq);
//        return false;
//      }
//
//      assert(readptr <= snippetLen);
    }

    if (lastEntry.type == EVT_RECV) {
      if (logger.level <= Logger.WARNING) logger.log("Log snippet ends with a RECV event; missing mandatory SIGN");
      return false;
    }

    if (lastEntry.type == EVT_SEND) {
      if (logger.level <= Logger.WARNING) logger.log("Log snippet ends with a SEND event; missing mandatory SENDSIGN");
      return false;
    }

    if (keyNodeHash != null && !keyNodeFound) {
      if (logger.level <= Logger.WARNING) logger.log("Key node not found in log snippet; flagging invalid");
      return false;
    }
    
    return true;
  }

}
