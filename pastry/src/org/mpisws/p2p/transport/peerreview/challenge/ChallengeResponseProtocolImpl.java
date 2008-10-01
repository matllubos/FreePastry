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
package org.mpisws.p2p.transport.peerreview.challenge;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;

import org.mpisws.p2p.transport.peerreview.PeerReviewCallback;
import org.mpisws.p2p.transport.peerreview.PeerReviewConstants;
import org.mpisws.p2p.transport.peerreview.PeerReviewImpl;
import org.mpisws.p2p.transport.peerreview.commitment.AuthenticatorStore;
import org.mpisws.p2p.transport.peerreview.commitment.CommitmentProtocol;
import org.mpisws.p2p.transport.peerreview.evidence.ChallengeAudit;
import org.mpisws.p2p.transport.peerreview.history.HashPolicy;
import org.mpisws.p2p.transport.peerreview.history.IndexEntry;
import org.mpisws.p2p.transport.peerreview.history.SecureHistory;
import org.mpisws.p2p.transport.peerreview.identity.IdentityTransport;
import org.mpisws.p2p.transport.peerreview.infostore.EvidenceRecord;
import org.mpisws.p2p.transport.peerreview.infostore.PeerInfoStore;
import org.mpisws.p2p.transport.peerreview.message.AckMessage;
import org.mpisws.p2p.transport.peerreview.message.ChallengeMessage;
import org.mpisws.p2p.transport.peerreview.message.ResponseMessage;
import org.mpisws.p2p.transport.peerreview.message.UserDataMessage;

import rice.environment.logging.Logger;
import rice.p2p.commonapi.rawserialization.RawSerializable;
import rice.p2p.util.rawserialization.SimpleInputBuffer;
import rice.p2p.util.tuples.Tuple;

