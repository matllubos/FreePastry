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
import java.util.ArrayList;
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
import org.mpisws.p2p.transport.peerreview.replay.record.RecordLayer;
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
import rice.selector.TimerTask;

public class CommitmentTest {
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
    
    public int hashCode() {
      return id.hashCode()^name.hashCode();
    }
    
    public boolean equals(Object o) {
      HandleImpl that = (HandleImpl)o;
      if (!id.equals(that.id)) return false;
      return name.equals(that.name);
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
  
//  static class BogusPR extends PeerReviewImpl<HandleImpl, IdImpl> {
//    Environment env;
//    CommitmentProtocol<HandleImpl, IdImpl> commitmentProtocol;
//    IdentityTransport<HandleImpl, IdImpl> transport;
//    PeerInfoStore<HandleImpl, IdImpl> store;
//    AuthenticatorStore<IdImpl> authStore;
//    SecureHistory history;
//    PeerReviewCallback<HandleImpl, IdImpl> app; 
    
//    public BogusPR(IdentityTransport<HandleImpl, IdImpl> transport, 
//        SecureHistory history, Environment env) throws IOException {
//      this.env = env;
//      this.transport = transport;
//      
//      transport.setCallback(this);
//      app = new BogusApp();
//      commitmentProtocol = new CommitmentProtocolImpl<HandleImpl, IdImpl>(this,transport,store,authStore,history, app, null, 60000);       
//    }
    
//    public BogusPR(String name, IdentityTransport<HandleImpl, IdImpl> transport,
//        Environment env) throws IOException {
////      , Serializer<HandleImpl> handleSerializer,
////        Serializer<IdImpl> idSerializer,
////        IdentifierExtractor<HandleImpl, IdImpl> identifierExtractor,
////        AuthenticatorSerializer authenticatorSerialilzer) {
//      super(transport, env, new HandleSerializer(), new IdSerializer(), new IdExtractor(), new IdStrTranslator<IdImpl>(){
//
//        public IdImpl readIdentifierFromString(String s) {
//          return new IdImpl(Integer.parseInt(s));
//        }
//
//        public String toString(IdImpl id) {
//          return Integer.toString(id.id);
//        }},
//          new AuthenticatorSerializerImpl(0,0), new EvidenceSerializerImpl<HandleImpl, IdImpl>(new HandleSerializer(),new IdSerializer(),transport.getHashSizeBytes(),transport.getSignatureSizeBytes()));
//      init(name);
//    }
//  }
  
  static class BogusTransport implements TransportLayer<HandleImpl, ByteBuffer> {
    public static Map<HandleImpl, BogusTransport> peerTable = new HashMap<HandleImpl, BogusTransport>();
    
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
  }
  
  class BogusApp implements PeerReviewCallback<HandleImpl, IdImpl> {

    Logger logger;
    Player player;
    Environment env;
    private TransportLayer<HandleImpl, ByteBuffer> tl;
    
    
    public BogusApp(Player player, TransportLayer<HandleImpl, ByteBuffer> tl, Environment env) {
      super();
      this.player = player;
      this.tl = tl;
      this.env = env;
      this.logger = env.getLogManager().getLogger(BogusApp.class, null);
      tl.setCallback(this);
    }
    

    public void init() {
      if (player.id == 1) {
        env.getSelectorManager().schedule(new TimerTask() {
          public String toString() {
            return "SendMessageTask "+scheduledExecutionTime();
          }
          
          @Override
          public void run() {
            tl.sendMessage(bob.localHandle, ByteBuffer.wrap("foo".getBytes()), new MessageCallback<HandleImpl, ByteBuffer>() {
              
              public void sendFailed(MessageRequestHandle<HandleImpl, ByteBuffer> msg,
                  Exception reason) {
                System.out.println("sendFailed("+msg+")");
                reason.printStackTrace();
              }
            
              public void ack(MessageRequestHandle<HandleImpl, ByteBuffer> msg) {
                alice.logger.log("ack("+msg+")");
              }
            
            }, null);
          }
        
        }, 1000);
      }
    }

    public boolean loadCheckpoint(InputBuffer buffer) throws IOException {
      logger.log("loadCheckpoint");
      if (buffer.readInt() != 31173) throw new RuntimeException("invalid checkpoint");
      init();
      return true;
    }

    public void storeCheckpoint(OutputBuffer buffer) throws IOException {
//      throw new RuntimeException("implement");
      buffer.writeInt(31173);
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
      logger.log("Message received.");
    }

    public void getWitnesses(IdImpl subject,
        WitnessListener<HandleImpl, IdImpl> callback) {
      callback.notifyWitnessSet(subject, Collections.singletonList(carol.localHandle));
    }

    public void notifyStatusChange(
        IdImpl id,
        int newStatus) {
      logger.log("notifyStatusChange("+id+","+PeerReviewImpl.getStatusString(newStatus)+")");
    }

    public Collection<HandleImpl> getMyWitnessedNodes() {
      return player.witnessed;
    }

    public PeerReviewCallback<HandleImpl, IdImpl> getReplayInstance(Verifier<HandleImpl> v) {
      return new BogusApp(playerTable.get(v.getLocalIdentifier()),v,v.getEnvironment());
    }
  }

  static Map<HandleImpl, IdentityTransprotLayerImpl<HandleImpl, IdImpl>> idTLTable = new HashMap<HandleImpl, IdentityTransprotLayerImpl<HandleImpl,IdImpl>>();

  CATool caTool;
  KeyPairGenerator keyPairGen;

  Map<HandleImpl,Player> playerTable = new HashMap<HandleImpl, Player>();

  
  class Player {
    
    Logger logger;
    HandleImpl localHandle;
    
    PeerReview<HandleImpl, IdImpl> pr;
    IdentityTransprotLayerImpl<HandleImpl, IdImpl> transport;
    public Collection<HandleImpl> witnessed = new ArrayList<HandleImpl>();

    int id;
    
    public Player(final String name, int id, final Environment env2) throws Exception {
      super();
      Environment env = env2.cloneEnvironment(name);
      this.id = id;
      this.logger = env.getLogManager().getLogger(Player.class, null);
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
        
//        System.out.println("Delete "+f+","+f.delete());        
      }
//      File f = new File(name+".data");
//      if (f.exists()) f.delete();
//      f = new File(name+".index");
//      if (f.exists()) f.delete();
      
      this.localHandle = new HandleImpl(name, new IdImpl(id));
      playerTable.put(localHandle, this);
      KeyPair pair = keyPairGen.generateKeyPair();    
      X509Certificate cert = caTool.sign(name,pair.getPublic());
      BogusTransport t1 = new BogusTransport(localHandle, cert);
      transport = new IdentityTransprotLayerImpl<HandleImpl, IdImpl>(
          new IdSerializer(), new X509SerializerImpl(),this.localHandle.id,
          cert,pair.getPrivate(),t1,new SHA1HashProvider(),env) {
        
        @Override
        public Cancellable requestCertificate(final HandleImpl source, final IdImpl certHolder,
            final Continuation<X509Certificate, Exception> c, Map<String, Object> options) {
          //logger.log("requestCert("+certHolder+" from "+source+")");
          return idTLTable.get(source).requestValue(source, certHolder, new Continuation<X509Certificate, Exception>() {
          
            public void receiveResult(X509Certificate result) {
              //logger.log("delivering cert for ("+certHolder+") c:"+c);
              knownValues.put(certHolder, result);
              if (c != null) c.receiveResult(result);
            }
          
            public void receiveException(Exception exception) {
              //logger.logException("exception when requesting cert for ("+certHolder+") c:"+c,exception);
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
      pr = new PeerReviewImpl<HandleImpl, IdImpl>(transport, env, new HandleSerializer(), new IdSerializer(), new IdExtractor(), new IdStrTranslator<IdImpl>(){

        public IdImpl readIdentifierFromString(String s) {
          return new IdImpl(Integer.parseInt(s));
        }

        public String toString(IdImpl id) {
          return Integer.toString(id.id);
        }},
          new AuthenticatorSerializerImpl(20,96), new EvidenceSerializerImpl<HandleImpl, IdImpl>(new HandleSerializer(),new IdSerializer(),transport.getHashSizeBytes(),transport.getSignatureSizeBytes()));
      pr.setApp(new BogusApp(this,pr,env));
      env.getSelectorManager().invoke(new Runnable() {
        public void run() {
          try {
            pr.init(name);
          } catch (IOException ioe) {
            ioe.printStackTrace();
            env2.destroy();
          }
        }
      });
    }
  }

  private HashProvider hasher;
  private SecureHistoryFactory historyFactory;
  
  Player alice;
  Player bob;
  Player carol;

  public CommitmentTest() throws Exception {
    final Environment env = RecordLayer.generateEnvironment(); //new Environment();
    
    SecureRandom random = new SecureRandom();
    caTool = CAToolImpl.getCATool("CommitmentTest","foo".toCharArray());
    
    // make a KeyPair
    keyPairGen =
      KeyPairGenerator.getInstance("RSA", "BC");
    keyPairGen.initialize(
        new RSAKeyGenParameterSpec(768,
            RSAKeyGenParameterSpec.F4),
        random);
    
    try {      
      hasher = new NullHashProvider();
      historyFactory = new SecureHistoryFactoryImpl(hasher,env);
      env.getSelectorManager().invoke(new Runnable() {
        
        public void run() {
          try {
      alice = new Player("alice",1,env);
      bob = new Player("bob",2,env);    
      carol = new Player("carol",3,env);
      
      carol.witnessed.add(alice.localHandle);
      carol.witnessed.add(bob.localHandle);
          } catch (Exception ioe) {
            throw new RuntimeException(ioe);
          }
        }
      });
          
//      bob.pr.requestCertificate(alice.localHandle, alice.localHandle.id, new Continuation<X509Certificate, Exception>() {
//      
//        public void receiveResult(X509Certificate result) {
//          try {            
//            byte[] msg = alice.transport.hash(ByteBuffer.wrap("foo".getBytes()));
////            byte[] msg = "foo".getBytes();
//            byte[] signature = alice.transport.sign(msg);
//            bob.transport.verify(alice.localHandle.id, ByteBuffer.wrap(msg), ByteBuffer.wrap(signature));      
//          } catch (Exception e) {
//            e.printStackTrace();
//          }
//        }
//      
//        public void receiveException(Exception exception) {
//          // TODO Auto-generated method stub
//      
//        }
//      
//      }, null);
      
//      System.out.println("Done creating players.");
      
//      env.getSelectorManager().invoke(new Runnable() {
//      
//        public void run() {
//          // TODO Auto-generated method stub
//      
//      alice.pr.sendMessage(bob.localHandle, ByteBuffer.wrap("foo".getBytes()), new MessageCallback<HandleImpl, ByteBuffer>() {
//      
//        public void sendFailed(MessageRequestHandle<HandleImpl, ByteBuffer> msg,
//            Exception reason) {
//          System.out.println("sendFailed("+msg+")");
//          reason.printStackTrace();
//        }
//      
//        public void ack(MessageRequestHandle<HandleImpl, ByteBuffer> msg) {
//          alice.logger.log("ack("+msg+")");
//        }
//      
//      }, null);
//        }
//        
//      });
  //    alice.transport.sendMessage(bob.localHandle, ByteBuffer.wrap("foo".getBytes()), null, null);
    } catch (Exception e) {
      env.destroy();      
      throw e;
    }
    
    env.getSelectorManager().invoke(new Runnable() {
    
      public void run() {
        // TODO Auto-generated method stub
    
      }
    
    });
//    Thread.sleep(5000);
//    env.destroy();
  }
  
  public static void main(String[] agrs) throws Exception {
    new CommitmentTest();
  }
}
