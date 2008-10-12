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
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;

import org.mpisws.p2p.transport.P2PSocket;
import org.mpisws.p2p.transport.P2PSocketReceiver;

import rice.Continuation;
import rice.Executable;
import rice.p2p.util.MathUtils;
import static javax.net.ssl.SSLEngineResult.HandshakeStatus.*;

public class SSLSocketManager<Identifier> implements P2PSocket<Identifier>,
    P2PSocketReceiver<Identifier> {
  P2PSocket<Identifier> socket;

  SSLEngine engine;
  SSLTransportLayerImpl<Identifier, ?> sslTL;

  boolean handshaking = true;

  SSLEngineResult result;
  HandshakeStatus status;

  // plaintext
  ByteBuffer encryptMe;
  ByteBuffer decryptToMe;

  // ciphertext
  LinkedList<ByteBuffer> unwrapMe = new LinkedList<ByteBuffer>();
  LinkedList<ByteBuffer> writeMe = new LinkedList<ByteBuffer>();

  public static final byte[] handshakePhrase = new byte[0]; //MathUtils.longToByteArray(-987);
//  public static final byte[] handshakePhrase = MathUtils.longToByteArray(-987);

  int appBufferMax;
  int netBufferMax;

  private Continuation<SSLSocketManager<Identifier>, Exception> c;

  boolean done = false;
  
  /**
   * Called on incoming side
   * 
   * @param transportLayerImpl
   * @param s
   */
  public SSLSocketManager(SSLTransportLayerImpl<Identifier, ?> sslTL,
      P2PSocket<Identifier> s,
      Continuation<SSLSocketManager<Identifier>, Exception> c, boolean server) {
    this.sslTL = sslTL;
    this.socket = s;
    this.c = c;

    engine = sslTL.context.createSSLEngine(s.getIdentifier().toString(), 0);
    engine.setUseClientMode(!server);
    if (server) engine.setNeedClientAuth(true);

    // System.out.println(Arrays.toString(engine.getSupportedCipherSuites()));
    // engine.setEnabledCipherSuites(new String[]
    // {"TLS_DHE_DSS_WITH_AES_256_CBC_SHA"});
//    engine.setEnabledCipherSuites(engine.getSupportedCipherSuites());

    appBufferMax = engine.getSession().getApplicationBufferSize();
    netBufferMax = engine.getSession().getPacketBufferSize();

    encryptMe = ByteBuffer.wrap(handshakePhrase);
    decryptToMe = ByteBuffer.allocate(appBufferMax+handshakePhrase.length);
    
    sslTL.logger.log("app:"+appBufferMax+" net:"+netBufferMax);
    socket.register(true, false, this);

    wrap();
  }

  protected void handleResult(SSLEngineResult result) {
    this.result = result;
    this.status = result.getHandshakeStatus();
  }
  
  public void receiveSelectResult(P2PSocket<Identifier> socket,
      boolean canRead, boolean canWrite) throws IOException {
    sslTL.logger.log("receive select result r:"+canRead+" w:"+canWrite);
    if (canRead) {
      read();
    }
    
    if (canWrite) {
      Iterator<ByteBuffer> i = writeMe.iterator();
      while (i.hasNext()) {
        ByteBuffer b = i.next();
        socket.write(b);
        if (b.hasRemaining()) break;
        i.remove();
      }
      if (!writeMe.isEmpty()) socket.register(false, true, this);
    }
    go2();
  }

  protected boolean read() throws IOException {
//    if (!done && canRead) {
      ByteBuffer foo = ByteBuffer.allocate(netBufferMax);
      socket.read(foo);
      if (foo.position() != 0) {
        foo.flip();
        unwrapMe.addLast(foo);
        return true;
      }
      // always be reading
//      socket.register(true, false, this);
//      go2();
//    }    
      return false;
  }
  
  protected void wrap() {
    try {
      ByteBuffer outgoing = ByteBuffer.allocate(netBufferMax);
      handleResult(engine.wrap(encryptMe, outgoing));
      sslTL.logger.log("client wrap: "+encryptMe+" "+result);
      if (outgoing.position() != 0) {
        outgoing.flip();
        writeMe.addLast(outgoing);
        sslTL.logger.log("registering to write:"+outgoing);
        socket.register(false, true, this);
      }    
    } catch (SSLException e) {
      fail(e);
      return;
    }
    go2();
  }
  
  protected void unwrap() {
    try {
      if (unwrapMe.isEmpty()) {
        if (read()) {
        } else {
          socket.register(true, false, this);
          return; // wait for bytes
        }
      }
      Iterator<ByteBuffer> i = unwrapMe.iterator();
      while (i.hasNext()) {
        ByteBuffer b = i.next();
        handleResult(engine.unwrap(b, decryptToMe));
        sslTL.logger.log("client unwrap: "+decryptToMe+" "+result);
        if (decryptToMe.position() != 0) {
          sslTL.logger.log("reading into " +decryptToMe);
        }
        sslTL.logger.log("unwrapped:"+b);
        if (b.hasRemaining()) break;
        i.remove();
      }
    } catch (Exception e) {
      fail(e);
      return;
    }
    go2();
  }
  
  protected void fail(Exception e) {
    c.receiveException(e);
    done = true;
  }
  
  protected void go2() {    
    if (lock) {
      sslTL.logger.log("go2: processing... bye.");
      return; // wait for processing to finish
    }
    switch(status) {
    case NOT_HANDSHAKING:
      return;
    case FINISHED:
      checkDone();
      return;
    case NEED_TASK:
      runDelegatedTasks();
    case NEED_WRAP:
      wrap();
      return;
    case NEED_UNWRAP:
      unwrap();
      return;
    }

//   try {
//    sslTL.logger.log("================");
//  
//    sslTL.logger.log("----");
//   checkDone();

//   if (!unwrapMe.isEmpty() || 
//       (result.getHandshakeStatus() == HandshakeStatus.NEED_WRAP) ||
//       (result.getHandshakeStatus() == HandshakeStatus.NEED_TASK)) go2();
  }

  protected void checkDone() {
    sslTL.logger.log("checkDone():"+status+" "+decryptToMe+" "+encryptMe);
    if (((status == HandshakeStatus.FINISHED) || (status == HandshakeStatus.NOT_HANDSHAKING)) &&
        decryptToMe.position() >= handshakePhrase.length &&
        !encryptMe.hasRemaining()) {
      done = true;
      sslTL.logger.log("Done:"+decryptToMe+" "+encryptMe);
      c.receiveResult(this);
    }
  }
  
  
  /*
   * If the result indicates that we have outstanding tasks to do, go ahead and
   * run them in this thread.
   */
  boolean lock = false;

  private void runDelegatedTasks() {
    if (result.getHandshakeStatus() == HandshakeStatus.NEED_TASK) {
      lock = true;
      sslTL.environment.getProcessor().process(new Executable() {
        
        public Object execute() throws Exception {
          // TODO Auto-generated method 
          Runnable runnable;
          while ((runnable = engine.getDelegatedTask()) != null) {
            sslTL.logger.log("\trunning delegated task...");
            runnable.run();
          }
          status = engine.getHandshakeStatus();
          if (status == HandshakeStatus.NEED_TASK) {
            throw new RuntimeException("handshake shouldn't need additional tasks");
          }
          sslTL.logger.log("\tnew HandshakeStatus: " + status);
          return null;
        }
      },new Continuation() {
        public void receiveException(Exception exception) {
          exception.printStackTrace();
        };
        public void receiveResult(Object result) {          
          sslTL.logger.log("Done executing, calling go2");          
          lock = false;
          go2();
        };
      }
      , sslTL.environment.getSelectorManager(), sslTL.environment.getTimeSource(), sslTL.environment.getLogManager());
    }
  }

  private static boolean isEngineClosed(SSLEngine engine) {
    return (engine.isOutboundDone() && engine.isInboundDone());
  }

  public void register(boolean wantToRead, boolean wantToWrite,
      P2PSocketReceiver<Identifier> receiver) {
    throw new RuntimeException("implement");
    // socket.register(wantToRead, wantToWrite, )
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

  public void receiveException(P2PSocket<Identifier> socket, Exception ioe) {
    c.receiveException(ioe);
  }
}
