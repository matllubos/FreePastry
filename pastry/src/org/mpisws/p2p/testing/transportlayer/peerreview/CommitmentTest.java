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
package org.mpisws.p2p.testing.transportlayer.peerreview;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SignatureException;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;

import org.mpisws.p2p.transport.ErrorHandler;
import org.mpisws.p2p.transport.MessageCallback;
import org.mpisws.p2p.transport.MessageRequestHandle;
import org.mpisws.p2p.transport.SocketCallback;
import org.mpisws.p2p.transport.SocketRequestHandle;
import org.mpisws.p2p.transport.TransportLayerCallback;
import org.mpisws.p2p.transport.peerreview.IdentifierExtractor;
import org.mpisws.p2p.transport.peerreview.PeerReview;
import org.mpisws.p2p.transport.peerreview.PeerReviewCallback;
import org.mpisws.p2p.transport.peerreview.commitment.Authenticator;
import org.mpisws.p2p.transport.peerreview.commitment.AuthenticatorSerializer;
import org.mpisws.p2p.transport.peerreview.commitment.AuthenticatorStore;
import org.mpisws.p2p.transport.peerreview.commitment.CommitmentProtocolImpl;
import org.mpisws.p2p.transport.peerreview.history.SecureHistory;
import org.mpisws.p2p.transport.peerreview.history.SecureHistoryImpl;
import org.mpisws.p2p.transport.peerreview.history.logentry.IndexEntryFactoryImpl;
import org.mpisws.p2p.transport.peerreview.identity.IdentityTransport;
import org.mpisws.p2p.transport.peerreview.identity.UnknownCertificateException;
import org.mpisws.p2p.transport.peerreview.infostore.Evidence;
import org.mpisws.p2p.transport.peerreview.infostore.PeerInfoStore;
import org.mpisws.p2p.transport.peerreview.message.PeerReviewMessage;
import org.mpisws.p2p.transport.util.Serializer;

import rice.Continuation;
import rice.environment.Environment;
import rice.p2p.commonapi.Cancellable;
import rice.p2p.commonapi.rawserialization.OutputBuffer;
import rice.p2p.commonapi.rawserialization.RawSerializable;
import rice.p2p.util.RandomAccessFileIOBuffer;

public class CommitmentTest {
 
  
  static class HandleImpl implements RawSerializable {
    String name;
    IdImpl id;
    
    public HandleImpl(String s, IdImpl id) {
      this.name = s;
      this.id = id;
    }
    
    public void serialize(OutputBuffer buf) throws IOException {
      buf.writeUTF(name);
      id.serialize(buf);
    }    
  }
  
  static class IdImpl implements RawSerializable {
    int id;
    public IdImpl(int id) {
      this.id = id;
    }
    public void serialize(OutputBuffer buf) throws IOException {
      buf.writeInt(id);
    }    
  }
  
  static class BogusPR implements PeerReview<HandleImpl, IdImpl> {
    public void challengeSuspectedNode(HandleImpl h) {
      throw new RuntimeException("implement");
    }

    public Authenticator extractAuthenticator(IdImpl id, long seq,
        short entryType, byte[] entryHash, byte[] topMinusOne, byte[] signature)
        throws IOException {
      throw new RuntimeException("implement");
    }

    public AuthenticatorSerializer getAuthenticatorSerializer() {
      throw new RuntimeException("implement");
    }

    public Environment getEnvironment() {
      throw new RuntimeException("implement");
    }

    public long getEvidenceSeq() {
      throw new RuntimeException("implement");
    }

    public Serializer<HandleImpl> getHandleSerializer() {
      throw new RuntimeException("implement");
    }

    public int getHashSizeInBytes() {
      throw new RuntimeException("implement");
    }

    public Serializer<IdImpl> getIdSerializer() {
      throw new RuntimeException("implement");
    }

    public IdentifierExtractor<HandleImpl, IdImpl> getIdentifierExtractor() {
      throw new RuntimeException("implement");
    }

    public int getSignatureSizeInBytes() {
      throw new RuntimeException("implement");
    }

    public long getTime() {
      throw new RuntimeException("implement");
    }

    public void sendEvidenceToWitnesses(IdImpl subject, long timestamp,
        Evidence evidence) {
      throw new RuntimeException("implement");
    }

    public void transmit(HandleImpl dest, boolean b, PeerReviewMessage message) {
      throw new RuntimeException("implement");
    }
    
  }
  
