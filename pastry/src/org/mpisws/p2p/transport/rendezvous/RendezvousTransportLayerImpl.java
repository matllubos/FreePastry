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
package org.mpisws.p2p.transport.rendezvous;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import org.mpisws.p2p.transport.ClosedChannelException;
import org.mpisws.p2p.transport.ErrorHandler;
import org.mpisws.p2p.transport.MessageCallback;
import org.mpisws.p2p.transport.MessageRequestHandle;
import org.mpisws.p2p.transport.P2PSocket;
import org.mpisws.p2p.transport.P2PSocketReceiver;
import org.mpisws.p2p.transport.SocketCallback;
import org.mpisws.p2p.transport.SocketRequestHandle;
import org.mpisws.p2p.transport.TransportLayer;
import org.mpisws.p2p.transport.TransportLayerCallback;
import org.mpisws.p2p.transport.util.MessageRequestHandleImpl;
import org.mpisws.p2p.transport.util.SocketInputBuffer;
import org.mpisws.p2p.transport.util.SocketRequestHandleImpl;

import rice.Continuation;
import rice.environment.Environment;
import rice.environment.logging.Logger;
import rice.p2p.commonapi.rawserialization.InputBuffer;
import rice.p2p.util.tuples.MutableTuple;
import rice.p2p.util.tuples.Tuple;

/**
 * The trick here is that this layer is at some level, say InetSocketAddress, but must pass around very High-Level
 * Identifiers, such as a NodeHandle for the rendezvous strategy to do its job, but maybe this can just be the RendezvousContact, and it can be casted.
 * @author Jeff Hoye
 *
 * @param <Identifier>
 */
