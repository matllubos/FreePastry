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
package org.mpisws.p2p.transport.signature;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;

import org.mpisws.p2p.pki.x509.X509Serializer;
import org.mpisws.p2p.transport.ErrorHandler;
import org.mpisws.p2p.transport.MessageCallback;
import org.mpisws.p2p.transport.MessageRequestHandle;
import org.mpisws.p2p.transport.P2PSocket;
import org.mpisws.p2p.transport.SocketCallback;
import org.mpisws.p2p.transport.SocketRequestHandle;
import org.mpisws.p2p.transport.TransportLayer;
import org.mpisws.p2p.transport.TransportLayerCallback;
import org.mpisws.p2p.transport.peerreview.identity.CertificateManager;
import org.mpisws.p2p.transport.peerreview.identity.IdentityTransport;
import org.mpisws.p2p.transport.peerreview.replay.IdentifierSerializer;
import org.mpisws.p2p.transport.util.BufferReader;
import org.mpisws.p2p.transport.util.BufferWriter;
import org.mpisws.p2p.transport.util.DefaultErrorHandler;
import org.mpisws.p2p.transport.util.SocketInputBuffer;
import org.mpisws.p2p.transport.util.SocketRequestHandleImpl;

import rice.Continuation;
import rice.environment.Environment;
import rice.environment.logging.Logger;
import rice.p2p.commonapi.Cancellable;
import rice.p2p.util.rawserialization.SimpleInputBuffer;
import rice.p2p.util.rawserialization.SimpleOutputBuffer;

/**
 * TODO: make it store known certs to a file, make it periodically check the revocation server.
 * 
 * @author Jeff Hoye
 *
 */
public class CertificateTransprotLayerImpl<Identifier> implements CertificateTransportLayer<Identifier, ByteBuffer>, TransportLayerCallback<Identifier, ByteBuffer>, IdentityTransport<Identifier, ByteBuffer> {
  public static final byte PASSTHROUGH = 0;
  public static final byte CERT_REQUEST = 1;
  public static final byte CERT_RESPONSE = 2;
  public static final byte CERT_RESPONSE_FAILED = 3;
  
  public static final String DEFAULT_SIGNATURE_ALGORITHM = "SHA1withRSA";
  
  
  Map<Identifier, X509Certificate> knownCerts;

  TransportLayerCallback<Identifier, ByteBuffer> callback;
  TransportLayer<Identifier, ByteBuffer> tl;

  IdentifierSerializer<Identifier> identifierSerializer;
  X509Serializer certificateSerializer;
   
  ErrorHandler<Identifier> errorHandler;
  Logger logger;
  
  String signatureAlgorithm = DEFAULT_SIGNATURE_ALGORITHM;
  String signatureImpl = "BC";
  
  Signature signer;
  
  // TODO: handle memory problems
  Map<Identifier, Signature> verifiers = new HashMap<Identifier, Signature>();
  
  public CertificateTransprotLayerImpl(IdentifierSerializer<Identifier> iSerializer, X509Serializer cSerializer, X509Certificate localCert, PrivateKey localPrivate, TransportLayer<Identifier, ByteBuffer> tl, Environment env) throws InvalidKeyException, NoSuchAlgorithmException, NoSuchProviderException {
    this.identifierSerializer = iSerializer;
    this.certificateSerializer = cSerializer;
    this.tl = tl;
    
    this.logger = env.getLogManager().getLogger(CertificateTransprotLayerImpl.class, null);
    this.errorHandler = new DefaultErrorHandler<Identifier>(this.logger);
    
    signer = Signature.getInstance(DEFAULT_SIGNATURE_ALGORITHM,"BC");
    signer.initSign(localPrivate);

  }
  
