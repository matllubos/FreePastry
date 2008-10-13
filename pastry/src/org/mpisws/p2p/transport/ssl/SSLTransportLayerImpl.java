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
package org.mpisws.p2p.transport.ssl;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.Socket;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.KeyStoreSpi;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Map;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509KeyManager;
import javax.net.ssl.X509TrustManager;

import org.mpisws.p2p.transport.ErrorHandler;
import org.mpisws.p2p.transport.MessageCallback;
import org.mpisws.p2p.transport.MessageRequestHandle;
import org.mpisws.p2p.transport.P2PSocket;
import org.mpisws.p2p.transport.SocketCallback;
import org.mpisws.p2p.transport.SocketRequestHandle;
import org.mpisws.p2p.transport.TransportLayer;
import org.mpisws.p2p.transport.TransportLayerCallback;
import org.mpisws.p2p.transport.util.DefaultErrorHandler;
import org.mpisws.p2p.transport.util.SocketRequestHandleImpl;

import rice.Continuation;
import rice.environment.Environment;
import rice.environment.logging.Logger;

/**
 * Does not encrypt UDP messages
 * The server authenticates to the client via a CACert
 * 
 * 
 * @author Jeff Hoye
 *
 * @param <Identifier>
 */
public class SSLTransportLayerImpl<Identifier, MessageType> implements SSLTransportLayer<Identifier, MessageType> {
  protected TransportLayer<Identifier, MessageType> tl;
  protected TransportLayerCallback<Identifier, MessageType> callback;
  protected ErrorHandler<Identifier> errorHandler;
  protected Logger logger;
  protected Environment environment;

  protected SSLContext context;
  
//  X509Certificate caCert;
  KeyPair keyPair;
//  X509TrustManager innerTM;
//  
//  TrustManager[] tm = new TrustManager[]{
//      new X509TrustManager() {
//
//          public X509Certificate[] getAcceptedIssuers() {
//            logger.log("getAcceptedIssuers");
//            return innerTM.getAcceptedIssuers();
//            
////              return new X509Certificate[] { caCert };
//          }
//
//          public void checkClientTrusted(
//                  java.security.cert.X509Certificate[] certs, String authType) throws CertificateException {
//            logger.log("checkClientTrusted "+authType+" "+certs[0].getSubjectDN()); //+" "+Arrays.toString(certs));
//            innerTM.checkClientTrusted(certs,authType);
//          }
//
//          public void checkServerTrusted(
//                  java.security.cert.X509Certificate[] certs, String authType) throws CertificateException {            
//            logger.log("checkServerTrusted "+authType+" "+certs[0].getSubjectDN());//+" "+Arrays.toString(certs));
//            innerTM.checkServerTrusted(certs, authType);
//          }
//      }};
//        
//  KeyManager[] km = new KeyManager[] {
//      new X509KeyManager() {
//      
//        public String[] getServerAliases(String keyType, Principal[] issuers) {
//          logger.log("getServerAlieses");
//          return null;
//        }
//      
//        public PrivateKey getPrivateKey(String alias) {
//          logger.log("getPrivateKey("+alias);
//          return keyPair.getPrivate();
//        }
//      
//        public String[] getClientAliases(String keyType, Principal[] issuers) {
//          logger.log("getClientAliases");
//          return null;
//        }
//      
//        public X509Certificate[] getCertificateChain(String alias) {
//          logger.log("getCertificateChain");
//          return null;
//        }
//      
//        public String chooseServerAlias(String keyType, Principal[] issuers,
//            Socket socket) {
//          logger.log("chooseServerAlias");
//          return null;
//        }
//      
//        public String chooseClientAlias(String[] keyType, Principal[] issuers,
//            Socket socket) {
//          logger.log("chooseServerAlias");
//          return null;
//        }
//      
//      }};
  
  private static String keyStoreFile = "testkeys";
  private static String trustStoreFile = "testkeys";
  private static String passwd = "passphrase";

