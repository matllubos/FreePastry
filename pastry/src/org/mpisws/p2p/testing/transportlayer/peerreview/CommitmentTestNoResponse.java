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

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.SignatureException;
import java.security.cert.X509Certificate;
import java.security.spec.RSAKeyGenParameterSpec;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.mpisws.p2p.pki.x509.CATool;
import org.mpisws.p2p.pki.x509.CAToolImpl;
import org.mpisws.p2p.pki.x509.X509Serializer;
import org.mpisws.p2p.pki.x509.X509SerializerImpl;
import org.mpisws.p2p.transport.ErrorHandler;
import org.mpisws.p2p.transport.MessageCallback;
import org.mpisws.p2p.transport.MessageRequestHandle;
import org.mpisws.p2p.transport.P2PSocket;
import org.mpisws.p2p.transport.SocketCallback;
import org.mpisws.p2p.transport.SocketRequestHandle;
import org.mpisws.p2p.transport.TransportLayer;
import org.mpisws.p2p.transport.TransportLayerCallback;
import org.mpisws.p2p.transport.peerreview.IdentifierExtractor;
import org.mpisws.p2p.transport.peerreview.PeerReview;
import org.mpisws.p2p.transport.peerreview.PeerReviewCallback;
import org.mpisws.p2p.transport.peerreview.PeerReviewImpl;
import org.mpisws.p2p.transport.peerreview.WitnessListener;
import org.mpisws.p2p.transport.peerreview.commitment.Authenticator;
import org.mpisws.p2p.transport.peerreview.commitment.AuthenticatorSerializer;
import org.mpisws.p2p.transport.peerreview.commitment.AuthenticatorSerializerImpl;
import org.mpisws.p2p.transport.peerreview.commitment.AuthenticatorStore;
import org.mpisws.p2p.transport.peerreview.commitment.CommitmentProtocol;
import org.mpisws.p2p.transport.peerreview.commitment.CommitmentProtocolImpl;
import org.mpisws.p2p.transport.peerreview.evidence.EvidenceSerializerImpl;
import org.mpisws.p2p.transport.peerreview.history.HashProvider;
import org.mpisws.p2p.transport.peerreview.history.SecureHistory;
import org.mpisws.p2p.transport.peerreview.history.SecureHistoryFactory;
import org.mpisws.p2p.transport.peerreview.history.SecureHistoryFactoryImpl;
import org.mpisws.p2p.transport.peerreview.history.hasher.SHA1HashProvider;
import org.mpisws.p2p.transport.peerreview.history.stub.NullHashProvider;
import org.mpisws.p2p.transport.peerreview.identity.IdentityTransport;
import org.mpisws.p2p.transport.peerreview.identity.IdentityTransportCallback;
import org.mpisws.p2p.transport.peerreview.identity.IdentityTransprotLayerImpl;
import org.mpisws.p2p.transport.peerreview.identity.UnknownCertificateException;
import org.mpisws.p2p.transport.peerreview.infostore.Evidence;
import org.mpisws.p2p.transport.peerreview.infostore.IdStrTranslator;
import org.mpisws.p2p.transport.peerreview.infostore.PeerInfoStore;
import org.mpisws.p2p.transport.peerreview.message.PeerReviewMessage;
import org.mpisws.p2p.transport.peerreview.replay.Verifier;
import org.mpisws.p2p.transport.table.UnknownValueException;
import org.mpisws.p2p.transport.util.MessageRequestHandleImpl;
import org.mpisws.p2p.transport.util.Serializer;

import rice.Continuation;
import rice.environment.Environment;
import rice.environment.logging.Logger;
import rice.p2p.commonapi.Cancellable;
import rice.p2p.commonapi.rawserialization.InputBuffer;
import rice.p2p.commonapi.rawserialization.OutputBuffer;
import rice.p2p.commonapi.rawserialization.RawSerializable;
import rice.p2p.util.rawserialization.SimpleOutputBuffer;

public class CommitmentTestNoResponse {
  public static final byte[] EMPTY_ARRAY = new byte[0];
  static class IdExtractor implements IdentifierExtractor<HandleImpl, IdImpl> {

    public IdImpl extractIdentifier(HandleImpl h) {
      return h.id;
    }
    
  }
  
  static class HandleSerializer implements Serializer<HandleImpl> {

    public HandleImpl deserialize(InputBuffer buf) throws IOException {
      return HandleImpl.build(buf);
    }

    public void serialize(HandleImpl i, OutputBuffer buf) throws IOException {
      i.serialize(buf);
    }
    
  }
  
  static class IdSerializer implements Serializer<IdImpl> {

    public IdImpl deserialize(InputBuffer buf) throws IOException {
      return IdImpl.build(buf);
    }

    public void serialize(IdImpl i, OutputBuffer buf) throws IOException {
      i.serialize(buf);
    }
    
  }
  
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
    
    public static HandleImpl build(InputBuffer buf) throws IOException {
      return new HandleImpl(buf.readUTF(), IdImpl.build(buf));
    }
    
