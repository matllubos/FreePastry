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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.mpisws.p2p.transport.peerreview.PeerReview;
import org.mpisws.p2p.transport.peerreview.PeerReviewImpl;
import org.mpisws.p2p.transport.peerreview.Verifier;
import org.mpisws.p2p.transport.peerreview.commitment.Authenticator;
import org.mpisws.p2p.transport.peerreview.commitment.AuthenticatorStore;
import org.mpisws.p2p.transport.peerreview.evidence.AuditResponse;
import org.mpisws.p2p.transport.peerreview.evidence.ChallengeAudit;
import org.mpisws.p2p.transport.peerreview.evidence.EvidenceTransferProtocol;
import org.mpisws.p2p.transport.peerreview.history.SecureHistory;
import org.mpisws.p2p.transport.peerreview.identity.IdentityTransport;
import org.mpisws.p2p.transport.peerreview.infostore.Evidence;
import org.mpisws.p2p.transport.peerreview.infostore.PeerInfoStore;
import org.mpisws.p2p.transport.peerreview.message.ChallengeMessage;

import rice.environment.logging.Logger;
import rice.p2p.commonapi.rawserialization.RawSerializable;
import rice.selector.TimerTask;
import static org.mpisws.p2p.transport.peerreview.Basics.renderStatus;

public class AuditProtocolImpl<Handle extends RawSerializable, Identifier extends RawSerializable> implements AuditProtocol<Handle, Identifier> {

  PeerReview<Handle, Identifier> peerreview;
  SecureHistory history; 
  PeerInfoStore<Handle, Identifier> infoStore;
  AuthenticatorStore<Identifier> authInStore;
  IdentityTransport<Handle, Identifier> transport;
  AuthenticatorStore<Identifier> authOutStore;
  EvidenceTransferProtocol<Handle, Identifier> evidenceTransferProtocol;
  AuthenticatorStore<Identifier> authCacheStore;
  
  /**
   * Here we remember calls to investigate() that have not been resolved yet 
   */
  Map<Handle,ActiveInvestigationInfo<Handle>> activeInvestigation = new HashMap<Handle, ActiveInvestigationInfo<Handle>>();

  /**
   * Here we remember calls to investigate() that have not been resolved yet 
   */
  Map<Handle,ActiveAuditInfo<Handle, Identifier>> activeAudit = new HashMap<Handle, ActiveAuditInfo<Handle,Identifier>>();

  int logDownloadTimeout;
  boolean replayEnabled;
  long lastAuditStarted;
  long auditIntervalMillis;
  
  /**
   * Null if there is no current timer.
   */
  protected TimerTask auditTimer;
  protected TimerTask progressTimer;

  Logger logger;
  
  public AuditProtocolImpl(PeerReview<Handle, Identifier> peerreview,
      SecureHistory history, PeerInfoStore<Handle, Identifier> infoStore,
      AuthenticatorStore<Identifier> authInStore,
      IdentityTransport<Handle, Identifier> transport,
      AuthenticatorStore<Identifier> authOutStore,
      EvidenceTransferProtocol<Handle, Identifier> evidenceTransferProtocol,
      AuthenticatorStore<Identifier> authCacheStore) {
    this.logger = peerreview.getEnvironment().getLogManager().getLogger(AuditProtocolImpl.class, null);
    this.peerreview = peerreview;
    this.history = history;
    this.infoStore = infoStore;
    this.authInStore = authInStore;
    this.transport = transport;
    this.authOutStore = authOutStore;
    this.evidenceTransferProtocol = evidenceTransferProtocol;
    this.authCacheStore = authCacheStore;
    
    this.progressTimer = null;
    this.logDownloadTimeout = DEFAULT_LOG_DOWNLOAD_TIMEOUT;
    this.replayEnabled = true;
    this.lastAuditStarted = peerreview.getTime();
    this.auditIntervalMillis = DEFAULT_AUDIT_INTERVAL_MILLIS;
    
    auditTimer = peerreview.getEnvironment().getSelectorManager().schedule(new TimerTask() {
      @Override
      public void run() {
        auditsTimerExpired();
      }},lastAuditStarted+auditIntervalMillis);
  }

  
  public void setLogDownloadTimeout(int timeoutMicros) { 
    this.logDownloadTimeout = timeoutMicros; 
  }
  
  public void disableReplay() { 
    this.replayEnabled = false; 
  }

