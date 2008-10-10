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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Map;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;

import org.mpisws.p2p.transport.P2PSocket;
import org.mpisws.p2p.transport.P2PSocketReceiver;

import rice.Continuation;
import rice.Executable;


public class SSLSocketManager<Identifier> implements P2PSocket<Identifier> {
  P2PSocket<Identifier> socket;

  SSLEngine engine;
  SSLTransportLayerImpl<Identifier, ?> sslTL;

  boolean handshaking = true;

  private Continuation<SSLSocketManager<Identifier>, Exception> c;
  /**
   * Called on incoming side
   * @param transportLayerImpl
   * @param s
   */
  public SSLSocketManager(SSLTransportLayerImpl<Identifier, ?> sslTL, P2PSocket<Identifier> s, Continuation<SSLSocketManager<Identifier>, Exception> c, boolean server) {
    this.sslTL = sslTL;
    this.socket = s;
    this.c = c;
    engine = sslTL.context.createSSLEngine(s.getIdentifier().toString(),0);
    engine.setUseClientMode(!server);
//    System.out.println(Arrays.toString(engine.getSupportedCipherSuites()));
//    engine.setEnabledCipherSuites(new String[] {"TLS_DHE_DSS_WITH_AES_256_CBC_SHA"});
    engine.setEnabledCipherSuites(engine.getSupportedCipherSuites());
    try {
      engine.beginHandshake();
    } catch (Exception e) {
      c.receiveException(e);
      return;
    }
    go();
  }

  protected void go() {
    try {
      HandshakeStatus status = engine.getHandshakeStatus();
      sslTL.logger.log("Status:"+status);
      if (status == HandshakeStatus.NEED_UNWRAP) {
        socket.register(true,false,new P2PSocketReceiver<Identifier>() {
          ByteBuffer incoming = ByteBuffer.allocate(65535);
          public void receiveException(P2PSocket<Identifier> socket, Exception ioe) {
            c.receiveException(ioe);
          }
  
          public void receiveSelectResult(P2PSocket<Identifier> socket,
              boolean canRead, boolean canWrite) throws IOException {
            sslTL.logger.log("rsr1("+canRead+","+canWrite+")");
            if (canRead) {
              socket.read(incoming);
              incoming.flip();
              sslTL.logger.log("read: "+incoming);
              ByteBuffer foo = ByteBuffer.allocate(1024);
              engine.unwrap(incoming, foo);
              foo.flip();
              if (foo.hasRemaining()) sslTL.logger.log("after read: "+foo);
            }
            go();
          }
        
        });
      } else if (status == HandshakeStatus.NEED_WRAP) {
        sslTL.logger.log("before wrap");
        final ByteBuffer outgoing = ByteBuffer.allocate(65535);
        engine.wrap(ByteBuffer.allocate(0), outgoing);
        outgoing.flip();
        sslTL.logger.log("registering to write"+outgoing);
        if (outgoing.remaining() == 0) throw new RuntimeException("outgoing:"+outgoing);
        socket.register(false,true,new P2PSocketReceiver<Identifier>() {
          public void receiveException(P2PSocket<Identifier> socket, Exception ioe) {
            c.receiveException(ioe);
          }
  
          public void receiveSelectResult(P2PSocket<Identifier> socket,
              boolean canRead, boolean canWrite) throws IOException {
            sslTL.logger.log("rsr2("+canRead+","+canWrite+")");
            if (canWrite) {
              socket.write(outgoing);
            }
            if (outgoing.hasRemaining()) {
              socket.register(false, true, this);
            } else {
              sslTL.logger.log("after write: ");
              go();
            }
          }        
        });
      } else if (status == HandshakeStatus.NEED_TASK) {
        final Runnable r = engine.getDelegatedTask();
        sslTL.environment.getProcessor().process(
            new Executable<Object, Exception>() {

              public Object execute() {
                sslTL.logger.log("Executing "+r);
                r.run();
                sslTL.logger.log("Done executing "+r);                
                return null;
              }}, 
            new Continuation<Object, Exception>() {

              public void receiveException(Exception exception) {
                c.receiveException(exception);
              }

              public void receiveResult(Object result) {
                sslTL.logger.log("Calling go() after "+r);
                go();
              }}, 
            sslTL.environment.getSelectorManager(), sslTL.environment.getTimeSource(), sslTL.environment.getLogManager());
      }
    } catch (IOException ioe) {
      c.receiveException(ioe);
    }
  }
  
  public void register(boolean wantToRead, boolean wantToWrite,
      P2PSocketReceiver<Identifier> receiver) {
    throw new RuntimeException("implement");
//    socket.register(wantToRead, wantToWrite, )
  }

  public long read(ByteBuffer dsts) throws IOException {
    // TODO Auto-generated method stub
    return 0;
  }

  public long write(ByteBuffer srcs) throws IOException {
    // TODO Auto-generated method stub
    return 0;
  }


  public void close() {
    socket.close();
  }

  public Identifier getIdentifier() {
    return socket.getIdentifier();
  }

  public Map<String, Object> getOptions() {
    return socket.getOptions();
  }

  public void shutdownOutput() {
    engine.closeOutbound();
  }

}