public class RendezvousTransportLayerImpl<Identifier, HighIdentifier extends RendezvousContact> implements 
    TransportLayer<Identifier, ByteBuffer>, TransportLayerCallback<Identifier, ByteBuffer>, PilotManager<HighIdentifier> {
  
  public static final byte NORMAL_SOCKET = 0; // used when normally opening a channel (bypassing rendezvous)
  public static final byte CONNECTOR_SOCKET = 1; // sent to the rendezvous server
  public static final byte ACCEPTOR_SOCKET = 2; // used when openChannel() is called
  public static final byte PILOT_SOCKET = 3; // used when openChannel() is called
  
  /**
   * TRUE if not Firewalled
   */
  boolean canContactDirect = true;
  
  /**
   * options.get(RENDEZVOUS_CONTACT_STRING) returns a RendezvousContact
   */
  public String RENDEZVOUS_CONTACT_STRING;  // usually: commonapi_destination_identity 
  
  TransportLayer<Identifier, ByteBuffer> tl;
  TransportLayerCallback<Identifier, ByteBuffer> callback;
  RendezvousGenerationStrategy<HighIdentifier> rendezvousGenerator;
  RendezvousStrategy<HighIdentifier> rendezvousStrategy;
  HighIdentifier myRendezvousContact;
  Logger logger;
  ContactDeserializer<Identifier, HighIdentifier> serializer;
  
  public RendezvousTransportLayerImpl(
      TransportLayer<Identifier, ByteBuffer> tl, 
      String RENDEZVOUS_CONTACT_STRING, 
      HighIdentifier myRendezvousContact,
      ContactDeserializer<Identifier, HighIdentifier> deserializer,
      RendezvousGenerationStrategy<HighIdentifier> rendezvousGenerator,
      RendezvousStrategy<HighIdentifier> rendezvousStrategy, 
      Environment env) {
    this.tl = tl;
    this.myRendezvousContact = myRendezvousContact;
    this.serializer = deserializer;
    this.RENDEZVOUS_CONTACT_STRING = RENDEZVOUS_CONTACT_STRING;
    this.rendezvousGenerator = rendezvousGenerator;
    this.rendezvousStrategy = rendezvousStrategy;
    
    this.logger = env.getLogManager().getLogger(RendezvousTransportLayerImpl.class, null);
    tl.setCallback(this);
  }
  
  /**
   * We may not be able to determine this from the get-go.
   * 
   * @param b
   */
  public void canContactDirect(boolean b) {
    canContactDirect = b; 
  }
  
  public SocketRequestHandle<Identifier> openSocket(Identifier i, final SocketCallback<Identifier> deliverSocketToMe, Map<String, Object> options) {
    if (logger.level <= Logger.FINE) logger.log("openSocket("+i+","+deliverSocketToMe+","+options+")");

    final SocketRequestHandle<Identifier> handle = new SocketRequestHandleImpl<Identifier>(i,options,logger);
    
    // TODO: throw proper exception if options == null, or !contains(R_C_S)
    final RendezvousContact contact = getHighIdentifier(options);

    if (contact == null || contact.canContactDirect()) {
      // write NORMAL_SOCKET and continue
      tl.openSocket(i, new SocketCallback<Identifier>(){
        public void receiveResult(SocketRequestHandle<Identifier> cancellable, P2PSocket<Identifier> sock) {
          sock.register(false, true, new P2PSocketReceiver<Identifier>() {
            ByteBuffer writeMe;
            {
              byte[] foo = {NORMAL_SOCKET};
              writeMe = ByteBuffer.wrap(foo);              
            }
            public void receiveSelectResult(P2PSocket<Identifier> socket, boolean canRead, boolean canWrite) throws IOException {
              long ret = socket.write(writeMe);
              if (ret < 0) {
                deliverSocketToMe.receiveException(handle, new ClosedChannelException("Socket was closed while rendezvous layer was trying to open a normal socket to "+socket));
                socket.close();
              }
              if (writeMe.hasRemaining()) {
                socket.register(false, true, this);
                return;
              }
              deliverSocketToMe.receiveResult(handle, socket);
            }
          
            public void receiveException(P2PSocket<Identifier> socket, IOException ioe) {
              deliverSocketToMe.receiveException(handle, ioe);
            }
          });
        }
        
        public void receiveException(SocketRequestHandle<Identifier> s, IOException ex) {
          deliverSocketToMe.receiveException(handle, ex);
        }
      }, options);
    } else {
      if (canContactDirect) {
        // request openChannel to me
      } else {
        // find rendezvous point (RP)
        // request openChannel to RP
        // open channel to the RP
      }
    }

    return handle;
  }
  protected HighIdentifier getHighIdentifier(Map<String, Object> options) {
    if (options == null) return null;
    return (HighIdentifier)options.get(RENDEZVOUS_CONTACT_STRING);
  }

  public void incomingSocket(P2PSocket<Identifier> s) throws IOException {
    if (logger.level <= Logger.FINE) logger.log("incomingSocket("+s+")");

    s.register(true, false, new P2PSocketReceiver<Identifier>() {

      public void receiveSelectResult(P2PSocket<Identifier> socket, boolean canRead, boolean canWrite) throws IOException {
        if (logger.level <= Logger.FINER) logger.log("incomingSocket("+socket+").rSR("+canRead+","+canWrite+")");
        // read byte, switch on it
        ByteBuffer buf = ByteBuffer.allocate(1);
        long bytesRead = socket.read(buf);
        
        if (bytesRead == 0) {
          // try again
          socket.register(true, false, this);
          return;
        }
        
        if (bytesRead < 0) {
          // input was closed
          socket.close();
          return;
        }
        
        // could check that bytesRead == 1, but we know it is
        buf.flip();
        byte socketType = buf.get();
        switch(socketType) {
        case NORMAL_SOCKET:          
          if (logger.level <= Logger.FINEST) logger.log("incomingSocket("+socket+").rSR("+canRead+","+canWrite+"):NORMAL");          
          callback.incomingSocket(socket);
          return;
        case CONNECTOR_SOCKET:
          // TODO: read the requested target, credentials, and route to it to establish a connection, which will respond as an ACCEPTOR
          InputBuffer sib = new SocketInputBuffer(socket,1024);
          HighIdentifier target = serializer.deserialize(sib);
          byte[] credentials = serializer.readCredentials(sib);
          rendezvousStrategy.openChannel(target, myRendezvousContact, credentials, null);
          // TODO: store credentials/target -> map
          // TODO: make a deliverResultToMe that closes the socket or returns some kind of error
          return;
        case ACCEPTOR_SOCKET:
          // read the credentials and match to the CONNECTOR, or Self, or wait
        }
      }
      
      public void receiveException(P2PSocket<Identifier> socket, IOException ioe) {
        // TODO Auto-generated method stub
        
      }
    });
  }

  /**
   * What to do if firewalled?
   *   ConnectRequest UDP only?  For now always use UDP_AND_TCP
   */
  public MessageRequestHandle<Identifier, ByteBuffer> sendMessage(Identifier i, ByteBuffer m, final MessageCallback<Identifier, ByteBuffer> deliverAckToMe, Map<String, Object> options) {
    if (logger.level <= Logger.FINE) logger.log("sendMessage("+i+","+m+","+deliverAckToMe+","+options+")");

    HighIdentifier high = getHighIdentifier(options);
    if (high == null || high.canContactDirect()) {
      // passthrough, need to allow for null during boostrap, we assume passthrough works
      return tl.sendMessage(i, m, deliverAckToMe, options);
    } else {
      // rendezvous
      final MessageRequestHandleImpl<Identifier, ByteBuffer> ret = new MessageRequestHandleImpl<Identifier, ByteBuffer>(i, m, options);
      MessageCallback<HighIdentifier, ByteBuffer> ack;
      if (deliverAckToMe == null) {
        ack = null;
      } else {
        ack = new MessageCallback<HighIdentifier, ByteBuffer>(){
          public void ack(MessageRequestHandle<HighIdentifier, ByteBuffer> msg) {
            deliverAckToMe.ack(ret);
          }
          public void sendFailed(MessageRequestHandle<HighIdentifier, ByteBuffer> msg, IOException reason) {
            deliverAckToMe.sendFailed(ret, reason);
          }
        };
      }
      ret.setSubCancellable(rendezvousStrategy.sendMessage(high, m, ack, options));
      return ret;
    }
  }
  
  public void messageReceived(Identifier i, ByteBuffer m, Map<String, Object> options) throws IOException {
    if (logger.level <= Logger.FINE) logger.log("messageReceived("+i+","+m+","+options+")");
    callback.messageReceived(i, m, options);
  }
  
  public void acceptMessages(boolean b) {
    tl.acceptMessages(b);
  }
  public void acceptSockets(boolean b) {
    tl.acceptSockets(b);
  }
  public Identifier getLocalIdentifier() {
    return tl.getLocalIdentifier();
  }
  public void setCallback(TransportLayerCallback<Identifier, ByteBuffer> callback) {
    this.callback = callback;
  }
  public void setErrorHandler(ErrorHandler<Identifier> handler) {
    // TODO Auto-generated method stub    
  }
  public void destroy() {
    tl.destroy();
  }

  Map<HighIdentifier, Tuple<SocketRequestHandle<Identifier>, P2PSocket<Identifier>>> pilots = 
    new HashMap<HighIdentifier, Tuple<SocketRequestHandle<Identifier>, P2PSocket<Identifier>>>();
  
  public SocketRequestHandle<HighIdentifier> openPilot(final HighIdentifier i, 
      final Continuation<SocketRequestHandle<HighIdentifier>, IOException> deliverAckToMe) {    
    if (pilots.containsKey(i)) {
      return null; 
    }

    final SocketRequestHandle<HighIdentifier> ret = new SocketRequestHandleImpl<HighIdentifier>(i,null,logger);
    
    final MutableTuple<SocketRequestHandle<Identifier>, P2PSocket<Identifier>> tuple = 
      new MutableTuple<SocketRequestHandle<Identifier>, P2PSocket<Identifier>>();
    
    tuple.setA(tl.openSocket(serializer.convert(i), new SocketCallback<Identifier>(){
      public void receiveResult(SocketRequestHandle<Identifier> cancellable, P2PSocket<Identifier> sock) {
        tuple.setB(sock);
        if (deliverAckToMe != null) deliverAckToMe.receiveResult(ret);
      }
    
      public void receiveException(SocketRequestHandle<Identifier> s, IOException ex) {
        if (deliverAckToMe != null) deliverAckToMe.receiveException(ex);
      }
    }, serializer.getOptions(i)));    
    
    return ret;
  }  
  
  public void closePilot(HighIdentifier i) {
    Tuple<SocketRequestHandle<Identifier>, P2PSocket<Identifier>> closeMe = pilots.remove(i);
    if (closeMe != null) {
      SocketRequestHandle<Identifier> deadHandle = closeMe.a();
      P2PSocket<Identifier> deadSocket = closeMe.b();
      if (deadSocket == null) {
        // the socket hasn't come back, so cancel the task
        deadHandle.cancel();
      } else {
        deadSocket.close();
      }
    }
  }

}