    public String toString() {
      return "HandleImpl<"+name+">";
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
    
    public static IdImpl build(InputBuffer buf) throws IOException {
      return new IdImpl(buf.readInt());
    }
    
    public String toString() {
      return "Id<"+id+">";
    }
    
    public int hashCode() {
      return id;      
    }
    
    public boolean equals(Object o) {
      IdImpl that = (IdImpl)o;
      return (this.id == that.id);
    }
  }
  
  static class BogusPR extends PeerReviewImpl<HandleImpl, IdImpl> {

    public BogusPR(String name, IdentityTransport<HandleImpl, IdImpl> transport,
        Environment env) throws IOException {

      super(transport, env, new HandleSerializer(), new IdSerializer(), new IdExtractor(), new IdStrTranslator<IdImpl>(){

        public IdImpl readIdentifierFromString(String s) {
          return new IdImpl(Integer.parseInt(s));
        }

        public String toString(IdImpl id) {
          return Integer.toString(id.id);
        }},
          new AuthenticatorSerializerImpl(0,0), new EvidenceSerializerImpl<HandleImpl, IdImpl>(new HandleSerializer(), new IdSerializer(), transport.getHashSizeBytes(),transport.getSignatureSizeBytes()));
      init(name);
    }
  }
  
  static class BogusTransport implements TransportLayer<HandleImpl, ByteBuffer>{
    public static Map<HandleImpl, BogusTransport> peerTable = new HashMap<HandleImpl, BogusTransport>();
    
//    public Map<IdImpl, X509Certificate> certs = new HashMap<IdImpl, X509Certificate>();
    
    HandleImpl localIdentifier;
    
