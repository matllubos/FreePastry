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

import org.mpisws.p2p.transport.MessageCallback;
import org.mpisws.p2p.transport.MessageRequestHandle;
import org.mpisws.p2p.transport.TransportLayerCallback;
import org.mpisws.p2p.transport.peerreview.commitment.Authenticator;
import org.mpisws.p2p.transport.peerreview.commitment.AuthenticatorSerializer;
import org.mpisws.p2p.transport.peerreview.commitment.AuthenticatorStore;
import org.mpisws.p2p.transport.peerreview.identity.IdentityTransport;
import org.mpisws.p2p.transport.peerreview.identity.IdentityTransportCallback;
import org.mpisws.p2p.transport.peerreview.infostore.Evidence;
import org.mpisws.p2p.transport.peerreview.message.PeerReviewMessage;
import org.mpisws.p2p.transport.peerreview.message.UserDataMessage;
import org.mpisws.p2p.transport.util.Serializer;

import rice.environment.Environment;
import rice.p2p.commonapi.rawserialization.RawSerializable;

public interface PeerReview<Handle extends RawSerializable, Identifier extends RawSerializable> extends 
    IdentityTransportCallback<Handle, Identifier>, IdentityTransport<Handle, Identifier>, PeerReviewConstants {

  /**
   * Option should map to an int < 255 to record the relevant length of the message.
   */
  public static final String RELEVANT_LENGTH = "PeerReview_Relevant_length";  

  /**
   * -> Boolean, tell peer review to not bother committing this message.  Don't sign it, log it, expect an ack
   */
  public static final String DONT_COMMIT = "PeerReview_ignore_commit";  

  public static final byte PEER_REVIEW_PASSTHROUGH = 0;
  public static final byte PEER_REVIEW_COMMIT = 1;
  
  
  public Authenticator extractAuthenticator(Identifier id, long seq, short entryType, byte[] entryHash, byte[] hTopMinusOne, byte[] signature) throws IOException;
  public boolean addAuthenticatorIfValid(AuthenticatorStore<Identifier> store, Identifier subject, Authenticator auth);

  
  Environment getEnvironment();

  AuthenticatorSerializer getAuthenticatorSerializer();

  Serializer<Handle> getHandleSerializer();
  Serializer<Identifier> getIdSerializer();

  void challengeSuspectedNode(Handle h);

//  public MessageRequestHandle<Handle, PeerReviewMessage> transmit(Handle dest, boolean b, PeerReviewMessage message, MessageCallback<Handle, PeerReviewMessage> deliverAckToMe);
  
  public void transmit(Handle dest, 
      PeerReviewMessage message,
      MessageCallback<Handle, ByteBuffer> deliverAckToMe, 
      Map<String, Object> options);

  /**
   * Current time in millis, however, we depend on there being a timesource that is more discritized
   * than the "wall" clock.  It is only advanced on a timeout or a message receipt.
   * @return
   */
  long getTime();

  int getHashSizeInBytes();

  int getSignatureSizeInBytes();
  
  public IdentifierExtractor<Handle, Identifier> getIdentifierExtractor();

  public long getEvidenceSeq();
  
  /**
   * 
   * @param subject the "bad" guy
   * @param timestamp
   * @param evidence
   */
  public void sendEvidenceToWitnesses(Identifier subject, long timestamp, Evidence evidence);


  public PeerReviewCallback<Handle, Identifier> getApp();

}
