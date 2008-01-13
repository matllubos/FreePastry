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
package org.mpisws.p2p.transport.wire.magicnumber;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Map;

import org.mpisws.p2p.transport.MessageRequestHandle;
import org.mpisws.p2p.transport.SocketRequestHandle;
import org.mpisws.p2p.transport.ErrorHandler;
import org.mpisws.p2p.transport.MessageCallback;
import org.mpisws.p2p.transport.P2PSocket;
import org.mpisws.p2p.transport.P2PSocketReceiver;
import org.mpisws.p2p.transport.SocketCallback;
import org.mpisws.p2p.transport.TransportLayer;
import org.mpisws.p2p.transport.TransportLayerCallback;
import org.mpisws.p2p.transport.util.MessageRequestHandleImpl;
import org.mpisws.p2p.transport.util.SocketRequestHandleImpl;
import org.mpisws.p2p.transport.util.DefaultCallback;
import org.mpisws.p2p.transport.util.DefaultErrorHandler;
import org.mpisws.p2p.transport.wire.exception.StalledSocketException;

import rice.environment.Environment;
import rice.environment.logging.Logger;
import rice.p2p.commonapi.Cancellable;
import rice.selector.TimerTask;

/**
 * This class eliminates random connections by dropping all messages/sockets that don't
 * correlate to a specific header.  It is the simplest example of how to add a new 
 * layer into the mpisws transport layer system.
 * 
 * @author Jeff Hoye
 *
 */
