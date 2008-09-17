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
package org.mpisws.p2p.transport.peerreview;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;

import org.mpisws.p2p.transport.ErrorHandler;
import org.mpisws.p2p.transport.MessageCallback;
import org.mpisws.p2p.transport.MessageRequestHandle;
import org.mpisws.p2p.transport.P2PSocket;
import org.mpisws.p2p.transport.SocketCallback;
import org.mpisws.p2p.transport.SocketRequestHandle;
import org.mpisws.p2p.transport.TransportLayer;
import org.mpisws.p2p.transport.TransportLayerCallback;
import org.mpisws.p2p.transport.peerreview.commitment.Authenticator;
import org.mpisws.p2p.transport.peerreview.commitment.AuthenticatorSerializer;
import org.mpisws.p2p.transport.peerreview.commitment.AuthenticatorStore;
import org.mpisws.p2p.transport.peerreview.evidence.ProofInconsistent;
import org.mpisws.p2p.transport.peerreview.history.HashProvider;
import org.mpisws.p2p.transport.peerreview.identity.IdentityTransport;
import org.mpisws.p2p.transport.peerreview.infostore.Evidence;
import org.mpisws.p2p.transport.peerreview.infostore.PeerInfoStore;
import org.mpisws.p2p.transport.peerreview.message.PeerReviewMessage;
import org.mpisws.p2p.transport.peerreview.message.UserDataMessage;
import org.mpisws.p2p.transport.util.Serializer;

import rice.environment.Environment;
import rice.environment.logging.Logger;
import rice.p2p.commonapi.rawserialization.RawSerializable;
import rice.p2p.util.rawserialization.SimpleInputBuffer;
import rice.p2p.util.rawserialization.SimpleOutputBuffer;

/**
 * 
 * @author Jeff Hoye
 *
 * @param <Handle> (Usually a NodeHandle)
 * @param <Identifier> (Permanent Identifier), can get an Identifier from a Handle
 */