  /**
   * CERT_REQUEST, int requestId, Identifier
   */
  public Cancellable requestCertificate(final Identifier source,
      final Identifier principal, final Continuation<X509Certificate, Exception> c,
      Map<String, Object> options) {
    
    if (knownCerts.containsKey(principal)) {
      c.receiveResult(knownCerts.get(principal));
      return null;
    }
    
    return tl.openSocket(source, new SocketCallback<Identifier>() {

      public void receiveResult(SocketRequestHandle<Identifier> cancellable,
          P2PSocket<Identifier> sock) {
        SimpleOutputBuffer sob = new SimpleOutputBuffer();
        try {
          sob.writeByte(CERT_REQUEST);
          identifierSerializer.serialize(principal,sob);
          new BufferWriter<Identifier>(sob.getByteBuffer(), sock, new Continuation<P2PSocket<Identifier>, Exception>() {
  
            public void receiveException(Exception exception) {
              c.receiveException(exception);
            }
  
            public void receiveResult(P2PSocket<Identifier> result) {
              new BufferReader<Identifier>(result,new Continuation<ByteBuffer, Exception>() {
              
                public void receiveResult(ByteBuffer result) {
                  try {
                    SimpleInputBuffer sib = new SimpleInputBuffer(result);
                    byte response = sib.readByte();
                    switch(response) {
                    case CERT_RESPONSE:
                      X509Certificate cert = certificateSerializer.deserialize(sib);
                      // TODO: verify the cert
                      
                      knownCerts.put(principal, cert);
                      c.receiveResult(cert);
                      break;
                    case CERT_RESPONSE_FAILED:
                      c.receiveException(new UnknownCertificateException(source, principal));
                    default:
                      c.receiveException(new IllegalStateException("Unknown response:"+response));    
                    }
                  } catch (Exception ioe) {
                    c.receiveException(ioe);
                  }
                }
              
                public void receiveException(Exception exception) {
                  c.receiveException(exception);
                }
              
              });
            }        
          });
        } catch (IOException ioe) {
          c.receiveException(ioe);
        }
      }    
      
      public void receiveException(SocketRequestHandle<Identifier> s,
          Exception ex) {
        c.receiveException(ex);
      }

    }, options);
  }
  
  public SocketRequestHandle<Identifier> openSocket(Identifier i,
      final SocketCallback<Identifier> deliverSocketToMe, Map<String, Object> options) {
    final SocketRequestHandleImpl<Identifier> ret = new SocketRequestHandleImpl<Identifier>(i,options,logger);
    
    ret.setSubCancellable(tl.openSocket(i, new SocketCallback<Identifier>() {

      public void receiveException(SocketRequestHandle<Identifier> s,
          Exception ex) {
        deliverSocketToMe.receiveException(ret, ex);
      }

      public void receiveResult(SocketRequestHandle<Identifier> cancellable,
          P2PSocket<Identifier> sock) {
        ByteBuffer writeMe = ByteBuffer.allocate(1);
        writeMe.put(PASSTHROUGH);
        writeMe.clear();
        new BufferWriter<Identifier>(writeMe, sock, new Continuation<P2PSocket<Identifier>, Exception>() {

          public void receiveException(Exception exception) {
            deliverSocketToMe.receiveException(ret, exception);
          }

          public void receiveResult(P2PSocket<Identifier> result) {
            deliverSocketToMe.receiveResult(ret, result);
          }
        
        }, false);
      }
      
    }, options));
    return ret;
  }
  
  public void incomingSocket(final P2PSocket<Identifier> sock) throws IOException {
    new BufferReader<Identifier>(sock,new Continuation<ByteBuffer, Exception>() {
    
      public void receiveResult(ByteBuffer result) {
        byte type = result.get();
        switch (type) {
        case PASSTHROUGH:
          try {
            callback.incomingSocket(sock);
          } catch (IOException ioe) {
            errorHandler.receivedException(sock.getIdentifier(), ioe);
          }
          return;
        case CERT_REQUEST:
          handleCertRequest(sock);
        default:
          errorHandler.receivedUnexpectedData(sock.getIdentifier(), new byte[] {type}, 0, sock.getOptions());
          sock.close();
        }
      }
    
      public void receiveException(Exception exception) {
        errorHandler.receivedException(sock.getIdentifier(), exception);
      }    
    },1);    
  }