  /**
   *  Starts to audit a node 
   */
  void beginAudit(Handle target, Authenticator authFrom, Authenticator authTo, byte needPrevCheckpoint, boolean replayAnswer) {
    long evidenceSeq = peerreview.getEvidenceSeq();

    /* Put together an AUDIT challenge */
    ChallengeAudit audit = new ChallengeAudit(needPrevCheckpoint,authFrom,authTo);    
    ChallengeMessage<Identifier> auditRequest = new ChallengeMessage<Identifier>(peerreview.getLocalId(),evidenceSeq,audit);

    /* Create an entry in our audit buffer */

    ActiveAuditInfo<Handle, Identifier> aai = new ActiveAuditInfo<Handle, Identifier>(
        target,replayAnswer,false,peerreview.getTime()+logDownloadTimeout,auditRequest,evidenceSeq,null);
    activeAudit.put(target,aai);
    
    /* Send the AUDIT challenge to the target node */

    if (logger.level <= Logger.FINE) logger.log("Sending AUDIT request to "+aai.target+" (range="+authFrom.getSeq()+"-"+authTo.getSeq()+",eseq="+evidenceSeq+")");
    peerreview.transmit(target, auditRequest, null, null);
  }

  public void startAudits() {
    /* Find out which nodes we are currently witnessing */

//    Collection<Handle> buffer = new ArrayList<Handle>;
    Collection<Handle> buffer = peerreview.getApp().getMyWitnessedNodes(); //app->getMyWitnessedNodes(buffer, MAX_WITNESSED_NODES);
    
    /* For each of these nodes ... */
    
    for (Handle h : buffer) {
      Identifier i = peerreview.getIdentifierExtractor().extractIdentifier(h);
      /* If the node is not trusted, we don't audit */
    
      int status = infoStore.getStatus(i);
      if (status != STATUS_TRUSTED) {
        if (logger.level <= Logger.FINE) logger.log("Node "+h+" is "+renderStatus(status)+"; skipping audit");
        continue;
      }
    
      /* If a previous audit of this node is still in progress, we skip this round */
          
      if (!activeAudit.containsKey(h)) {
          
        /* Retrieve the node's newest and last-checked authenticators. Note that we need
           at least two distinct authenticators to be able to audit a node. */
          
        Authenticator authFrom;
        Authenticator authTo;
        boolean haveEnoughAuthenticators = true;
        byte needPrevCheckpoint = 0;

        if (logger.level <= Logger.INFO) logger.log("Starting to audit "+h);

        if ((authFrom = infoStore.getLastCheckedAuth(i)) == null) {
          if ((authFrom = authInStore.getOldestAuthenticator(i)) != null) {
            if (logger.level <= Logger.FINE) logger.log("We haven't audited this node before; using oldest authenticator");
            needPrevCheckpoint = 1;
          } else {
            if (logger.level <= Logger.FINE) logger.log("We don't have any authenticators for this node; skipping this audit");
            haveEnoughAuthenticators = false;
          }
        }

        if ((authTo = authInStore.getMostRecentAuthenticator(i)) == null) {
          if (logger.level <= Logger.FINE) logger.log("No recent authenticator; skipping this audit");
          haveEnoughAuthenticators = false;
        }
          
        if (haveEnoughAuthenticators && authFrom.getSeq()>authTo.getSeq()) {
          if (logger.level <= Logger.FINE) logger.log("authFrom>authTo; skipping this audit");
          haveEnoughAuthenticators = false; 
        }
          
        /* Add an entry to our table of ongoing audits. This entry will periodically
           be checked by cleanupAudits() */
          
        if (haveEnoughAuthenticators) {
          beginAudit(h, authFrom, authTo, needPrevCheckpoint, true);
        }
      } else {
        if (logger.level <= Logger.WARNING) logger.log("Node "+h+" is already being audited; skipping");
      }
    }
    
    /* Make sure that makeProgressOnAudits() is called regularly */
    
    if (progressTimer == null) {
      progressTimer = peerreview.getEnvironment().getSelectorManager().schedule(new TimerTask() {

        @Override
        public void run() {
          makeProgressTimerExpired();
        }}, peerreview.getTime() + PROGRESS_INTERVAL_MILLIS);
    }    
  }

