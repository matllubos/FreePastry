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
package org.mpisws.p2p.transport.networkinfo;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Map;

import org.mpisws.p2p.transport.ErrorHandler;
import org.mpisws.p2p.transport.MessageCallback;
import org.mpisws.p2p.transport.MessageRequestHandle;
import org.mpisws.p2p.transport.P2PSocket;
import org.mpisws.p2p.transport.P2PSocketReceiver;
import org.mpisws.p2p.transport.SocketCallback;
import org.mpisws.p2p.transport.SocketRequestHandle;
import org.mpisws.p2p.transport.TransportLayer;
import org.mpisws.p2p.transport.TransportLayerCallback;
import org.mpisws.p2p.transport.simpleidentity.InetSocketAddressSerializer;
import org.mpisws.p2p.transport.util.DefaultCallback;
import org.mpisws.p2p.transport.util.DefaultErrorHandler;
import org.mpisws.p2p.transport.util.InsufficientBytesException;
import org.mpisws.p2p.transport.util.SocketInputBuffer;
import org.mpisws.p2p.transport.util.SocketRequestHandleImpl;
import org.mpisws.p2p.transport.wire.magicnumber.MagicNumberTransportLayer;

import rice.Continuation;
import rice.environment.Environment;
import rice.environment.logging.Logger;
import rice.p2p.commonapi.Cancellable;
import rice.p2p.util.AttachableCancellable;
import rice.p2p.util.rawserialization.SimpleOutputBuffer;

/**
 * Can open a TCP connection to a known node, and it will return your IP address.  
 * 
 * Use getMyIpAddress()
 * 
 * header = 0; // bypass
 * header = 1; // return IP
 * 
 * @author Jeff Hoye
 *
 */