public class PeerReviewImpl<Handle extends RawSerializable, Identifier extends RawSerializable> implements 
  TransportLayer<Handle, ByteBuffer>,
  TransportLayerCallback<Handle, ByteBuffer>,
  PeerReview<Handle, Identifier> {

  TransportLayer<Handle, ByteBuffer> tl;
  TransportLayerCallback<Handle, ByteBuffer> callback;

  Environment env;
  Serializer<Identifier> idSerializer;
  Serializer<Handle> handleSerializer;
  AuthenticatorSerializer authenticatorSerialilzer;
  AuthenticatorStore<Identifier> authOutStore;
  IdentityTransport<Handle, Identifier> transport;
  PeerInfoStore<Handle, Identifier> infoStore;
  
  IdentifierExtractor<Handle, Identifier> identifierExtractor;
  Logger logger;

  public PeerReviewImpl(TransportLayer<Handle, ByteBuffer> tl,
      Environment env, Serializer<Handle> handleSerializer,
      Serializer<Identifier> idSerializer,      
      IdentifierExtractor<Handle, Identifier> identifierExtractor,
      AuthenticatorSerializer authenticatorSerialilzer) {
    super();
    this.tl = tl;
    this.env = env;
    this.logger = env.getLogManager().getLogger(PeerReviewImpl.class, null);
    this.idSerializer = idSerializer;
    this.handleSerializer = handleSerializer;
    this.identifierExtractor = identifierExtractor;
    this.authenticatorSerialilzer = authenticatorSerialilzer; 
  }
    
  public SocketRequestHandle<Handle> openSocket(Handle i, SocketCallback<Handle> deliverSocketToMe, Map<String, Object> options) {
    return tl.openSocket(i, deliverSocketToMe, options);
  }

  public void incomingSocket(P2PSocket<Handle> s) throws IOException {
    callback.incomingSocket(s);
  }

  public MessageRequestHandle<Handle, ByteBuffer> sendMessage(Handle i, ByteBuffer m, MessageCallback<Handle, ByteBuffer> deliverAckToMe, Map<String, Object> options) {
    return tl.sendMessage(i, m, deliverAckToMe, options);
  }

  public void messageReceived(Handle i, ByteBuffer m, Map<String, Object> options) throws IOException {
    callback.messageReceived(i, m, options);
  }
  
  public void acceptMessages(boolean b) {
    tl.acceptMessages(b);
  }

  public void acceptSockets(boolean b) {
    tl.acceptSockets(b);
  }

  public Handle getLocalIdentifier() {
    return tl.getLocalIdentifier();
  }

  public void setCallback(TransportLayerCallback<Handle, ByteBuffer> callback) {
    this.callback = callback;
  }

  public void setErrorHandler(ErrorHandler<Handle> handler) {
    // TODO Auto-generated method stub
    
  }

  public void destroy() {
    tl.destroy();
  }

  public AuthenticatorSerializer getAuthenticatorSerializer() {
    return authenticatorSerialilzer;
  }

  public Environment getEnvironment() {
    return env;
  }

  public Serializer<Identifier> getIdSerializer() {
    return idSerializer;
  }

  public long getTime() {
    return env.getTimeSource().currentTimeMillis();
  }
  
  /** 
   * A helper function that extracts an authenticator from an incoming message and adds it to our local store. 
   */
  public Authenticator extractAuthenticator(Identifier id, long seq, short entryType, byte[] entryHash, byte[] hTopMinusOne, byte[] signature) throws IOException {
//    *(long long*)&authenticator[0] = seq;
    
    SimpleOutputBuffer sob = new SimpleOutputBuffer();
    sob.writeLong(seq);
    sob.writeShort(entryType);
    sob.write(hTopMinusOne);
    sob.write(entryHash);
    byte[] hash = transport.hash(sob.getByteBuffer());
    Authenticator ret = new Authenticator(seq,hash,signature);
    if (addAuthenticatorIfValid(authOutStore, id, ret)) {
      return ret;
    }
    return null;
  }

  /**
   * Helper function called internally from the library. It takes a (potentially
   * new) authenticator and adds it to our local store if (a) it hasn't been
   * recorded before, and (b) its signature is valid.
   */
  boolean addAuthenticatorIfValid(AuthenticatorStore<Identifier> store, Identifier subject, Authenticator auth) {
    // see if we can exit early
    Authenticator existingAuth = store.statAuthenticator(subject, auth.getSeq());
    if (existingAuth != null) {       
      /* If yes, then it should be bit-wise identical to the new one */
    
      if (auth.equals(existingAuth)) {
        return true;
      }
    }
   
     /* maybe the new authenticator is a forgery? Let's check the signature! 
        If the signature doesn't check out, then we simply discard the 'authenticator' and
        move on. */
     assert(transport.hasCertificate(subject));

     try {
       SimpleOutputBuffer sob = new SimpleOutputBuffer();
       sob.writeLong(auth.getSeq());
       sob.write(auth.getHash());
       byte[] signedHash = transport.hash(sob.getByteBuffer());
       transport.verify(subject, signedHash, 0, signedHash.length, auth.getSignature(), 0, auth.getSignature().length);
       
//    char buf1[1000];
       
       /* Do we already have an authenticator with the same sequence number and from the same node? */
       if (existingAuth != null) {
         /* The signature checks out, so the node must have signed two different authenticators
         with the same sequence number! This is a proof of misbehavior, and we must
         notify the witness set! */
             if (logger.level < Logger.WARNING) logger.log("Authenticator conflict for "+subject+" seq #"+auth.getSeq());
             if (logger.level < Logger.FINE) logger.log("Existing: ["+existingAuth+"]");
             if (logger.level < Logger.FINE) logger.log("New:      ["+auth+"]");
             
             /**
              * PROOF_INCONSISTENT
              * byte type = PROOF_INCONSISTENT
              * authenticator auth1
              * char whichInconsistency   // 0=another auth, 1=a log snippet
              * -----------------------
              * authenticator auth2       // if whichInconsistency==0
              * -----------------------
              * long long firstSeq        // if whichInconsistency==1
              * hash baseHash
              * [entries]
              */
             ProofInconsistent proof = new ProofInconsistent(auth,existingAuth);
        long evidenceSeq = getEvidenceSeq();
        infoStore.addEvidence(identifierExtractor.extractIdentifier(transport.getLocalIdentifier()), subject, evidenceSeq, proof);
        sendEvidenceToWitnesses(subject, evidenceSeq, proof);
         return false;
       }
       
     
       /* We haven't seen this authenticator... Signature is ok, so we keep the new authenticator in our store. */  
       store.addAuthenticator(subject, auth);
       return true;
       
    } catch (Exception e) {
      return false;
    }
  }
   
  /* Gets a fresh, unique sequence number for evidence */
  long nextEvidenceSeq = 0L;
  public long getEvidenceSeq() {
    if (nextEvidenceSeq < getTime()) {
      nextEvidenceSeq = getTime();
    }
    return nextEvidenceSeq++;
  }

  public Serializer<Handle> getHandleSerializer() {
    return handleSerializer;
  }

  public int getHashSizeInBytes() {
    return transport.getHashSizeBytes();
  }

  public int getSignatureSizeInBytes() {
    return transport.signatureSizeInBytes();
  }

  public IdentifierExtractor<Handle, Identifier> getIdentifierExtractor() {
    return identifierExtractor;
  }

  public void challengeSuspectedNode(Handle h) {
    throw new RuntimeException("todo: implement");
  }

  public void transmit(Handle dest, boolean b, PeerReviewMessage message) {
    throw new RuntimeException("todo: implement");
  }

  /**
   * Called internally by other classes if they have found evidence against one of our peers.
   * We ask the EvidenceTransferProtocol to send it to the corresponding witness set. 
   */
  public void sendEvidenceToWitnesses(Identifier subject, long timestamp,
      Evidence evidence) {
    throw new RuntimeException("todo: implement.");
//    unsigned int accusationMaxlen = 1 + 2*MAX_ID_SIZE + sizeof(long long) + evidenceLen + signatureSizeBytes;
//    unsigned char *accusation = (unsigned char*) malloc(accusationMaxlen);
//    unsigned int accusationLen = 0;
//    char buf1[256];
//  
//    accusation[accusationLen++] = MSG_ACCUSATION;
//    transport->getLocalHandle()->getIdentifier()->write(accusation, &accusationLen, accusationMaxlen);
//    subject->write(accusation, &accusationLen, accusationMaxlen);
//    writeLongLong(accusation, &accusationLen, evidenceSeq);
//    memcpy(&accusation[accusationLen], evidence, evidenceLen);
//    accusationLen += evidenceLen;
//   
//    plog(2, "Relaying evidence to <%s>'s witnesses", subject->render(buf1));
//    evidenceTransferProtocol->sendMessageToWitnesses(subject, false, accusation, accusationLen);
//  
//    free(accusation);
  }


}