  /**
   * Called periodically to check if all audits have finished. An audit may not
   * finish if either (a) the target does not respond, or (b) the target's
   * response is discarded because it is malformed. In either case, we report
   * the failed AUDIT challenge to the witnesses.
   */
  void cleanupAudits() {
    long now = peerreview.getTime();
   
    for (ActiveAuditInfo<Handle, Identifier> foo : activeAudit.values()) { 
      if ((now >= foo.currentTimeout) && !foo.isReplaying) {
        Identifier i = peerreview.getIdentifierExtractor().extractIdentifier(foo.target);
//        int headerLen = 1 + peerreview->getIdentifierSizeBytes() + sizeof(long long);
           
        if (logger.level <= Logger.WARNING) logger.log("No response to AUDIT request; filing as evidence "+foo.evidenceSeq);
        try {
          infoStore.addEvidence(peerreview.getLocalId(), i, foo.evidenceSeq, 
              foo.request.challenge);
        } catch (IOException ioe) {
          throw new RuntimeException(ioe);
        }
        peerreview.sendEvidenceToWitnesses(i, foo.evidenceSeq, foo.request.challenge);
  
        terminateAudit(foo.target);
      }          
    }
  }

  void terminateAudit(Handle h) {
    activeAudit.remove(h);
  }
  
  void terminateInvestigation(Handle h) {
    activeInvestigation.remove(h);
  }

  void makeProgressOnInvestigations() {
    long now = peerreview.getTime();
    
    for (ActiveInvestigationInfo<Handle> aii : activeInvestigation.values()) {
      if (aii.currentTimeout < now) {
        long authFromSeq = aii.authFrom!=null ? aii.authFrom.getSeq() : -1;
        long authToSeq = aii.authTo!=null ? aii.authTo.getSeq() : -1;
        if ((0<=authFromSeq) && (authFromSeq <= aii.since) && (aii.since < authToSeq)) {
          if (logger.level <= Logger.FINE) logger.log("Investigation of "+aii.target+" (since "+aii.since+") is proceeding with an audit");
          
//  #if 0
//          unsigned char lastCheckedAuth[authenticatorSizeBytes];
//          if (infoStore->getLastCheckedAuth(aii.target->getIdentifier(), lastCheckedAuth)) {
//            long long lastCheckedSeq = *(long long*)&lastCheckedAuth;
//            if (lastCheckedSeq > authFromSeq) {
//              if (logger.level <= Logger.FINER) logger.log("We already have the log up to %lld; starting investigation from there", lastCheckedSeq);
//              memcpy(aii.authFrom, lastCheckedAuth, authenticatorSizeBytes);
//              authFromSeq = lastCheckedSeq;
//            }
//          }
//  #endif
          
          if (authToSeq > authFromSeq) {
            if (logger.level <= Logger.FINER) logger.log("Authenticators: "+authFromSeq+"-"+authToSeq);
            beginAudit(aii.target, aii.authFrom, aii.authTo, FLAG_FULL_MESSAGES_SENDER, false);
            terminateInvestigation(aii.target);
          } else {
            if (logger.level <= Logger.WARNING) logger.log("Cannot start investigation; authTo<authFrom ?!? (since="+aii.since+", authFrom="+authFromSeq+", authTo="+authToSeq+")");
            terminateInvestigation(aii.target);
          }
        } else {
          if (logger.level <= Logger.FINE) logger.log("Retransmitting investigation requests for "+aii.target+" at "+aii.since);
          sendInvestigation(aii.target);
          aii.currentTimeout += INVESTIGATION_INTERVAL_MILLIS;
        }
      }
    }
  }

  private void sendInvestigation(Handle target) {
    // TODO Auto-generated method stub
    
  }


  void setAuditInterval(long newIntervalMillis) {
    auditIntervalMillis = newIntervalMillis;
    auditTimer.cancel();
    startAuditTimer();
  }


  /**
   * A periodic timer to audit all nodes for which we are a witness 
   */
  protected void auditsTimerExpired() {
    startAudits();
    lastAuditStarted = peerreview.getTime();
    startAuditTimer();
  }
  
  protected void startAuditTimer() {
    long nextTimeout = lastAuditStarted + (long)((500+(peerreview.getRandomSource().nextInt(1000)))*0.001*auditIntervalMillis);
    if (nextTimeout <= peerreview.getTime()) {
      nextTimeout = peerreview.getTime() + 1;
    }
    auditTimer = peerreview.getEnvironment().getSelectorManager().schedule(new TimerTask() {
    
      @Override
      public void run() {
        auditsTimerExpired();
      }
    
    },nextTimeout);    
  }
  
