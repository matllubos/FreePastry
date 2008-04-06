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
import java.util.HashMap;
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
import org.mpisws.p2p.transport.multiaddress.MultiInetSocketAddress;
import org.mpisws.p2p.transport.simpleidentity.InetSocketAddressSerializer;
import org.mpisws.p2p.transport.util.DefaultCallback;
import org.mpisws.p2p.transport.util.DefaultErrorHandler;
import org.mpisws.p2p.transport.util.InsufficientBytesException;
import org.mpisws.p2p.transport.util.MessageRequestHandleImpl;
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
    Prober,
    TransportLayer<InetSocketAddress, ByteBuffer>, 
    TransportLayerCallback<InetSocketAddress, ByteBuffer> {
  protected Logger logger;
  protected Environment environment;
  
  protected TransportLayerCallback<InetSocketAddress, ByteBuffer> callback;
  protected ErrorHandler<InetSocketAddress> errorHandler;
  protected TransportLayer<InetSocketAddress, ByteBuffer> tl;

  protected static final byte HEADER_PASSTHROUGH_BYTE = (byte)0;
  protected static final byte HEADER_IP_ADDRESS_REQUEST_BYTE = (byte)1;
  protected static final byte HEADER_PROBE_REQUEST_BYTE = (byte)2;
  protected static final byte HEADER_PROBE_RESPONSE_BYTE = (byte)3;
  protected static final byte[] HEADER_PASSTHROUGH = {HEADER_PASSTHROUGH_BYTE};
  protected static final byte[] HEADER_IP_ADDRESS_REQUEST = {HEADER_IP_ADDRESS_REQUEST_BYTE};
  
  
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
    ret.attach(openSocket(bootstrap, HEADER_IP_ADDRESS_REQUEST, new SocketCallback<InetSocketAddress>() {
    
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
        case HEADER_IP_ADDRESS_REQUEST_BYTE:
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
        case HEADER_PROBE_REQUEST_BYTE:
          handleProbeRequest(socket);
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

  public void handleProbeRequest(final P2PSocket<InetSocketAddress> socket) {
    // read addr, uid
    try {
      new P2PSocketReceiver<InetSocketAddress>() {
        SocketInputBuffer sib = new SocketInputBuffer(socket);
      
        public void receiveSelectResult(P2PSocket<InetSocketAddress> socket,
            boolean canRead, boolean canWrite) throws IOException {
          // try to read the stuff until it works or fails
          try {
            MultiInetSocketAddress addr = MultiInetSocketAddress.build(sib);
            long uid = sib.readLong();
            probeStrategy.requestProbe(addr, uid);
          } catch (InsufficientBytesException ibe) {    
            socket.register(true, false, this);
          }
        }
      
        public void receiveException(P2PSocket<InetSocketAddress> socket,
            Exception ioe) {
          // TODO Auto-generated method stub
      
        }
      
      }.receiveSelectResult(socket, true, false);
    } catch (IOException ioe) {
      errorHandler.receivedException(socket.getIdentifier(), ioe);
      socket.close();
    }
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

  /**
   * Set the PASSTHROUGH header
   */
  public MessageRequestHandle<InetSocketAddress, ByteBuffer> sendMessage(
      InetSocketAddress i, ByteBuffer m,
      final MessageCallback<InetSocketAddress, ByteBuffer> deliverAckToMe,
      Map<String, Object> options) {
    
    final MessageRequestHandleImpl<InetSocketAddress, ByteBuffer> ret = new MessageRequestHandleImpl<InetSocketAddress, ByteBuffer>(i,m,options);
    
    ByteBuffer passThrough = ByteBuffer.allocate(m.remaining()+1);
    passThrough.put(HEADER_PASSTHROUGH_BYTE);
    passThrough.put(m);
    passThrough.flip();
    
    MessageCallback<InetSocketAddress, ByteBuffer> myCallback = null;
    if (deliverAckToMe != null) {
      myCallback = new MessageCallback<InetSocketAddress, ByteBuffer>() {

        public void ack(MessageRequestHandle<InetSocketAddress, ByteBuffer> msg) {
          deliverAckToMe.ack(ret);
        }
        
        public void sendFailed(
            MessageRequestHandle<InetSocketAddress, ByteBuffer> msg,
            Exception reason) {
          deliverAckToMe.sendFailed(ret, reason);
        }
      };
    }
    ret.setSubCancellable(tl.sendMessage(i, passThrough, myCallback, options));
    return ret;
  }
  
  public void messageReceived(InetSocketAddress i, ByteBuffer m,
      Map<String, Object> options) throws IOException {
    byte header = m.get();
    switch(header) {
    case HEADER_PASSTHROUGH_BYTE:
      callback.messageReceived(i, m, options);
      return;
    case HEADER_PROBE_RESPONSE_BYTE:
      long uid = m.getLong();
      // No need to remove them from the table, this will get done in destroy()
      verifyConnectionRequests.get(uid).udpSuccess(i, null);
    }
  }

  public void destroy() {
    verifyConnectionRequests.clear();
    tl.destroy();    
  }

  /**
   * Ask this strategy to probe a requesting node, but from a 3rd party node
   */
  protected ProbeStrategy probeStrategy;
  public void setProbeStrategy(ProbeStrategy probeStrategy) {
    this.probeStrategy = probeStrategy;
  }

  Map<Long, ConnectivityResult> verifyConnectionRequests = new HashMap<Long, ConnectivityResult>();
  
  /**
   * ask probeAddress to call probeStrategy.requestProbe()
   */
  public Cancellable verifyConnectivity(MultiInetSocketAddress local,
      InetSocketAddress probeAddress, 
      final ConnectivityResult deliverResultToMe,
      Map<String, Object> options) {
    AttachableCancellable ret = new AttachableCancellable();

    final long uid = environment.getRandomSource().nextLong();
    
    if (logger.level <= Logger.FINE) logger.log("verifyConnectivity("+local+","+probeAddress+"):"+uid);

    synchronized(verifyConnectionRequests) {
      verifyConnectionRequests.put(uid, deliverResultToMe);
    }
    
    // header has the PROBE_REQUEST and uid
    SimpleOutputBuffer sob = new SimpleOutputBuffer();
    try {
      sob.writeByte(HEADER_PROBE_REQUEST_BYTE);
      local.serialize(sob);
      sob.writeLong(uid);
    } catch (IOException ioe) {
      // shouldn't happen
      synchronized(verifyConnectionRequests) {
        verifyConnectionRequests.remove(uid);
      }      
      deliverResultToMe.receiveException(ioe);
      return null;
    }
    
    // if they cancel, pull it from the table
    ret.attach(new Cancellable() {    
      public boolean cancel() {
        synchronized(verifyConnectionRequests) {
          verifyConnectionRequests.remove(uid);
        }
        return true;
      }    
    });
    
    ret.attach(openSocket(probeAddress, sob.getBytes(), new SocketCallback<InetSocketAddress>() {    
      public void receiveResult(SocketRequestHandle<InetSocketAddress> cancellable,
          P2PSocket<InetSocketAddress> sock) {
        // maybe we should read a response here, but I don't think it's important, just read to close
        
        sock.register(true, false, new P2PSocketReceiver<InetSocketAddress>() {
        
          public void receiveSelectResult(P2PSocket<InetSocketAddress> socket,
              boolean canRead, boolean canWrite) throws IOException {
            // we just want to record the socket closing
            long bytesRead = socket.read(ByteBuffer.allocate(1));
            
            if (bytesRead < 0) {
              // what we expect
              socket.close();
              return;
            }

            if (bytesRead == 0) {
              // weird, but just reregister
              socket.register(true,false,this);
              return;
            }
            
            if (bytesRead > 0) {
              // this shouldn't happen, it should be closed, reregister anyway
              if (logger.level <= Logger.WARNING) logger.log("Unexpected response on REQUEST_PROBE_SOCKET reregistering.");
              socket.register(true,false,this);              
              return;
            }            
          }
        
          public void receiveException(P2PSocket<InetSocketAddress> socket,
              Exception ioe) {
            deliverResultToMe.receiveException(ioe);
          }        
        });
        
//        final SocketInputBuffer sib = new SocketInputBuffer(sock);
//        
//        try {
//          new P2PSocketReceiver<InetSocketAddress>() {
//            
//            public void receiveSelectResult(P2PSocket<InetSocketAddress> socket,
//                boolean canRead, boolean canWrite) throws IOException {
//              // read response
//              
//              try {
//                InetSocketAddress addr = addrSerializer.deserialize(sib, null, null);
//                c.receiveResult(addr);
//              } catch (InsufficientBytesException ibe) {
//                socket.register(true, false, this);
//              } catch (Exception e) {
//                c.receiveException(e);
//              }
//            }
//          
//            public void receiveException(P2PSocket<InetSocketAddress> socket,
//                Exception ioe) {
//              c.receiveException(ioe);
//            }
//          
//          }.receiveSelectResult(sock, true, false);        
//        } catch (IOException ioe) {
//          c.receiveException(ioe);
//        }
      }
    
      public void receiveException(SocketRequestHandle<InetSocketAddress> s,
          Exception ex) {
        deliverResultToMe.receiveException(ex);
      }    
    }, options));
    return ret;
  }

  public Cancellable probe(InetSocketAddress addr, long uid, MessageCallback<InetSocketAddress, ByteBuffer> deliverResponseToMe, Map<String, Object> options) {
    ByteBuffer msg = ByteBuffer.allocate(9); // header+uid
    msg.put(HEADER_PROBE_RESPONSE_BYTE);
    msg.putLong(uid);
    return tl.sendMessage(addr, msg, deliverResponseToMe, options);
  }
}