public class MagicNumberTransportLayer<Identity> implements 
    TransportLayer<Identity, ByteBuffer>, 
    TransportLayerCallback<Identity, ByteBuffer> {
  
  protected Logger logger;
  protected Environment environment;
  
  protected TransportLayerCallback<Identity, ByteBuffer> callback;
  protected ErrorHandler<Identity> errorHandler;
  protected TransportLayer<Identity, ByteBuffer> wire;
  
  public byte[] HEADER;
  public int SOCKET_TIMEOUT;
  
  /**
   * 
   * @param wtl
   * @param env
   * @param callback notify me of new sockets/messages
   * @param errorHandler call me when there is a problem
   * @param header the header to expect from a socket/packet
   * @param timeOut how long to wait before killing a socket that is not sending (milliseconds)
   */
  public MagicNumberTransportLayer(TransportLayer<Identity, ByteBuffer> wtl, 
      Environment env, 
      ErrorHandler<Identity> errorHandler, 
      byte[] header,
      int timeOut) {
    this.logger = env.getLogManager().getLogger(MagicNumberTransportLayer.class, null);
    this.environment = env;
    this.wire = wtl;
    this.HEADER = header;
    this.SOCKET_TIMEOUT = timeOut;
    
    this.errorHandler = errorHandler;
    
    this.callback = new DefaultCallback<Identity, ByteBuffer>(logger);
    
    if (this.errorHandler == null) {
      this.errorHandler = new DefaultErrorHandler<Identity>(logger); 
    }
    
    wire.setCallback(this);
  }

  public void setCallback(TransportLayerCallback<Identity, ByteBuffer> callback) {
    this.callback = callback;
  }

  public void setErrorHandler(ErrorHandler<Identity> handler) {
    wire.setErrorHandler(handler);
    if (handler == null) {
      this.errorHandler = new DefaultErrorHandler<Identity>(logger);
      return;
    }
    this.errorHandler = handler;
  }

  public void acceptMessages(boolean b) {
    wire.acceptMessages(b);
  }

  public void acceptSockets(boolean b) {
    wire.acceptSockets(b);
  }

  public Identity getLocalIdentifier() {
    return wire.getLocalIdentifier();
  }

  public SocketRequestHandle openSocket(final Identity i, 
      final SocketCallback<Identity> deliverSocketToMe, 
      Map<String, Object> options) {
    if (deliverSocketToMe == null) throw new IllegalArgumentException("deliverSocketToMe must be non-null!");

    final SocketRequestHandleImpl<Identity> cancellable = new SocketRequestHandleImpl<Identity>(i, options, logger);

    cancellable.setSubCancellable(wire.openSocket(i, new SocketCallback<Identity>(){    
      public void receiveResult(SocketRequestHandle<Identity> c, final P2PSocket<Identity> result) {
        if (cancellable.getSubCancellable() != null && c != cancellable.getSubCancellable()) throw new RuntimeException("c != cancellable.getSubCancellable() (indicates a bug in the code) c:"+c+" sub:"+cancellable.getSubCancellable());
        
        cancellable.setSubCancellable(new Cancellable() {        
          public boolean cancel() {
            result.close();
            return true;
          }        
        });
        
        result.register(false, true, new P2PSocketReceiver<Identity>(){        
          ByteBuffer buf = ByteBuffer.wrap(HEADER);
          public void receiveSelectResult(P2PSocket<Identity> socket, boolean canRead, boolean canWrite) throws IOException {
            if (canRead) throw new IOException("Never asked to read!");
            if (!canWrite) throw new IOException("Can't write!");
            long ret = socket.write(buf);
            if (ret < 0) {
              socket.close();
              return;
            }
            if (buf.hasRemaining()) {
              socket.register(false, true, this);
            } else {
              deliverSocketToMe.receiveResult(cancellable, socket);
            }
          }        
          public void receiveException(P2PSocket<Identity> socket, IOException e) {
            deliverSocketToMe.receiveException(cancellable, e);
          }
        });
      }    
      public void receiveException(SocketRequestHandle<Identity> c, IOException exception) {
        if (cancellable.getSubCancellable() != null && c != cancellable.getSubCancellable()) throw new RuntimeException("c != cancellable.getSubCancellable() (indicates a bug in the code) c:"+c+" sub:"+cancellable.getSubCancellable());
        deliverSocketToMe.receiveException(cancellable, exception);
//        errorHandler.receivedException(i, exception);
      }    
    }, options));
    
    return cancellable;
  }

  public MessageRequestHandle<Identity, ByteBuffer> sendMessage(
      final Identity i, 
      final ByteBuffer m, 
      final MessageCallback<Identity, ByteBuffer> deliverAckToMe, 
      Map<String, Object> options) {
    
    // build a new ByteBuffer with the header
    byte[] msgWithHeader = new byte[HEADER.length+m.remaining()];
    System.arraycopy(HEADER, 0, msgWithHeader, 0, HEADER.length);
    m.get(msgWithHeader, HEADER.length, m.remaining());
    
    if (logger.level <= Logger.FINE) logger.log("sendMessage("+i+","+m+")");

    final MessageRequestHandleImpl<Identity, ByteBuffer> cancellable 
      = new MessageRequestHandleImpl<Identity, ByteBuffer>(i, m, options);

    final ByteBuffer buf = ByteBuffer.wrap(msgWithHeader);
    cancellable.setSubCancellable(wire.sendMessage(i, 
        buf, 
        new MessageCallback<Identity, ByteBuffer>() {
        
          public void ack(MessageRequestHandle<Identity, ByteBuffer> msg) {
            if (cancellable.getSubCancellable() != null && msg != cancellable.getSubCancellable()) throw new RuntimeException("msg != cancellable.getSubCancellable() (indicates a bug in the code) msg:"+msg+" sub:"+cancellable.getSubCancellable());
            if (deliverAckToMe != null) deliverAckToMe.ack(cancellable);
          }
        
          public void sendFailed(MessageRequestHandle<Identity, ByteBuffer> msg, Exception ex) {
            if (cancellable.getSubCancellable() != null && msg != cancellable.getSubCancellable()) throw new RuntimeException("msg != cancellable.getSubCancellable() (indicates a bug in the code) msg:"+msg+" sub:"+cancellable.getSubCancellable());
            if (deliverAckToMe == null) {
              errorHandler.receivedException(i, ex);
            } else {
              deliverAckToMe.sendFailed(cancellable, ex);
            }
          }
        }, 
        options));
    
    return cancellable;
  }

  public void destroy() {
    wire.destroy();
  }

  public void incomingSocket(P2PSocket<Identity> s) throws IOException {
    s.register(true, false, new VerifyHeaderReceiver(s));
  }

  public void messageReceived(Identity i, ByteBuffer m, Map<String, Object> options) throws IOException {
    if (logger.level <= Logger.FINE) logger.log("messageReceived("+i+","+m+")");

    if (m.remaining() < HEADER.length) {
      errorHandler.receivedUnexpectedData(i, m.array(), 0, null);
      return;
    }
    byte[] hdr = new byte[HEADER.length];
    
    m.get(hdr);
    
    if (Arrays.equals(HEADER, hdr)) {
      callback.messageReceived(i, m, options); 
      return;
    }
    
    errorHandler.receivedUnexpectedData(i, m.array(), 0, null);
  }
  
  protected class VerifyHeaderReceiver extends TimerTask implements P2PSocketReceiver<Identity> {
    ByteBuffer buf = ByteBuffer.allocate(HEADER.length);
    
    P2PSocket<Identity> socket;
    public VerifyHeaderReceiver(P2PSocket<Identity> s) {
      this.socket = s;
      environment.getSelectorManager().getTimer().schedule(this,SOCKET_TIMEOUT);      
    }
    
    public void receiveException(P2PSocket<Identity> socket, IOException ioe) {
      errorHandler.receivedException(socket.getIdentifier(), ioe);
      // TODO Auto-generated method stub      
    }

    public void receiveSelectResult(P2PSocket<Identity> socket, boolean canRead, boolean canWrite) throws IOException {
      // TODO: Optimization: Check array at each step, to fail faster
      // TODO: Make timeout/cancellable
      if (canWrite) throw new IOException("Never asked to write!");
      if (!canRead) throw new IOException("Can't read!");
      if (socket.read(buf) < 0) {
        socket.close();
      }
      if (buf.hasRemaining()) {
        socket.register(true, false, this); 
      } else {
        if (Arrays.equals(HEADER, buf.array())) {
          // header matched
          cancel();
          callback.incomingSocket(socket);
        } else {    
          cancel();
          errorHandler.receivedUnexpectedData(socket.getIdentifier(), buf.array(), 0, null); 
        }
      }
    }

    @Override
    public void run() {
      socket.close();
      errorHandler.receivedException(socket.getIdentifier(), new StalledSocketException(socket.getIdentifier(), "Timeout on incoming socket expired."));
    }  
    
    public String toString() {
      return MagicNumberTransportLayer.this+" VHR";
    }
  }
}