public class ChallengeResponseProtocolImpl<Handle extends RawSerializable, Identifier extends RawSerializable> 
    implements PeerReviewConstants, ChallengeResponseProtocol<Handle, Identifier> {
  PeerReviewImpl<Handle, Identifier> peerreview;
  IdentityTransport<Handle, Identifier> transport;
  PeerInfoStore<Handle, Identifier> infoStore;
  SecureHistory history;
  AuthenticatorStore<Identifier> authOutStore; 
  Object auditProtocol;
  CommitmentProtocol<Handle, Identifier> commitmentProtocol;
  PeerReviewCallback<Handle, Identifier> app;
  private Logger logger;

  public ChallengeResponseProtocolImpl(
      PeerReviewImpl<Handle, Identifier> peerReviewImpl,
      IdentityTransport<Handle, Identifier> transport,
      PeerInfoStore<Handle, Identifier> infoStore, SecureHistory history,
      AuthenticatorStore<Identifier> authOutStore, Object auditProtocol,
      CommitmentProtocol<Handle, Identifier> commitmentProtocol,
      PeerReviewCallback<Handle, Identifier> callback) {
    this.peerreview = peerReviewImpl;
    this.transport = transport;
    this.infoStore = infoStore;
    this.history = history;
    this.authOutStore = authOutStore;
    this.auditProtocol = auditProtocol;
    this.commitmentProtocol = commitmentProtocol;
    this.app = callback;
    
    this.logger = peerreview.getEnvironment().getLogManager().getLogger(ChallengeResponseProtocolImpl.class, null);
  }
  
  /**
   * Called when another node sends us a challenge. If the challenge is valid, we respond. 
   */
  public void handleChallenge(Handle source, ByteBuffer buf, Map<String, Object> options) throws IOException {
    ChallengeMessage<Identifier> challenge;
    try {
      SimpleInputBuffer sib = new SimpleInputBuffer(buf);
      challenge = new ChallengeMessage<Identifier>(sib,peerreview.getIdSerializer(),peerreview.getEvidenceSerializer());
    } catch (IOException ioe) {
      if (logger.level <= Logger.WARNING) logger.log("Frivolous challenge from "+source);
      throw ioe;
    }
    
    short type = challenge.getChallengeType(); 
    switch (type) {

      /* AUDIT challenges: We respond with a serialized log snippet */
        
      case CHAL_AUDIT:
      {
        ChallengeAudit audit = (ChallengeAudit)challenge.getChallenge();
        /* Some sanity checking */
      
//        if (bodyLen != (1+2*authenticatorSizeBytes)) {
//          if (logger.level <= Logger.WARNING) logger.log("Received an AUDIT challenge with an invalid length (%d)", bodyLen);
//          return;
//        }
//        
//        unsigned char flags = body[0];
//        long long seqFrom = *(long long*)&body[1];
//        long long seqTo = *(long long*)&body[1+authenticatorSizeBytes];
        byte flags = audit.flags;
        long seqFrom = audit.from.getSeq();
        long seqTo = audit.to.getSeq();
        if (logger.level <= Logger.FINER) logger.log(
            "Received an AUDIT challenge for ["+seqFrom+","+seqTo+"] from "+source+
            " (eseq="+challenge.evidenceSeq+", flags="+audit.flags+")");
        
        if (seqTo < seqFrom) {
          if (logger.level <= Logger.WARNING) logger.log("Received an AUDIT challenge with seqTo<seqFrom:"+seqTo+"<"+seqFrom);
          return;
        }
        
        if ((seqFrom < history.getBaseSeq()) || (seqTo > history.getLastSeq())) {
          if (logger.level <= Logger.WARNING) logger.log(
              "Received an AUDIT whose range ["+seqFrom+"-"+seqTo+"] is outside our history range ["+
              history.getBaseSeq()+"-"+history.getLastSeq()+"]");
          return;
        }
         
        /* If the challenge asks for a snippet that starts with a checkpoint (FLAG_INCLUDE_CHECKPOINT set),
           we look up the last such entry; otherwise we start at the specified sequence number */
        
        short[] chkpointTypes = new short[]{ EVT_CHECKPOINT, EVT_INIT };
        long idxFrom = ((flags & FLAG_INCLUDE_CHECKPOINT) == FLAG_INCLUDE_CHECKPOINT) ? history.findLastEntry(chkpointTypes, seqFrom) : history.findSeq(seqFrom);
        long idxTo = history.findSeq(seqTo);
  
        if ((idxFrom >= 0) && (idxTo >= 0)) {
          IndexEntry fromEntry = history.statEntry(idxFrom);
          if (fromEntry == null) throw new RuntimeException("Cannot get beginSeq during AUDIT challenge");
          short beginType = fromEntry.getType();
          long beginSeq = fromEntry.getSeq();
//            
          /* Log entries with consecutive sequence numbers correspond to events that have happened
             'at the same time' (i.e. without updating the clock). In order to be able to replay
             this properly, we need to start at the first such event, which we locate by rounding
             down to the closest multiple of 1000. */
            
          if (((beginSeq % 1000) > 0) && !((flags & FLAG_INCLUDE_CHECKPOINT) == FLAG_INCLUDE_CHECKPOINT)) {
            beginSeq -= (beginSeq % 1000);
            idxFrom = history.findSeq(beginSeq);
            if (logger.level <= Logger.FINEST) logger.log("Moving beginSeq to "+beginSeq+" (idx="+idxFrom+")");
            assert(idxFrom >= 0);
          }
          
          /* Similarly, we should always 'round up' to the next multiple of 1000 */
          
          long followingSeq;
          
          IndexEntry toEntry;
          while ((toEntry = history.statEntry(idxTo+1)) != null) {
            followingSeq = toEntry.getSeq();
            if ((followingSeq % 1000) == 0)
              break;
            
            idxTo++;
            if (logger.level <= Logger.FINEST) logger.log( "Advancing endSeq past "+followingSeq+" (idx="+idxTo+")");
          }
                    
//          unsigned char endType;
          if ((toEntry = history.statEntry(idxTo)) == null) { 
            throw new RuntimeException("Cannot get endType during AUDIT challenge "+idxTo);
          }
          short endType = toEntry.getType();
          if (endType == EVT_RECV) {
            idxTo++;
          }
  
          /* Serialize the requested log snippet */
        
          HashPolicy hashPolicy = new ChallengeHashPolicy(flags, challenge.originator, peerreview.getIdSerializer());
          throw new RuntimeException("todo: implement.");
//          FILE *outfile = tmpfile();
//          if (history->serializeRange(idxFrom, idxTo, hashPolicy, outfile)) {
//            int size = ftell(outfile);
//            
//            unsigned char extInfo[255];
//            unsigned int extInfoLen = extInfoPolicy ? extInfoPolicy->storeExtInfo(history, followingSeq, extInfo, sizeof(extInfo)) : 0;
//            if (extInfoLen < 0)
//              extInfoLen = 0;
//            if (extInfoLen > sizeof(extInfo))
//              extInfoLen = sizeof(extInfo);
//            
//            unsigned int maxHeaderLen = 1+2*MAX_ID_SIZE+sizeof(long long)+1+MAX_HANDLE_SIZE+sizeof(long long)+1+extInfoLen;
//            unsigned char *buffer = (unsigned char*) malloc(maxHeaderLen+size);
//            unsigned int headerLen = 0;
//            
//            /* Put together a RESPONSE message */
//            
//            writeByte(buffer, &headerLen, MSG_RESPONSE);
//            writeBytes(buffer, &headerLen, originatorAsBytes, identifierSizeBytes);
//            transport->getLocalHandle()->getIdentifier()->write(buffer, &headerLen, maxHeaderLen);
//            writeLongLong(buffer, &headerLen, evidenceSeq);
//            writeByte(buffer, &headerLen, type);
//            peerreview->getLocalHandle()->write(buffer, &headerLen, maxHeaderLen);
//            writeLongLong(buffer, &headerLen, beginSeq);
//            writeByte(buffer, &headerLen, extInfoLen);
//            if (extInfoLen>0)
//              writeBytes(buffer, &headerLen, extInfo, extInfoLen);
//            assert(headerLen <= maxHeaderLen);
//  
//            fseek(outfile, 0, SEEK_SET);
//            fread(&buffer[headerLen], size, 1, outfile);
//  
//            /* ... and send it back to the challenger */
//  
//            if (logger.level <= Logger.FINER) logger.log( "Answering AUDIT challenge with %d-byte log snippet", size); 
//            peerreview->transmit(source, false, buffer, headerLen+size);
//            free(buffer);
//          } else {
//            if (logger.level <= Logger.WARNING) logger.log("Error accessing history");
//          }
//  
//          fclose(outfile);
//          delete hashPolicy;
        } else {
          if (logger.level <= Logger.WARNING) logger.log(
              "Cannot respond to AUDIT challenge ["+seqFrom+"-"+seqTo+",flags="+flags+
              "]; entries not found (iF="+idxFrom+"/iT="+idxTo+")");
        }
        break;
      }
      
      /* SEND challenges: We accept the message if necessary and then respond with an ACK. At this point,
         the statement protocol has already checked the signature and filed the authenticator. */
      
      case CHAL_SEND:
      {
        UserDataMessage<Handle> udm = (UserDataMessage<Handle>)challenge.getChallenge();        

        if (logger.level <= Logger.INFO) logger.log( "Received a SEND challenge");

        Tuple<AckMessage<Identifier>, Boolean> ret = commitmentProtocol.logMessageIfNew(udm);
        AckMessage<Identifier> response = ret.a();
        boolean loggedPreviously = ret.b();
        
        /* We ONLY deliver the message to the application if we haven't done so
           already (i.e. if it was logged previously) */
          
        if (!loggedPreviously) {
          if (logger.level <= Logger.FINER) logger.log( "Delivering message in CHAL_SEND "+udm);
          app.messageReceived(udm.getSenderHandle(), udm.getPayload(), options);
        }
  
        /* Put together a RESPONSE with an authenticator for the message in the challenge */

        ResponseMessage rMsg = new ResponseMessage(challenge.originator,peerreview.getLocalIdentifier(),challenge.evidenceSeq,CHAL_SEND,ret.a());

        /* ... and send it back to the challenger */
        
        if (logger.level <= Logger.FINER) logger.log( "Returning a  response");
        peerreview.transmit(source, rMsg.serialize(), null, options);        
        break;
      }
      
      /* These are all the challenges we know */
      
      default:
      {
        if (logger.level <= Logger.WARNING) logger.log("Unknown challenge #"+type+" from "+source);
        break;
      }
    }

  }


  /**
   * Looks up the first unanswered challenge to a SUSPECTED node, and sends it to that node 
   */
  public void challengeSuspectedNode(Handle target) {    
//    Identifier originator;
//    long evidenceSeq;
//    int evidenceLen;
    
    Identifier tIdentifier = peerreview.getIdentifierExtractor().extractIdentifier(target);
    EvidenceRecord<Handle, Identifier> record = infoStore.statFirstUnansweredChallenge(tIdentifier);
    if (record == null) {
      throw new RuntimeException("Node "+target+" is SUSPECTED, but I cannot retrieve an unanswered challenge?!?");      
    }
    
    try {
      /* Construct a CHALLENGE message ... */
      ChallengeMessage<Identifier> challenge = 
        new ChallengeMessage<Identifier>(
            record.getOriginator(),record.getTimeStamp(),
            infoStore.getEvidence(record.getOriginator(), tIdentifier, record.getTimeStamp())
            );
      /* ... and send it */
      peerreview.transmit(target, challenge.serialize(), null, null);
    } catch (IOException ioe) {
      throw new RuntimeException(ioe);
    }        
  }
  
  public void handleIncomingMessage(Handle source, UserDataMessage<Handle> message, Map<String, Object> options) throws IOException {
    int status = infoStore.getStatus(peerreview.getIdentifierExtractor().extractIdentifier(source));
    switch (status) {
      case STATUS_EXPOSED:
        if (logger.level <= Logger.FINE) logger.log("Got a user message from exposed node "+source+"; discarding");
        return;
      case STATUS_TRUSTED:
        commitmentProtocol.handleIncomingMessage(source, message, options);
        return;
    }

    assert(status == STATUS_SUSPECTED);
    
    /* If the status is SUSPECTED, we queue the message for later delivery */

    if (logger.level <= Logger.WARNING) logger.log("Incoming message from SUSPECTED node "+source+"; queueing and challenging the node");
    copyAndEnqueueTail(source, message, false);

    /* Furthermore, we must have an unanswered challenge, which we send to the remote node */

    challengeSuspectedNode(source);
  }

  private void copyAndEnqueueTail(Handle source,
      UserDataMessage<Handle> message, boolean b) {
    throw new RuntimeException("implement me");
  }

}