  /**
   * While some audits haven't finished, we must call makeProgress() regularly 
   */
  protected void makeProgressTimerExpired() {
    progressTimer.cancel();
    cleanupAudits();
    makeProgressOnInvestigations();
    if (progressTimer == null && ((activeAudit.size() > 0) || (activeInvestigation.size() > 0))) {
      progressTimer = peerreview.getEnvironment().getSelectorManager().schedule(new TimerTask() {
        @Override
        public void run() {
          makeProgressTimerExpired();
        }        
      }, peerreview.getTime() + PROGRESS_INTERVAL_MILLIS);
    }
  }
  
  /**
   * Called by the challenge/response protocol if we have received a response to
   * an AUDIT challenge. At this point, we already know that we have all the
   * necessary certificates (because of the statement protocol).
   */
  public void processAuditResponse(Identifier subject, long timestamp, AuditResponse response) {
//    LogSnippit snippet = response.getLogSnippit();
//    ActiveAuditInfo<Handle, Identifier> aai = findOngoingAudit(subject, timestamp);
    
    /* Read the header of the log snippet */
    
//    int reqHdrSize = 1+peerreview->getIdentifierSizeBytes()+sizeof(long long);
//    ChallengeAudit challengeAudit = aai.request.challenge;
//    long fromSeq = challengeAudit.from.getSeq();
//    Authenticator toAuthenticator = challengeAudit.to; 
//    long toSeq = toAuthenticator.getSeq();
//    unsigned char currentNodeHash[hashSizeBytes];
//    unsigned char initialNodeHash[hashSizeBytes];
//    int readptr = 0;
//    Handle subjectHandle = peerreview->readNodeHandle(snippet, (unsigned int*)&readptr, snippetLen);
//    long long currentSeq = readLongLong(snippet, (unsigned int*)&readptr);
//    int extInfoLen = snippet[readptr++];
//    unsigned char *extInfo = &snippet[readptr];
//    readptr += extInfoLen;
//    
//    long long initialCurrentSeq = currentSeq;
//    memcpy(currentNodeHash, &snippet[readptr], hashSizeBytes);
//    memcpy(initialNodeHash, &snippet[readptr], hashSizeBytes);
//    readptr += hashSizeBytes;
//    int initialReadPtr = readptr;
//    char buf1[200];
//    
    
    /* Retrieve all the authenticators we have for this node */
    
//    unsigned char *auths = NULL;
//    int numAuths = authInStore->numAuthenticatorsFor(subject, fromSeq, toSeq);
//    if (numAuths > 0) {
//      auths = (unsigned char*) malloc(numAuths * authenticatorSizeBytes);
//      int ret = authInStore->getAuthenticators(subject, auths, numAuths * authenticatorSizeBytes, fromSeq, toSeq);
//      assert(ret == (numAuths * authenticatorSizeBytes));
//    }

//    /* We have to keep a small fraction of the authenticators around so we can later
//       answer AUTHREQ messages from other nodes. */
//
//    plog(2, "Checking of AUDIT response against %d authenticators [%lld-%lld]", numAuths, fromSeq, toSeq);
//
//    unsigned char mrauth[authenticatorSizeBytes];
//    long long mostRecentAuthInCache = -1;
//    if (authCacheStore->getMostRecentAuthenticator(subject, mrauth)) 
//      mostRecentAuthInCache = *(long long*)&mrauth;
//      
//    for (int i=numAuths-1; i>=0; i--) {
//      unsigned char *thisAuth = &auths[authenticatorSizeBytes * i];
//      long long thisSeq = *(long long*)thisAuth;
//      if (thisSeq > (mostRecentAuthInCache + (AUTH_CACHE_INTERVAL*1000LL))) {
//        plog(3, "Caching auth %lld for %s", thisSeq, subject->render(buf1));
//        authCacheStore->addAuthenticator(subject, thisAuth);
//        mostRecentAuthInCache = thisSeq;
//      }
//    }
//
//    /* We read the entries one by one, calculating the node hashes as we go, and we compare
//       the node hashes to our (sorted) list of authenticators. If there is any divergence,
//       we have proof that the node is misbehaving. */
//
//    int authPtr = numAuths - 1;
//    unsigned char *nextAuth = (authPtr<0) ? NULL : &auths[authPtr*authenticatorSizeBytes];
//    long long nextAuthSeq = (authPtr<0) ? -1 : *(long long*)nextAuth;
//    plog(3, "  NA #%d %lld", authPtr, nextAuthSeq);
//
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
//
//      vlog(3, "[2] Entry type %d, size=%d, seq=%lld%s", entryType, entrySize, currentSeq, entryIsHashed ? " (hashed)" : "");
//
//      const unsigned char *entry = &snippet[readptr];
//
//      /* Calculate the node hash from the entry */
//
//      unsigned char contentHash[hashSizeBytes];
//      if (entryIsHashed)
//        memcpy(contentHash, entry, hashSizeBytes);
//      else
//        transport->hash(contentHash, entry, entrySize);
//
//      transport->hash(currentNodeHash, (const unsigned char*)&currentSeq, sizeof(currentSeq), &entryType, sizeof(entryType), currentNodeHash, hashSizeBytes, contentHash, hashSizeBytes);
//
//      char buf1[256];
//      vlog(4, "NH [%s]", renderBytes(currentNodeHash, hashSizeBytes, buf1));
//
//      /* If we have an authenticator for this entry (matched by sequence number), the hash in the
//         authenticator must match the node hash. If not, we have a proof of misbehavior. */
//
//      if (authPtr >= 0) {
//        if (currentSeq == nextAuthSeq) {
//          if (memcmp(currentNodeHash, &nextAuth[sizeof(long long)], hashSizeBytes)) {
//            warning("Found a divergence for node <%08X>'s authenticator #%lld", subject, currentSeq);
//            panic("Cannot file PROOF yet");
//          }
//
//          plog(4,"Authenticator verified OK");
//
//          authPtr --;
//          nextAuth = (authPtr<0) ? NULL : &auths[authPtr*authenticatorSizeBytes];
//          nextAuthSeq = (authPtr<0) ? -1 : *(long long*)nextAuth;
//          plog(4, "NA #%d %lld", authPtr, nextAuthSeq);
//        } else if (currentSeq > nextAuthSeq) {
//          warning("Node %s is trying to hide authenticator #%lld", subject->render(buf1), nextAuthSeq);
//          panic("Cannot file PROOF yet");
//        }
//      }
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
//      assert(readptr <= snippetLen);
//    }
//
//    /* All these authenticators for this segment checked out okay. We don't need them any more,
//       so we can remove them from our local store. */
//
//    plog(2, "All authenticators in range [%lld,%lld] check out OK; flushing", fromSeq, toSeq);
//  #warning must check the old auths; flush from fromSeq only!
//    authInStore->flushAuthenticatorsFor(subject, LONG_LONG_MIN, toSeq);
//    if (auths)
//      free(auths);
//
//    /* Find out if the log snipped is 'useful', i.e. if we can append it to our local history */
//
//    char namebuf[200];
//    infoStore->getHistoryName(subject, namebuf);
//    SecureHistory *subjectHistory = SecureHistory::open(namebuf, "w", transport);
//    
//    bool logCanBeAppended = false;
//    long long topSeq = 0;
//    if (subjectHistory) {
//      subjectHistory->getTopLevelEntry(NULL, &topSeq);
//      if (topSeq >= fromSeq) 
//        logCanBeAppended = true;
//    } else {
//      logCanBeAppended = true;
//    }
//
//    /* If it should not be replayed (e.g. because it was retrieved during an investigation), 
//       we stop here */
//    
//    if (!aai.shouldBeReplayed/* && !logCanBeAppended*/) {
//      plog(2, "This audit response does not need to be replayed; discarding");
//      delete subjectHandle;
//      if (subjectHistory)
//        delete subjectHistory;
//      terminateAudit(idx);
//      return;
//    }
//
//    /* Add entries to our local copy of the subject's history */
//    
//    plog(2, "Adding entries in snippet to log '%s'", namebuf);
//    if (!logCanBeAppended)
//      panic("Cannot append snippet to local copy of node's history; there appears to be a gap (%lld-%lld)!", topSeq, fromSeq);
//    
//    if (!subjectHistory) {
//      subjectHistory = SecureHistory::create(namebuf, initialCurrentSeq-1, initialNodeHash, transport);
//      if (!subjectHistory)
//        panic("Cannot create subject history: '%s'", namebuf);
//    }
//    
//    long long firstNewSeq = currentSeq - (currentSeq%1000) + 1000;
//    EvidenceTool::appendSnippetToHistory(&snippet[initialReadPtr], snippetLen - initialReadPtr, subjectHistory, peerreview, initialCurrentSeq, firstNewSeq);
//  #warning need to verify older authenticators against history
//
//    if (replayEnabled) {
//
//      /* We need to replay the log segment, so let's find the last checkpoint */
//
//      unsigned char markerTypes[1] = { EVT_CHECKPOINT };
//      int lastCheckpointIdx = subjectHistory->findLastEntry(markerTypes, 1, fromSeq);
//
//      plog(4, "LastCheckpointIdx=%d (up to %lld)", lastCheckpointIdx, fromSeq);
//
//      if (lastCheckpointIdx < 0) {
//        warning("Cannot find last checkpoint in subject history %s", namebuf);
//    //    if (subjectHistory->getNumEntries() >= MAX_ENTRIES_BETWEEN_CHECKPOINTS)
//    //      panic("TODO: Must generate proof due to lack of checkpoints");
//
//        delete subjectHistory;
//        delete subjectHandle;
//        terminateAudit(idx);
//        return;
//      }
//
//      /* Create a Verifier instance and get a Replay instance from the application */
//
//      Verifier *verifier = new Verifier(peerreview, subjectHistory, subjectHandle, signatureSizeBytes, hashSizeBytes, lastCheckpointIdx, fromSeq/1000, extInfo, extInfoLen);
//      PeerReviewCallback *replayApp = app->getReplayInstance(verifier);
//      if (!replayApp)
//        panic("Application returned NULL when getReplayInstance() was called");
//
//      verifier->setApplication(replayApp);
//
//      aai.verifier = verifier;
//      aai.isReplaying = true;
//
//      plog(1, "REPLAY ============================================");
//      plog(2, "Node being replayed: %s", subjectHandle->render(buf1));
//      plog(2, "Range in log       : %lld-%lld", fromSeq, toSeq);
//
//      /* Do the replay */
//
//      while (verifier->makeProgress());
//
//      bool verifiedOK = verifier->verifiedOK(); 
//      plog(1, "END OF REPLAY: %s =================", verifiedOK ? "VERIFIED OK" : "VERIFICATION FAILED");
//
//      /* If there was a divergence, we have a proof of misbehavior */
//
//      if (!verifiedOK) {
//        FILE *outfile = tmpfile();
//        if (!subjectHistory->serializeRange(lastCheckpointIdx, subjectHistory->getNumEntries()-1, NULL, outfile))
//          panic("Cannot serialize range for PROOF");
//
//        int snippet2size = ftell(outfile);
//        long long lastCheckpointSeq;
//        if (!subjectHistory->statEntry(lastCheckpointIdx, &lastCheckpointSeq, NULL, NULL, NULL, NULL))
//          panic("Cannot stat checkpoint entry");
//
//        warning("Audit revealed a protocol violation; filing evidence (snippet from %lld)", lastCheckpointSeq);
//
//        unsigned int maxProofLen = 1+authenticatorSizeBytes+MAX_HANDLE_SIZE+sizeof(long long)+snippet2size;
//        unsigned char *proof = (unsigned char*)malloc(maxProofLen);
//        unsigned int proofLen = 0;
//        writeByte(proof, &proofLen, PROOF_NONCONFORMANT);
//        writeBytes(proof, &proofLen, toAuthenticator, authenticatorSizeBytes);
//        subjectHandle->write(proof, &proofLen, maxProofLen);
//        writeLongLong(proof, &proofLen, lastCheckpointSeq);
//        fseek(outfile, 0, SEEK_SET);
//        fread(&proof[proofLen], snippet2size, 1, outfile);
//        proofLen += snippet2size;
//        assert(proofLen <= maxProofLen);
//
//        long long evidenceSeq = peerreview->getEvidenceSeq();
//        infoStore->addEvidence(transport->getLocalHandle()->getIdentifier(), subject, evidenceSeq, proof, proofLen);
//        peerreview->sendEvidenceToWitnesses(subject, evidenceSeq, proof, proofLen);
//      } 
//    }
//
//    /* Terminate the audit, and remember the last authenticator for further reference */
//
//    plog(2, "Audit completed; terminating");  
//    infoStore->setLastCheckedAuth(aai.target->getIdentifier(), &aai.request[reqHdrSize+2+authenticatorSizeBytes]);
//    terminateAudit(idx);
//    delete subjectHandle;
//    delete subjectHistory;
  }

  public Evidence statOngoingAudit(Identifier subject, long evidenceSeq) {
    return null;
  }

}