public class NetworkInfoTransportLayer implements 
    InetSocketAddressLookup, 
    TransportLayer<InetSocketAddress, ByteBuffer>, 
    TransportLayerCallback<InetSocketAddress, ByteBuffer> {
  protected Logger logger;
  protected Environment environment;
  
  protected TransportLayerCallback<InetSocketAddress, ByteBuffer> callback;
  protected ErrorHandler<InetSocketAddress> errorHandler;
  protected TransportLayer<InetSocketAddress, ByteBuffer> tl;

  protected static final byte HEADER_PASSTHROUGH_BYTE = (byte)0;
  protected static final byte HEADER_IP_PLEASE_BYTE = (byte)1;
  protected static final byte[] HEADER_PASSTHROUGH = {HEADER_PASSTHROUGH_BYTE};
  protected static final byte[] HEADER_IP_PLEASE = {HEADER_IP_PLEASE_BYTE};
  
  
  public NetworkInfoTransportLayer(TransportLayer<InetSocketAddress, ByteBuffer> tl, 
      Environment env, 
      ErrorHandler<InetSocketAddress> errorHandler) {
    this.logger = env.getLogManager().getLogger(MagicNumberTransportLayer.class, null);
    this.environment = env;
    this.tl= tl;
    
    this.errorHandler = errorHandler;
    
    if (this.errorHandler == null) {
      this.errorHandler = new DefaultErrorHandler<InetSocketAddress>(logger); 
    }
    
    tl.setCallback(this);
  }

  InetSocketAddressSerializer addrSerializer = new InetSocketAddressSerializer();
  
  public Cancellable getMyInetAddress(InetSocketAddress bootstrap, 
      final Continuation<InetSocketAddress, Exception> c, Map<String, Object> options) {
    AttachableCancellable ret = new AttachableCancellable();
    ret.attach(openSocket(bootstrap, HEADER_IP_PLEASE, new SocketCallback<InetSocketAddress>() {
    
      public void receiveResult(SocketRequestHandle<InetSocketAddress> cancellable,
          P2PSocket<InetSocketAddress> sock) {
        final SocketInputBuffer sib = new SocketInputBuffer(sock);
        
        try {
          new P2PSocketReceiver<InetSocketAddress>() {
            
            public void receiveSelectResult(P2PSocket<InetSocketAddress> socket,
                boolean canRead, boolean canWrite) throws IOException {
              // read IP address
              try {
                InetSocketAddress addr = addrSerializer.deserialize(sib, null, null);
                c.receiveResult(addr);
              } catch (InsufficientBytesException ibe) {
                socket.register(true, false, this);
              } catch (Exception e) {
                c.receiveException(e);
              }
            }
          
            public void receiveException(P2PSocket<InetSocketAddress> socket,
                Exception ioe) {
              c.receiveException(ioe);
            }
          
          }.receiveSelectResult(sock, true, false);        
        } catch (IOException ioe) {
          c.receiveException(ioe);
        }
      }
    
      public void receiveException(SocketRequestHandle<InetSocketAddress> s,
          Exception ex) {
        c.receiveException(ex);
      }    
    }, options));
    return ret;
  }
  
  public SocketRequestHandle<InetSocketAddress> openSocket(InetSocketAddress i,
      SocketCallback<InetSocketAddress> deliverSocketToMe,
      Map<String, Object> options) {
    return openSocket(i,HEADER_PASSTHROUGH,deliverSocketToMe,options);
  }
  
  public SocketRequestHandle<InetSocketAddress> openSocket(InetSocketAddress i, final byte[] header,
      final SocketCallback<InetSocketAddress> deliverSocketToMe,
      Map<String, Object> options) {

    if (deliverSocketToMe == null) throw new IllegalArgumentException("deliverSocketToMe must be non-null!");

    final SocketRequestHandleImpl<InetSocketAddress> cancellable = new SocketRequestHandleImpl<InetSocketAddress>(i, options, logger);

    cancellable.setSubCancellable(tl.openSocket(i, new SocketCallback<InetSocketAddress>(){    
      public void receiveResult(SocketRequestHandle<InetSocketAddress> c, final P2PSocket<InetSocketAddress> result) {
        if (cancellable.getSubCancellable() != null && c != cancellable.getSubCancellable()) throw new RuntimeException("c != cancellable.getSubCancellable() (indicates a bug in the code) c:"+c+" sub:"+cancellable.getSubCancellable());
        
        cancellable.setSubCancellable(new Cancellable() {        
          public boolean cancel() {
            result.close();
            return true;
          }        
        });
        
        result.register(false, true, new P2PSocketReceiver<InetSocketAddress>(){        
          ByteBuffer buf = ByteBuffer.wrap(header);
          public void receiveSelectResult(P2PSocket<InetSocketAddress> socket, boolean canRead, boolean canWrite) throws IOException {
            if (canRead) throw new IOException("Never asked to read!");
            if (!canWrite) throw new IOException("Can't write!");
            long ret = socket.write(buf);            
            if (ret < 0) {
              socket.close();
              return;
            }
//            notifyListenersWrite((int)ret, socket.getIdentifier(), socket.getOptions(), false, true);
            if (buf.hasRemaining()) {
              socket.register(false, true, this);
            } else {
              deliverSocketToMe.receiveResult(cancellable, socket);
            }
          }        
          public void receiveException(P2PSocket<InetSocketAddress> socket, Exception e) {
            deliverSocketToMe.receiveException(cancellable, e);
          }
        });
      }    
      public void receiveException(SocketRequestHandle<InetSocketAddress> c, Exception exception) {
        if (cancellable.getSubCancellable() != null && c != cancellable.getSubCancellable()) throw new RuntimeException("c != cancellable.getSubCancellable() (indicates a bug in the code) c:"+c+" sub:"+cancellable.getSubCancellable());
        deliverSocketToMe.receiveException(cancellable, exception);
//        errorHandler.receivedException(i, exception);
      }    
    }, options));
    
    return cancellable;
  }

  public void incomingSocket(P2PSocket<InetSocketAddress> s) throws IOException {
    new P2PSocketReceiver<InetSocketAddress>() {
      ByteBuffer bb = ByteBuffer.allocate(HEADER_PASSTHROUGH.length); 
      public void receiveSelectResult(P2PSocket<InetSocketAddress> socket,
          boolean canRead, boolean canWrite) throws IOException {
        if (socket.read(bb) < 0) {
          socket.close();
          return;
        }
        if(bb.hasRemaining()) {
          socket.register(true, false, this);    
          return;
        }
        
        // read the array
        byte[] ret = bb.array();
        if (ret.length > 1) throw new RuntimeException("Make this work over the array, implementation expectes header to be 1 byte.");
        switch (ret[0]) {
        case HEADER_PASSTHROUGH_BYTE:
          callback.incomingSocket(socket);
          return;
        case HEADER_IP_PLEASE_BYTE:
          // write out the caller's ip address
          SimpleOutputBuffer sob = new SimpleOutputBuffer();
          addrSerializer.serialize(socket.getIdentifier(), sob);          
          final ByteBuffer writeMe = sob.getByteBuffer();
          new P2PSocketReceiver<InetSocketAddress>() {           
            public void receiveSelectResult(P2PSocket<InetSocketAddress> socket,
                boolean canRead, boolean canWrite) throws IOException {
              if (socket.write(writeMe) < 0) {
                socket.close();
              }
              if (writeMe.hasRemaining()) {
                socket.register(false, true, this);
              } else {
                socket.close();
              }
            }
          
            public void receiveException(P2PSocket<InetSocketAddress> socket,
                Exception ioe) {
              // do nothing
            }
          
          }.receiveSelectResult(socket, false, true);
          return;
        default:
          // header didn't match up
          errorHandler.receivedUnexpectedData(socket.getIdentifier(), ret, 0, socket.getOptions());
        }
        
      }
    
      public void receiveException(P2PSocket<InetSocketAddress> socket,
          Exception ioe) {
        errorHandler.receivedException(socket.getIdentifier(), ioe);
      }    
    }.receiveSelectResult(s, true, false);
  }

  
  public void setCallback(TransportLayerCallback<InetSocketAddress, ByteBuffer> callback) {
    this.callback = callback;
  }

  public void setErrorHandler(ErrorHandler<InetSocketAddress> handler) {
    if (handler == null) {
      this.errorHandler = new DefaultErrorHandler<InetSocketAddress>(logger);
      return;
    }
    this.errorHandler = handler;
  }

  public void acceptMessages(boolean b) {
    tl.acceptMessages(b);
  }

  public void acceptSockets(boolean b) {
    tl.acceptSockets(b);
  }

  public InetSocketAddress getLocalIdentifier() {
    return tl.getLocalIdentifier();
  }

  public MessageRequestHandle<InetSocketAddress, ByteBuffer> sendMessage(
      InetSocketAddress i, ByteBuffer m,
      MessageCallback<InetSocketAddress, ByteBuffer> deliverAckToMe,
      Map<String, Object> options) {
    return tl.sendMessage(i, m, deliverAckToMe, options);
  }
  public void messageReceived(InetSocketAddress i, ByteBuffer m,
      Map<String, Object> options) throws IOException {
    callback.messageReceived(i, m, options);
  }


  public void destroy() {
    tl.destroy();    
  }
}