  static class BogusTransport implements IdentityTransport<HandleImpl, IdImpl>{
    public static Map<HandleImpl, BogusTransport> peerTable = new HashMap<HandleImpl, BogusTransport>();
    
    HandleImpl localIdentifier;
    
    public BogusTransport(HandleImpl handle) {
      peerTable.put(handle, this);
      this.localIdentifier = handle;
    }
    
    public boolean hasCertificate(IdImpl id) {
      throw new RuntimeException("implement");
    }

    public Cancellable requestCertificate(HandleImpl source, IdImpl certHolder,
        Continuation<X509Certificate, Exception> c, Map<String, Object> options) {
      throw new RuntimeException("implement");
    }

    public byte[] sign(byte[] bytes) {
      throw new RuntimeException("implement");
    }

    public short signatureSizeInBytes() {
      throw new RuntimeException("implement");
    }

    public void verify(IdImpl id, byte[] msg, int moff, int mlen,
        byte[] signature, int soff, int slen) throws InvalidKeyException,
        NoSuchAlgorithmException, NoSuchProviderException, SignatureException,
        UnknownCertificateException {
      throw new RuntimeException("implement");
    }

    public void acceptMessages(boolean b) {
      throw new RuntimeException("implement");
    }

    public void acceptSockets(boolean b) {
      throw new RuntimeException("implement");
    }

    public HandleImpl getLocalIdentifier() {
      return localIdentifier;
    }

    public SocketRequestHandle<HandleImpl> openSocket(HandleImpl i,
        SocketCallback<HandleImpl> deliverSocketToMe,
        Map<String, Object> options) {
      throw new RuntimeException("implement");
    }

    public MessageRequestHandle<HandleImpl, ByteBuffer> sendMessage(
        HandleImpl i, ByteBuffer m,
        MessageCallback<HandleImpl, ByteBuffer> deliverAckToMe,
        Map<String, Object> options) {
      peerTable.get(i).receiveMessage(localIdentifier, m);      
      return null;
    }

    TransportLayerCallback<HandleImpl, ByteBuffer> callback;
    
    private void receiveMessage(HandleImpl i, ByteBuffer m) {
      try {
        callback.messageReceived(i, m, null);
      } catch (IOException ioe) {
        ioe.printStackTrace();
      }
    }

    public void setCallback(
        TransportLayerCallback<HandleImpl, ByteBuffer> callback) {
      this.callback = callback;
    }

    public void setErrorHandler(ErrorHandler<HandleImpl> handler) {
      throw new RuntimeException("implement");
    }

    public void destroy() {
      throw new RuntimeException("implement");
    }

    public byte[] getEmptyHash() {
      throw new RuntimeException("implement");
    }

    public short getHashSizeBytes() {
      throw new RuntimeException("implement");
    }

    public byte[] hash(long seq, short type, byte[] nodeHash, byte[] contentHash) {
      throw new RuntimeException("implement");
    }

    public byte[] hash(ByteBuffer... hashMe) {
      throw new RuntimeException("implement");
    }
    
  }

  static class Player {
    HandleImpl localHandle;
    
    IdentityTransport<HandleImpl, IdImpl> transport;
    BogusPR pr;
    PeerInfoStore<HandleImpl, IdImpl> store;
    AuthenticatorStore<IdImpl> authStore;
    SecureHistory history;
    CommitmentProtocolImpl<HandleImpl, IdImpl> cp;
    PeerReviewCallback<HandleImpl, IdImpl> app; 

    public Player(String name, int id, Environment env) throws IOException {
      super();
      this.localHandle = new HandleImpl(name, new IdImpl(id));
      
      transport = new BogusTransport(localHandle);
      pr = new BogusPR();
//      store;
//      authStore;
      history = new SecureHistoryImpl(
          new RandomAccessFileIOBuffer(name+".index","rw"),
          new RandomAccessFileIOBuffer(name+".data","rw"),
          false, transport, new IndexEntryFactoryImpl(), env.getLogManager().getLogger(SecureHistoryImpl.class, null));
      cp = new CommitmentProtocolImpl<HandleImpl, IdImpl>(pr,transport,store,authStore,history, app, null, 60000); 
      
    }
  }
  
  public static void main(String[] agrs) throws IOException {
    Environment env = new Environment();
    
    Player alice = new Player("alice",1,env);
    Player bob = new Player("bob",2,env);
    
  }
}
