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
package org.mpisws.p2p.testing.transportlayer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.security.spec.RSAKeyGenParameterSpec;
import java.util.Map;

import org.mpisws.p2p.pki.x509.CATool;
import org.mpisws.p2p.pki.x509.CAToolImpl;
import org.mpisws.p2p.transport.P2PSocket;
import org.mpisws.p2p.transport.P2PSocketReceiver;
import org.mpisws.p2p.transport.SocketCallback;
import org.mpisws.p2p.transport.SocketRequestHandle;
import org.mpisws.p2p.transport.TransportLayerCallback;
import org.mpisws.p2p.transport.ssl.SSLTransportLayerImpl;
import org.mpisws.p2p.transport.wire.WireTransportLayer;
import org.mpisws.p2p.transport.wire.WireTransportLayerImpl;

import rice.environment.Environment;

public class SSLTest {
  public static void main(String[] args) throws Exception {
    InetAddress addr = InetAddress.getLocalHost();
    Environment rootEnv = new Environment();
    SecureRandom sRandom = new SecureRandom();
    CATool caTool = CAToolImpl.getCATool("MyCA","foo".toCharArray());    
    // make a KeyPair
    KeyPairGenerator keyPairGen =
      KeyPairGenerator.getInstance("RSA", "BC");
    keyPairGen.initialize(
        new RSAKeyGenParameterSpec(768,
            RSAKeyGenParameterSpec.F4),
        sRandom);
    
    Environment aliceEnv = rootEnv.cloneEnvironment("alice");
    InetSocketAddress aliceAddr = new InetSocketAddress(addr,9001); 
    KeyPair alicePair = keyPairGen.generateKeyPair();    
    X509Certificate aliceCert = caTool.sign("alice",alicePair.getPublic());
    WireTransportLayer aliceWire = new WireTransportLayerImpl(aliceAddr,aliceEnv,null);
    SSLTransportLayerImpl<InetSocketAddress, ByteBuffer> aliceSSL = new SSLTransportLayerImpl<InetSocketAddress, ByteBuffer>(
        aliceWire,alicePair,caTool.getCertificate(),aliceEnv);
    
    Environment bobEnv = rootEnv.cloneEnvironment("bob");
    InetSocketAddress bobAddr = new InetSocketAddress(addr,9002); 
    KeyPair bobPair = keyPairGen.generateKeyPair();    
    X509Certificate bobCert = caTool.sign("bob",bobPair.getPublic());
    WireTransportLayer bobWire = new WireTransportLayerImpl(bobAddr,bobEnv,null);
    SSLTransportLayerImpl<InetSocketAddress, ByteBuffer> bobSSL = new SSLTransportLayerImpl<InetSocketAddress, ByteBuffer>(
        bobWire,bobPair,caTool.getCertificate(),bobEnv);

    aliceSSL.setCallback(new TransportLayerCallback<InetSocketAddress, ByteBuffer>() {

      public void incomingSocket(P2PSocket<InetSocketAddress> s)
          throws IOException {
        System.out.println("Incoming Socket:"+s);
      }

      public void messageReceived(InetSocketAddress i, ByteBuffer m,
          Map<String, Object> options) throws IOException {
        // TODO Auto-generated method stub
        
      }});
    
    bobSSL.openSocket(aliceAddr, new SocketCallback<InetSocketAddress>() {    
      public void receiveResult(SocketRequestHandle<InetSocketAddress> cancellable,
          P2PSocket<InetSocketAddress> sock) {
        sock.register(false, true, new P2PSocketReceiver<InetSocketAddress>() {
          ByteBuffer writeMe = ByteBuffer.wrap(new String("foo").getBytes());
          public void receiveSelectResult(P2PSocket<InetSocketAddress> socket,
              boolean canRead, boolean canWrite) throws IOException {            
            socket.write(writeMe);
            if (writeMe.hasRemaining()) {
              socket.register(false, true, this);
            }
            System.out.println("done writing");
          }
        
          public void receiveException(P2PSocket<InetSocketAddress> socket,
              Exception ioe) {
            ioe.printStackTrace();
          }        
        });
      }
    
      public void receiveException(SocketRequestHandle<InetSocketAddress> s,
          Exception ex) {
        ex.printStackTrace();
      }    
    }, null);
    
    
  }
  
  
}