    public BogusTransport(HandleImpl handle, X509Certificate cert) {
      peerTable.put(handle, this);
      this.localIdentifier = handle;
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
    
    protected void receiveMessage(HandleImpl i, ByteBuffer m) {
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
  }
  
  class BogusApp implements PeerReviewCallback<HandleImpl, IdImpl> {
    Logger logger;
    public BogusApp(Logger logger) {
      this.logger = logger;
    }

    public void notifyStatusChange(
        IdImpl id,
        int newStatus) {
      logger.log("notifyStatusChange("+id+","+PeerReviewImpl.getStatusString(newStatus)+")");
    }

    public void init() {
      throw new RuntimeException("implement");
    }

    public boolean loadCheckpoint(InputBuffer buffer) {
      throw new RuntimeException("implement");
    }

    public void storeCheckpoint(OutputBuffer buffer) {
      throw new RuntimeException("implement");
    }

    public void destroy() {
      throw new RuntimeException("implement");
    }

    public void notifyCertificateAvailable(IdImpl id) {
      throw new RuntimeException("implement");
    }

    public void receive(HandleImpl source, boolean datagram, ByteBuffer msg) {
      throw new RuntimeException("implement");
    }

    public void sendComplete(long id) {
      throw new RuntimeException("implement");
    }

    public void statusChange(IdImpl id, int newStatus) {
      throw new RuntimeException("implement");
    }

    public void incomingSocket(P2PSocket<HandleImpl> s) throws IOException {
      throw new RuntimeException("implement");
    }

    public void messageReceived(HandleImpl i, ByteBuffer m,
        Map<String, Object> options) throws IOException {
      System.out.println("Message received.");
    }
    
    public void getWitnesses(IdImpl subject,
        WitnessListener<HandleImpl, IdImpl> callback) {
      callback.notifyWitnessSet(subject, Collections.singletonList(carol.localHandle));
    }

    public Collection<HandleImpl> getMyWitnessedNodes() {
      // TODO Auto-generated method stub
      return null;
    }

    public PeerReviewCallback<HandleImpl, IdImpl> getReplayInstance(
        Verifier<HandleImpl, IdImpl> v) {
      // TODO Auto-generated method stub
      return null;
    }
  }

  static Map<HandleImpl, IdentityTransprotLayerImpl<HandleImpl, IdImpl>> idTLTable = new HashMap<HandleImpl, IdentityTransprotLayerImpl<HandleImpl,IdImpl>>();

  static final CATool caTool;
  static final KeyPairGenerator keyPairGen;
  static {
    try {
      SecureRandom random = new SecureRandom();
      caTool = CAToolImpl.getCATool("CommitmentTest","foo".toCharArray());
      
      // make a KeyPair
      keyPairGen =
        KeyPairGenerator.getInstance("RSA", "BC");
      keyPairGen.initialize(
          new RSAKeyGenParameterSpec(768,
              RSAKeyGenParameterSpec.F4),
          random);
    } catch (Exception ioe) {
      throw new RuntimeException(ioe);
    }
  }

  class Player {
    
    HandleImpl localHandle;
    
    BogusPR pr;
    IdentityTransprotLayerImpl<HandleImpl, IdImpl> transport;
    Environment env;
    
    public Player(String name, int id, Environment env) throws Exception {
      this(name,id,env, null);
    }
    public Player(String name, int id, Environment env2, final HandleImpl dropFrom) throws Exception {
      super();
      env = env2.cloneEnvironment(name);
      File f = new File(name);
      if (f.exists()) {
        File f2 = new File(f,"peers");
        File[] foo = f2.listFiles();
        if (foo != null) {
          for (int c = 0; c < foo.length; c++) {
            foo[c].delete();
          }
        }
        
        foo = f.listFiles();
        if (foo != null) {
          for (int c = 0; c < foo.length; c++) {
            foo[c].delete();
          }
        }
        
        System.out.println("Delete "+f+","+f.delete());        
      }
      
      this.localHandle = new HandleImpl(name, new IdImpl(id));
      KeyPair pair = keyPairGen.generateKeyPair();    
      X509Certificate cert = caTool.sign(name,pair.getPublic());      
      BogusTransport t1;
      if (dropFrom != null) {
        t1 = new BogusTransport(localHandle, cert) {
          @Override
          protected void receiveMessage(HandleImpl i, ByteBuffer m) {
            if (i.equals(dropFrom)) {
              System.out.println("Dropping message "+m);              
            } else {
              super.receiveMessage(i, m);            
            }
          }
          
        };
      } else {
        t1 = new BogusTransport(localHandle, cert);        
      }
      transport = new IdentityTransprotLayerImpl<HandleImpl, IdImpl>(
          new IdSerializer(), new X509SerializerImpl(),this.localHandle.id,
          cert,pair.getPrivate(),t1,new SHA1HashProvider(),env) {
        @Override
        public Cancellable requestCertificate(HandleImpl source, final IdImpl certHolder,
            final Continuation<X509Certificate, Exception> c, Map<String, Object> options) {
//          System.out.println("requestCert("+source+")");
          return idTLTable.get(source).requestValue(source, certHolder, new Continuation<X509Certificate, Exception>() {
          
            public void receiveResult(X509Certificate result) {
              knownValues.put(certHolder, result);
              if (c != null) c.receiveResult(result);
            }
          
            public void receiveException(Exception exception) {
              if (c != null) c.receiveException(exception);
            }
          
          }, options);
//          if (cert != null) {
//            knownValues.put(certHolder, cert);
//            if (c != null) c.receiveResult(cert);
//            callback.notifyCertificateAvailable(certHolder);
//          } else {
//            UnknownValueException ex = new UnknownValueException(source, certHolder);
//            if (c != null) {
//              c.receiveException(ex);
//            } else {
//              ex.printStackTrace();
//            }
//          }
//          return null;
        }        
      };
      idTLTable.put(localHandle, transport);
      pr = new BogusPR(name, transport, env);
      pr.setCallback(new BogusApp(env.getLogManager().getLogger(BogusApp.class, null)));
    }
  }

  private static HashProvider hasher;
  private static SecureHistoryFactory historyFactory;
  
  Player alice;
  Player bob;
  Player carol;
  
  public CommitmentTestNoResponse() throws Exception {
    Environment env = new Environment();
    try {      
      hasher = new NullHashProvider();
      historyFactory = new SecureHistoryFactoryImpl(hasher,env);
      
      alice = new Player("alice",1,env);
      bob = new Player("bob",2,env, alice.localHandle);    
      // the witness
      carol = new Player("carol",3,env);    
          
    } catch (Exception e) {
      env.destroy();      
      throw e;
    }
    alice.env.getSelectorManager().invoke(new Runnable() {
      
      public void run() {
        alice.pr.sendMessage(bob.localHandle, ByteBuffer.wrap("foo".getBytes()), new MessageCallback<HandleImpl, ByteBuffer>() {
          
          public void sendFailed(MessageRequestHandle<HandleImpl, ByteBuffer> msg,
              Exception reason) {
            System.out.println("sendFailed("+msg+")");
            reason.printStackTrace();
          }
        
          public void ack(MessageRequestHandle<HandleImpl, ByteBuffer> msg) {
            System.out.println("alice ack("+msg+")");
          }
        
        }, null);
      }      
    });
    Thread.sleep(15000);
    carol.env.getSelectorManager().invoke(new Runnable() {
      
      public void run() {
        carol.pr.sendMessage(bob.localHandle, ByteBuffer.wrap("bar".getBytes()), new MessageCallback<HandleImpl, ByteBuffer>() {
          
          public void sendFailed(MessageRequestHandle<HandleImpl, ByteBuffer> msg,
              Exception reason) {
            System.out.println("sendFailed("+msg+")");
            reason.printStackTrace();
          }
        
          public void ack(MessageRequestHandle<HandleImpl, ByteBuffer> msg) {
            System.out.println("carol ack("+msg+")");
          }
        
        }, null);
      }      
    });
    
    Thread.sleep(45000);
    env.destroy();    
  }
  
  public static void main(String[] agrs) throws Exception {
    new CommitmentTestNoResponse();
  }
}