  public void handleCertRequest(final P2PSocket<Identifier> sock) {
    new BufferReader<Identifier>(sock,new Continuation<ByteBuffer, Exception>() {
    
      public void receiveResult(ByteBuffer result) {
        try {
          SimpleInputBuffer sib = new SimpleInputBuffer(result);
          Identifier principal = identifierSerializer.deserialize(sib);
          ByteBuffer writeMe;
          if (knownCerts.containsKey(principal)) {
            SimpleOutputBuffer sob = new SimpleOutputBuffer();
            sob.writeByte(CERT_RESPONSE);
            certificateSerializer.serialize(sob, knownCerts.get(principal));
            writeMe = sob.getByteBuffer();
          } else {
            writeMe = ByteBuffer.allocate(1);
            writeMe.put(CERT_RESPONSE_FAILED);
            writeMe.clear();
          }
          new BufferWriter<Identifier>(writeMe,sock,null);
        } catch (Exception ioe) {
          errorHandler.receivedException(sock.getIdentifier(), ioe);
          sock.close();
        }
      }
    
      public void receiveException(Exception exception) {
        errorHandler.receivedException(sock.getIdentifier(), exception);
      }
    
    });
  }
  
  public boolean hasCertificate(Identifier i) {
    return knownCerts.containsKey(i);
  }
  
//  public void getCertificate(Identifier i) {
//    
//  }
  
  public void acceptMessages(boolean b) {
    tl.acceptMessages(b);
  }
  
  public void acceptSockets(boolean b) {
    tl.acceptSockets(b);
  }
  
  public Identifier getLocalIdentifier() {
    return tl.getLocalIdentifier();
  }
  
  public MessageRequestHandle<Identifier, ByteBuffer> sendMessage(Identifier i,
      ByteBuffer m, MessageCallback<Identifier, ByteBuffer> deliverAckToMe,
      Map<String, Object> options) {
    return tl.sendMessage(i, m, deliverAckToMe, options);
  }
  
  public void setCallback(
      TransportLayerCallback<Identifier, ByteBuffer> callback) {
    this.callback = callback;
  }
  public void setErrorHandler(ErrorHandler<Identifier> handler) {
    this.errorHandler = handler;
  }
  
  public void destroy() {
    tl.destroy();
  }
  
  public void messageReceived(Identifier i, ByteBuffer m,
      Map<String, Object> options) throws IOException {
    callback.messageReceived(i, m, options);
  }

  // TODO: implement
  public byte[] sign(byte[] bytes) throws SignatureException {
    signer.update(bytes);
    return signer.sign();
  }

  public void verify(Identifier id, byte[] msg, int moff, int mlen, byte[] signature, int soff, int slen) throws InvalidKeyException, NoSuchAlgorithmException, NoSuchProviderException, SignatureException, UnknownCertificateException {
    Signature verifier = getVerifier(id);
    if (verifier == null) throw new UnknownCertificateException(getLocalIdentifier(),id);
    verifier.update(msg, moff, mlen);
    verifier.verify(signature, soff, slen);
  }
  
  /**
   * Returns null if we don't know the cert for the identifier.
   * 
   * @param i
   * @return
   * @throws NoSuchAlgorithmException
   * @throws NoSuchProviderException
   * @throws InvalidKeyException
   */
  public Signature getVerifier(Identifier i) throws NoSuchAlgorithmException, NoSuchProviderException, InvalidKeyException {
    Signature ret = verifiers.get(i);
    if (ret == null) {
      if (knownCerts.containsKey(i)) {
        X509Certificate cert = knownCerts.get(i);
        ret = Signature.getInstance(DEFAULT_SIGNATURE_ALGORITHM, "BC");
        ret.initVerify(cert);
        verifiers.put(i, ret);
      }
    }
    return ret;
  }
  
}