  public SSLTransportLayerImpl(TransportLayer<Identifier, MessageType> tl, KeyStore ks, Environment env) throws Exception {
    this.environment = env;
//    this.keyPair = keyPair;
//    this.caCert = caCert;
    this.logger = env.getLogManager().getLogger(SSLTransportLayerImpl.class, null);
    this.tl = tl;
    errorHandler = new DefaultErrorHandler<Identifier>(logger, Logger.WARNING);

    this.context = SSLContext.getInstance("TLS");

//    KeyManagerFactory kmf =
//      KeyManagerFactory.getInstance("SunX509");
//    kmf.init(ksKeys, passphrase);
//
//    // TrustManager's decide whether to allow connections.
//    TrustManagerFactory tmf =
//      TrustManagerFactory.getInstance("SunX509");
//    tmf.init(ksTrust);

//    context.init(km, tm, null);

    KeyStore ts = ks;
    char[] passphrase = "".toCharArray();
    
//    KeyStore ks = KeyStore.getInstance("JKS");
//    KeyStore ts = KeyStore.getInstance("JKS");

//    char[] passphrase = passwd.toCharArray();

//    ks.load(new FileInputStream(keyStoreFile), passphrase);
//    ts.load(new FileInputStream(trustStoreFile), passphrase);

    
//    KeyStore ks = KeyStore.
    
    KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
    kmf.init(ks, passphrase);

    TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
    tmf.init(ts);    
    
    TrustManager[] tms = tmf.getTrustManagers();
//    innerTM = (X509TrustManager)tms[0];
//    tms = tm;
//    System.out.println(Arrays.toString(tms));
    context.init(kmf.getKeyManagers(), tms, null);

    tl.setCallback(this);
    
  }
  
  public SocketRequestHandle<Identifier> openSocket(Identifier i,
      final SocketCallback<Identifier> deliverSocketToMe, Map<String, Object> options) {
    final SocketRequestHandleImpl<Identifier> ret = new SocketRequestHandleImpl<Identifier>(i,options,logger);
    ret.setSubCancellable(tl.openSocket(i, new SocketCallback<Identifier>() {

      public void receiveException(SocketRequestHandle<Identifier> s,
          Exception ex) {
        deliverSocketToMe.receiveException(s, ex);
      }

      public void receiveResult(SocketRequestHandle<Identifier> cancellable,
          P2PSocket<Identifier> sock) {
        new SSLSocketManager(SSLTransportLayerImpl.this, sock, new Continuation<SSLSocketManager<Identifier>, Exception>() {

          public void receiveException(Exception exception) {
            deliverSocketToMe.receiveException(ret, exception);
          }

          public void receiveResult(SSLSocketManager<Identifier> result) {
            deliverSocketToMe.receiveResult(ret, result);
          }}, false);
      }
    
    }, options));
    return ret;
  }

  /**
   * TODO: support resuming
   */
  public void incomingSocket(final P2PSocket<Identifier> s) throws IOException {
    new SSLSocketManager<Identifier>(this,s,new Continuation<SSLSocketManager<Identifier>, Exception>() {

      public void receiveException(Exception exception) {
        errorHandler.receivedException(s.getIdentifier(), exception);
      }

      public void receiveResult(SSLSocketManager<Identifier> result) {
        try {
          callback.incomingSocket(result);
        } catch (IOException ioe) {
          result.close();
          errorHandler.receivedException(s.getIdentifier(), ioe);
        }
      }},true);
  }

  public void setCallback(TransportLayerCallback<Identifier, MessageType> callback) {
    this.callback = callback;
  }

  public void acceptMessages(boolean b) {
    tl.acceptMessages(b);
  }

  public void acceptSockets(boolean b) {
    tl.acceptSockets(b);
  }

  public void destroy() {
    tl.destroy();
  }

  public Identifier getLocalIdentifier() {
    return tl.getLocalIdentifier();
  }

  public MessageRequestHandle<Identifier, MessageType> sendMessage(Identifier i,
      MessageType m, MessageCallback<Identifier, MessageType> deliverAckToMe,
      Map<String, Object> options) {
    return tl.sendMessage(i, m, deliverAckToMe, options);
  }

  public void setErrorHandler(ErrorHandler<Identifier> handler) {
    this.errorHandler = handler;
  }

  public void messageReceived(Identifier i, MessageType m,
      Map<String, Object> options) throws IOException {
    callback.messageReceived(i, m, options);
  }

}
