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
import java.security.cert.X509Certificate;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;

import org.mpisws.p2p.transport.ClosedChannelException;
import org.mpisws.p2p.transport.P2PSocket;
import org.mpisws.p2p.transport.P2PSocketReceiver;
import org.mpisws.p2p.transport.util.OptionsFactory;

import rice.Continuation;
import rice.Executable;
import static javax.net.ssl.SSLEngineResult.HandshakeStatus.*;

public class SSLSocketManager<Identifier> implements P2PSocket<Identifier>,
    P2PSocketReceiver<Identifier> {
  P2PSocket<Identifier> socket;

  SSLEngine engine;
  SSLTransportLayerImpl<Identifier, ?> sslTL;

  boolean handshaking = true;

  SSLEngineResult result;
  HandshakeStatus status;

//  LinkedList<ByteBuffer> encryptMe; // plain, outgoing
  LinkedList<ByteBuffer> writeMe = new LinkedList<ByteBuffer>(); // cipher, outgoing
  LinkedList<ByteBuffer> unwrapMe = new LinkedList<ByteBuffer>(); // cipher, incoming
  LinkedList<ByteBuffer> readMe; // plain, incoming
  

  ByteBuffer bogusEncryptMe;

  int appBufferMax;
  int netBufferMax;

  private Continuation<SSLSocketManager<Identifier>, Exception> c;

  boolean doneHandshaking = false;
  
  Map<String, Object> options;
  
  /**
   * Called on incoming side
   * 
   * @param transportLayerImpl
   * @param s
   */
  public SSLSocketManager(SSLTransportLayerImpl<Identifier, ?> sslTL,
      P2PSocket<Identifier> s,
      Continuation<SSLSocketManager<Identifier>, Exception> c, boolean server, boolean useClientAuth) {
    this.sslTL = sslTL;
    this.socket = s;
    this.c = c;

    engine = sslTL.context.createSSLEngine(s.getIdentifier().toString(), 0);
    engine.setUseClientMode(!server);
    if (server && useClientAuth) engine.setNeedClientAuth(true);

    // System.out.println(Arrays.toString(engine.getSupportedCipherSuites()));
    // engine.setEnabledCipherSuites(new String[]
    // {"TLS_DHE_DSS_WITH_AES_256_CBC_SHA"});
//    engine.setEnabledCipherSuites(engine.getSupportedCipherSuites());

    appBufferMax = engine.getSession().getApplicationBufferSize();
    netBufferMax = engine.getSession().getPacketBufferSize();

//    encryptMe = new LinkedList<ByteBuffer>();
    bogusEncryptMe = ByteBuffer.allocate(0);
    readMe = new LinkedList<ByteBuffer>();
    
//    sslTL.logger.log("app:"+appBufferMax+" net:"+netBufferMax);
    socket.register(true, false, this);

    handshakeWrap();
  }

  protected void handleResult(SSLEngineResult result) {
//    sslTL.logger.log("handleResult:"+result);
    this.result = result;
    this.status = result.getHandshakeStatus();
  }

  boolean handshakeFail = false;
  
  public void receiveSelectResult(P2PSocket<Identifier> socket,
      boolean canRead, boolean canWrite) throws IOException {
    if (handshakeFail) return;
//    sslTL.logger.log("receive select result r:"+canRead+" w:"+canWrite);
    if (canWrite) {
      Iterator<ByteBuffer> i = writeMe.iterator();
      while (i.hasNext()) {
        ByteBuffer b = i.next();
        socket.write(b);
        if (b.hasRemaining()) break;
        i.remove();
      }
      if (writeMe.isEmpty()) {
        if (registeredToWrite != null) {
          P2PSocketReceiver<Identifier> temp = registeredToWrite;
          registeredToWrite = null;
          temp.receiveSelectResult(this, false, true);       
        }
      } else {
        socket.register(false, true, this);        
      }
    }
    if (canRead) {
      if (doneHandshaking) {
        if (read()) {
          if (registeredToRead != null) {
            P2PSocketReceiver<Identifier> temp = registeredToRead;
            registeredToRead = null;
            temp.receiveSelectResult(this, true, false);                 
          }
        }
      } else {
        read();
        continueHandshaking();
      }
    }    
  }

  protected boolean read() throws IOException {
    ByteBuffer foo = ByteBuffer.allocate(netBufferMax);
    if (socket.read(foo) < 0) fail(new ClosedChannelException("Unexpected socket closure "+this));
    if (foo.position() != 0) {
      foo.flip();
      unwrapMe.addLast(foo);
      return true;
    }
    return false;
  }
  
  protected void handshakeWrap() {
      try {
        ByteBuffer outgoing = ByteBuffer.allocate(netBufferMax);
        handleResult(engine.wrap(bogusEncryptMe, outgoing));
//        sslTL.logger.log("client wrap: "+encryptMe+" "+result);
        if (outgoing.position() != 0) {
          outgoing.flip();
          writeMe.addLast(outgoing);
//          sslTL.logger.log("registering to write:"+outgoing);
          socket.register(false, true, this);
        }    
      } catch (SSLException e) {
        fail(e);
        return;
      }
      continueHandshaking();
  }

  protected void unwrap() throws SSLException{
    Iterator<ByteBuffer> i = unwrapMe.iterator();
    while (i.hasNext()) {
      ByteBuffer b = i.next();
      ByteBuffer foo = ByteBuffer.allocate(appBufferMax);
      handleResult(engine.unwrap(b, foo));
//      sslTL.logger.log("client unwrap: "+foo+" "+result);
      if (foo.position() != 0) {
        foo.flip();
        readMe.addLast(foo);
//        sslTL.logger.log("reading into " +decryptToMe);
      }
//      sslTL.logger.log("unwrapped:"+b);
      if (b.hasRemaining()) break;
      i.remove();
    }
  }
  
  protected void handshakeUnwrap() {
    try {
      if (unwrapMe.isEmpty()) {
        if (read()) {
        } else {
          socket.register(true, false, this);
          return; // wait for bytes
        }
      }
      unwrap();
    } catch (Exception e) {
      fail(e);
      return;
    }
    continueHandshaking();
  }
  
  protected void fail(Exception e) {
    sslTL.logger.log("fail:"+e);
    handshakeFail = true;
    c.receiveException(e);
    socket.close();
    doneHandshaking = true;
  }
  
  protected void continueHandshaking() {    
    if (runningTaskLock) {
//      sslTL.logger.log("go2: processing... bye.");
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
      handshakeWrap();
      return;
    case NEED_UNWRAP:
      handshakeUnwrap();
      return;
    }
  }

  protected void checkDone() {
    if (((status == HandshakeStatus.FINISHED) || (status == HandshakeStatus.NOT_HANDSHAKING))) {
      try {
        
        X509Certificate crt = ((X509Certificate) engine.getSession().getPeerCertificates()[0]);
        String name = crt.getSubjectDN().getName();
        if (name.startsWith("CN=")) {
          name = name.substring(3);
          options = OptionsFactory.addOption(socket.getOptions(), SSLTransportLayer.OPTION_CERT_SUBJECT, name);
///          sslTL.logger.log("Talking to:"+name);
        } else {
          fail(new IllegalArgumentException("CN must start with CN= "+name+" "+this));
          return;          
        }
      } catch (Exception e) {
        fail(e);
        return;
      }
      doneHandshaking = true;
      c.receiveResult(this);
    }
  }
  
  
  /*
   * If the result indicates that we have outstanding tasks to do, go ahead and
   * run them in this thread.
   */
  boolean runningTaskLock = false;

  private void runDelegatedTasks() {
    if (result.getHandshakeStatus() == HandshakeStatus.NEED_TASK) {
      runningTaskLock = true;
      sslTL.environment.getProcessor().process(new Executable<Object, Exception>() {
        
        public Object execute() throws Exception {
          // TODO Auto-generated method 
          Runnable runnable;
          while ((runnable = engine.getDelegatedTask()) != null) {
//            sslTL.logger.log("\trunning delegated task...");
            runnable.run();
          }
          status = engine.getHandshakeStatus();
          if (status == HandshakeStatus.NEED_TASK) {
            fail(new IOException("handshake shouldn't need additional tasks"));
            return null;
          }
//          sslTL.logger.log("\tnew HandshakeStatus: " + status);
          return null;
        }
      },new Continuation<Object, Exception>() {
        public void receiveException(Exception exception) {
          exception.printStackTrace();
        };
        public void receiveResult(Object result) {          
//          sslTL.logger.log("Done executing, calling go2");          
          runningTaskLock = false;
          continueHandshaking();
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
    if (wantToRead) {
      // try to read, decrypt
      if (!readMe.isEmpty()) {
        try {
          receiver.receiveSelectResult(this, true, false);
        } catch (IOException ioe) {
          receiver.receiveException(this, ioe);
        }
      } else {
        // TODO: check not already registered
        registeredToRead = receiver;
        socket.register(true, false, this);
      }
    }
    
    if (wantToWrite) {
      if (writeMe.isEmpty()) {
        try {
          receiver.receiveSelectResult(this, false, true);
        } catch (IOException ioe) {
          receiver.receiveException(this, ioe);
        }
      } else {
        // TODO: check not already registered
        registeredToWrite = receiver;
        socket.register(false, true, this);
      }
    }    
  }
  
  P2PSocketReceiver<Identifier> registeredToRead;
  P2PSocketReceiver<Identifier> registeredToWrite;
  
  public long read(ByteBuffer dsts) throws IOException {
    long start = dsts.position();
    unwrap();
    while(dsts.hasRemaining() && !readMe.isEmpty()) {
      dsts.put(readMe.getFirst());
      if (readMe.getFirst().hasRemaining()) {
        return dsts.position()-start;
      } else {
        readMe.removeFirst();
      }
    }
    
    // now try reading off the socket
    if (dsts.hasRemaining()) {
      if (read()) {
        unwrap();
        dsts.put(readMe.getFirst());
        if (readMe.getFirst().hasRemaining()) {
          return dsts.position()-start;
        } else {
          readMe.removeFirst();
        }
      }    
    }
    return dsts.position()-start;
  }

  public long write(ByteBuffer srcs) throws IOException {
    ByteBuffer outgoing = ByteBuffer.allocate(netBufferMax);
    SSLEngineResult tempResult = engine.wrap(srcs, outgoing);
    if (outgoing.position() != 0) {
      outgoing.flip();
      writeMe.addLast(outgoing);
      // try to write it to the wire
      receiveSelectResult(socket, false, true);
    }
    return tempResult.bytesConsumed();
  }

  public void close() {
    socket.close();
  }

  public Identifier getIdentifier() {
    return socket.getIdentifier();
  }

  public Map<String, Object> getOptions() {
    return options;
  }

  public void shutdownOutput() {
    engine.closeOutbound();
  }

  public void receiveException(P2PSocket<Identifier> socket, Exception ioe) {
    c.receiveException(ioe);
  }
}
