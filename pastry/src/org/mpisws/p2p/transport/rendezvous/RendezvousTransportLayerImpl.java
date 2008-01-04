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
import java.util.LinkedList;
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
import org.mpisws.p2p.transport.util.InsufficientBytesException;
import org.mpisws.p2p.transport.util.MessageRequestHandleImpl;
import org.mpisws.p2p.transport.util.SocketInputBuffer;
import org.mpisws.p2p.transport.util.SocketRequestHandleImpl;

import rice.Continuation;
import rice.environment.Environment;
import rice.environment.logging.Logger;
import rice.p2p.commonapi.rawserialization.InputBuffer;
import rice.p2p.util.rawserialization.SimpleOutputBuffer;
import rice.p2p.util.tuples.MutableTuple;
import rice.p2p.util.tuples.Tuple;
import rice.selector.SelectorManager;
import rice.selector.TimerTask;

/**
 * The trick here is that this layer is at some level, say InetSocketAddress, but must pass around very High-Level
 * Identifiers, such as a NodeHandle for the rendezvous strategy to do its job, but maybe this can just be the RendezvousContact, and it can be casted.
 * 
 * protocol:
 * byte CONNECTOR_SOCKET
 *   HighIdentifier target = serializer.deserialize(sib);
 *   HighIdentifier opener = serializer.deserialize(sib);
 *   int uid = sib.readInt();
 * 
 * @author Jeff Hoye
 *
 * @param <Identifier>
 */
public class RendezvousTransportLayerImpl<Identifier, HighIdentifier extends RendezvousContact> implements 
    TransportLayer<Identifier, ByteBuffer>, TransportLayerCallback<Identifier, ByteBuffer>, PilotManager<HighIdentifier> {
  
  public static final byte NORMAL_SOCKET = 0; // used when normally opening a channel (bypassing rendezvous)
  public static final byte CONNECTOR_SOCKET = 1; // sent to the rendezvous server
  public static final byte ACCEPTOR_SOCKET = 2; // used when openChannel() is called
  public static final byte PILOT_SOCKET = 3; // forms a pilot connection 
  
  /**
   * TRUE if not Firewalled
   */
  boolean canContactDirect = true;
  
  /**
   * Value should be a HighIdentifier
   */
  public static final String OPTION_USE_PILOT = "USE_PILOT";
  
  /**
   * options.get(RENDEZVOUS_CONTACT_STRING) returns a RendezvousContact
   */
  public String RENDEZVOUS_CONTACT_STRING;  // usually: commonapi_destination_identity 
  
  TransportLayer<Identifier, ByteBuffer> tl;
  TransportLayerCallback<Identifier, ByteBuffer> callback;
  RendezvousGenerationStrategy<HighIdentifier> rendezvousGenerator;
  PilotFinder pilotFinder;
  RendezvousStrategy<HighIdentifier> rendezvousStrategy;
  HighIdentifier localNodeHandle;
  Logger logger;
  ContactDeserializer<Identifier, HighIdentifier> serializer;
  protected SelectorManager selectorManager;
  
  public RendezvousTransportLayerImpl(
      TransportLayer<Identifier, ByteBuffer> tl, 
      String RENDEZVOUS_CONTACT_STRING, 
      HighIdentifier myRendezvousContact,
      ContactDeserializer<Identifier, HighIdentifier> deserializer,
      RendezvousGenerationStrategy<HighIdentifier> rendezvousGenerator,
      PilotFinder pilotFinder,
      RendezvousStrategy<HighIdentifier> rendezvousStrategy, 
      Environment env) {
    this.selectorManager = env.getSelectorManager();
    this.tl = tl;
    this.localNodeHandle = myRendezvousContact;
    this.serializer = deserializer;
    this.RENDEZVOUS_CONTACT_STRING = RENDEZVOUS_CONTACT_STRING;
    this.rendezvousGenerator = rendezvousGenerator;
    this.pilotFinder = pilotFinder;
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
    if (logger.level <= Logger.FINEST) logger.log("openSocket("+i+","+deliverSocketToMe+","+options+")");

    final SocketRequestHandle<Identifier> handle = new SocketRequestHandleImpl<Identifier>(i,options,logger);
    
    // TODO: throw proper exception if options == null, or !contains(R_C_S)
    final HighIdentifier contact = getHighIdentifier(options);

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
      if (options.containsKey(OPTION_USE_PILOT)) {
        HighIdentifier middleMan = (HighIdentifier)options.get(OPTION_USE_PILOT);
        // this is normally used when a node is joining, wo you can't route to
        logger.log("OPTION_USE_PILOT->"+middleMan);        
        openSocketViaPilot(contact, middleMan, handle, deliverSocketToMe, options);
        return handle;
      } else {
        if (canContactDirect) {
          // see if I have a pilot to the node already
          HighIdentifier middleMan = (HighIdentifier)pilotFinder.findPilot(contact);          
          if (middleMan == null) {
            // request openChannel to me
            logger.log("I need to contact "+contact+" via routing");
            // use rendezvousStrategy
          } else {
            // use middleman
            logger.log("I need to open a socket via "+middleMan); 
            openSocketViaPilot(contact, middleMan, handle, deliverSocketToMe, options);
            return handle;
          }
        } else {
          logger.log("I need to open a socket to "+contact+", but we're both firewalled!");
          // choose rendezvous point (RP)
          // request openChannel to RP
          // open channel to the RP
        }
      }
    }

    return handle;
  }
  
  protected void openSocketUsingPilotToMe() {
    throw new RuntimeException("Not implemented.");    
  }
  
  protected void openSocketViaPilot(
      final HighIdentifier dest, 
      HighIdentifier middleMan, 
      final SocketRequestHandle<Identifier> handle, 
      final SocketCallback<Identifier> deliverSocketToMe, 
      Map<String, Object> options) {

    // build header
    SimpleOutputBuffer sob = new SimpleOutputBuffer();
    try {
      sob.writeByte(CONNECTOR_SOCKET);
      serializer.serialize(dest, sob);
      serializer.serialize(localNodeHandle, sob);
    } catch (IOException ioe) {
      deliverSocketToMe.receiveException(handle, ioe);
    }

    final ByteBuffer buf = sob.getByteBuffer();

    // open the socket
    tl.openSocket(serializer.convert(middleMan), new SocketCallback<Identifier>() {
      public void receiveResult(SocketRequestHandle<Identifier> cancellable,
          P2PSocket<Identifier> sock) {

        try {
          new P2PSocketReceiver<Identifier>() {
  
            public void receiveSelectResult(P2PSocket<Identifier> socket,
                boolean canRead, boolean canWrite) throws IOException {
              long bytesWritten = socket.write(buf); 
              if (bytesWritten < 0) {
                deliverSocketToMe.receiveException(handle, new ClosedChannelException("Channel closed detected in "+RendezvousTransportLayerImpl.this));
                return;
              }
              if (buf.hasRemaining()) {
                socket.register(false, true, this);
                return;
              }
              
              deliverSocketToMe.receiveResult(handle, socket);
            }
          
            public void receiveException(P2PSocket<Identifier> socket,
                IOException ioe) {
              deliverSocketToMe.receiveException(handle, ioe);
            }
          }.receiveSelectResult(sock, false, true);
        } catch (IOException ioe) {
          deliverSocketToMe.receiveException(handle, ioe);
        }
      }
      public void receiveException(SocketRequestHandle<Identifier> s,
          IOException ex) {
        deliverSocketToMe.receiveException(handle, ex);
      }
    }, options);
    
    throw new RuntimeException("Not implemented.");
  }
  
  protected void routeForSocket() {
    throw new RuntimeException("Not implemented.");    
  }
  
  protected HighIdentifier getHighIdentifier(Map<String, Object> options) {
    if (options == null) return null;
    return (HighIdentifier)options.get(RENDEZVOUS_CONTACT_STRING);
  }

  public void incomingSocket(P2PSocket<Identifier> s) throws IOException {
    if (logger.level <= Logger.FINEST) logger.log("incomingSocket("+s+")");

    s.register(true, false, new P2PSocketReceiver<Identifier>() {

      public void receiveSelectResult(P2PSocket<Identifier> socket, boolean canRead, boolean canWrite) throws IOException {
        if (logger.level <= Logger.FINEST) logger.log("incomingSocket("+socket+").rSR("+canRead+","+canWrite+")");
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
          // TODO: read the requested target, etc, and route to it to establish a connection, which will respond as an ACCEPTOR
          // TODO: make this recover from errors when sib doesn't have enough data, needs to reset(), reregister to read, probably should just do this in its own class
          InputBuffer sib;
          sib = new SocketInputBuffer(socket,1024);         
          HighIdentifier target = serializer.deserialize(sib);
          HighIdentifier opener = serializer.deserialize(sib);
          int uid = sib.readInt();
          rendezvousStrategy.openChannel(target, localNodeHandle, opener, uid, null);
          // TODO: store connection details in a map to this socket -> map
          // TODO: make a deliverResultToMe that closes the socket or returns some kind of error
          return;
        case ACCEPTOR_SOCKET:
          // read the connection details and match to the CONNECTOR, or Self, or wait
          return;
        case PILOT_SOCKET:
          new IncomingPilot(socket);
          return;
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
    if (logger.level <= Logger.FINEST) logger.log("sendMessage("+i+","+m+","+deliverAckToMe+","+options+")");

    HighIdentifier high = getHighIdentifier(options);
    if (high == null || high.canContactDirect()) {
      // pass-through, need to allow for null during bootstrap, we assume pass-through works
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
    if (logger.level <= Logger.FINEST) logger.log("messageReceived("+i+","+m+","+options+")");
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

  // *************Pilot Sockets (used to connect leafset members) ******************
  // *************** outgoing Pilots, only used by NATted nodes ********************
  Map<HighIdentifier, OutgoingPilot> outgoingPilots = 
    new HashMap<HighIdentifier, OutgoingPilot>();
  
  /**
   * Only used by NATted node.
   * 
   * Opens a pilot socket to a "lifeline" node.  These are usually nodes near the local node in the id space. 
   */
  public SocketRequestHandle<HighIdentifier> openPilot(final HighIdentifier i, 
      final Continuation<SocketRequestHandle<HighIdentifier>, IOException> deliverAckToMe) {    
    if (logger.level <= Logger.INFO) logger.log("openPilot("+i+")");
    if (outgoingPilots.containsKey(i)) {
      return outgoingPilots.get(i); 
    }

    Map<String, Object> options = serializer.getOptions(i);
    final OutgoingPilot o = new OutgoingPilot(i,options);
    outgoingPilots.put(i, o);
    
    o.setCancellable(tl.openSocket(serializer.convert(i), new SocketCallback<Identifier>(){
      public void receiveResult(SocketRequestHandle<Identifier> cancellable, P2PSocket<Identifier> sock) {
        o.setSocket(sock);
        if (deliverAckToMe != null) deliverAckToMe.receiveResult(o);
      }
    
      public void receiveException(SocketRequestHandle<Identifier> s, IOException ex) {
        o.receiveException(ex);
        if (deliverAckToMe != null) deliverAckToMe.receiveException(ex);
      }
    }, options));    
    
    return o;
  }  
  
  public void closePilot(HighIdentifier i) {
    if (logger.level <= Logger.INFO) logger.log("closePilot("+i+")");
    OutgoingPilot closeMe = outgoingPilots.remove(i);
    if (closeMe != null) {
      closeMe.cancel();
    }
  }
  
  public static final byte PILOT_PING = 1;
  public static final byte PILOT_PONG = 2;
  public static final byte PILOT_REQUEST = 3;

  public static final byte[] PILOT_PING_BYTES = {PILOT_PING};
  public static final byte[] PILOT_PONG_BYTES = {PILOT_PONG};
  public static final byte[] PILOT_SOCKET_BYTES = {PILOT_SOCKET};

  public static final int PILOT_PING_PERIOD = 5000; //60000;
  
  abstract class AbstractPilot extends TimerTask implements P2PSocketReceiver<Identifier> {
    protected P2PSocket<Identifier> socket;

    /**
     * Used to read in ping responses.
     */
    protected SocketInputBuffer sib;
    protected HighIdentifier i;
    private LinkedList<ByteBuffer> queue = new LinkedList<ByteBuffer>();

    protected void enqueue(ByteBuffer bb) {
      if (logger.level <= Logger.FINEST) logger.log(this+".enqueue("+bb+")");
      queue.add(bb);
      socket.register(false, true, this);
    }
    
    protected void write() throws IOException {
      if (queue.isEmpty()) return;
      long ret = socket.write(queue.getFirst());
      if (logger.level <= Logger.FINEST) logger.log(this+" wrote "+ret+" bytes of "+queue.getFirst());
      if (ret < 0) cancel();
      if (queue.getFirst().hasRemaining()) {        
        socket.register(false, true, this);
        return;
      } else {
        queue.removeFirst();
        write();
      }
    }
    
    public void receiveSelectResult(P2PSocket<Identifier> socket,
        boolean canRead, boolean canWrite) throws IOException {
      // write the high identifier
      if (canWrite) {
        write();
      }
      if (canRead) {
        read();
      }
    }
    
    public String toString() {
      return ""+i;
    }
    
    abstract void read() throws IOException;    
  }
  
  class OutgoingPilot extends AbstractPilot implements SocketRequestHandle<HighIdentifier> {
    
    protected SocketRequestHandle<Identifier> cancellable;
    
    protected Map<String, Object> options;
    
    public OutgoingPilot(HighIdentifier i, Map<String, Object> options) {
      this.i = i;
      this.options = options;
      selectorManager.schedule(this, PILOT_PING_PERIOD, PILOT_PING_PERIOD);
    }

    public void receiveException(IOException ex) {
      cancel();
    }

    public void setCancellable(SocketRequestHandle<Identifier> cancellable) {
      this.cancellable = cancellable;
    }

    public void setSocket(P2PSocket<Identifier> socket) {
      if (cancelled) {
        socket.close();
        return;
      }
      this.cancellable = null;
      this.socket = socket;
      try {
        enqueue(ByteBuffer.wrap(PILOT_SOCKET_BYTES));
        enqueue(serializer.serialize(localNodeHandle));
        sib = new SocketInputBuffer(socket,1024);
        receiveSelectResult(socket, true, true);
      } catch (IOException ioe) {
        cancel();
      }
    }
    
    public boolean ping() {
      if (logger.level <= Logger.FINEST) logger.log(this+".ping "+socket);
      if (socket == null) return false;
      enqueue(ByteBuffer.wrap(PILOT_PING_BYTES));
      return true;
    }
    
    
    public void receiveException(P2PSocket<Identifier> socket, IOException ioe) {
      cancel();
    }

    /**
     * Can read a pong or request
     * Can write the initiation or ping
     */
    protected void read() throws IOException {
      try {
        byte msgType = sib.readByte();
        switch(msgType) {
        case PILOT_PONG:
          if (logger.level <= Logger.FINEST) logger.log(this+" received pong");          
          sib.clear();
          read(); // read the next thing, or re-register if there isn't enough to read
          break;
        case PILOT_REQUEST:
          // TODO handle this
          break;
        }        
      } catch (InsufficientBytesException ibe) {
        socket.register(true, false, this);
        return;
      } catch (IOException ioe) {
//      } catch (ClosedChannelException cce) {
        cancel();
      }
    }
    
    public HighIdentifier getIdentifier() {
      return i;
    }

    public Map<String, Object> getOptions() {
      return options;
    }

    public boolean cancel() {
      super.cancel();
      if (socket == null) {
        if (cancellable != null) {
          cancellable.cancel();
          cancellable = null;
        }
      } else {
        socket.close();        
      }
      outgoingPilots.remove(i);
      return true;
    }

    @Override
    public void run() {
      ping();
    }

  }
  
  // ********* incoming Pilots, only used by non-NATted nodes *************
  Map<HighIdentifier, IncomingPilot> incomingPilots = new HashMap<HighIdentifier, IncomingPilot>();
  
  class IncomingPilot extends AbstractPilot {
    /**
     * Used to read the initial connection information, then re-constructed each time to read pings.
     * Always ready to read the pings.
     */
    public IncomingPilot(P2PSocket<Identifier> socket) throws IOException {
      this.socket = socket;
      sib = new SocketInputBuffer(socket,1024);
      receiveSelectResult(socket, true, true);
    }

    protected void read() throws IOException {
//      logger.log(this+".read()");
      if (i == null) {
        // only do this the first time
        try {
          i = serializer.deserialize(sib);
          if (logger.level <= Logger.INFO) logger.log("Received incoming Pilot from "+i);
        } catch (InsufficientBytesException ibe) {
          socket.register(true, false, this);
          return;
        }
        sib.clear();
        incomingPilots.put(i,this);                
        
        // NOTE, it's not important to put a return here, because maybe the node sent a ping while waiting for this step, 
        // just rely on the recovery to properly re-register this
      }

      try {
//        logger.log(this+" reading byte");
        byte msgType = sib.readByte();
        switch(msgType) {
        case PILOT_PING:
          if (logger.level <= Logger.FINER) logger.log(this+" received ping");
          sib.clear();          
          enqueue(ByteBuffer.wrap(PILOT_PONG_BYTES));
          read();  // read the next thing, or re-register if there isn't enough to read
          break;
        }
      } catch (InsufficientBytesException ibe) {
//        logger.log(this+" InsufficientBytesException");
        socket.register(true, false, this);
        return;
      } catch (IOException ioe) {
//      } catch (ClosedChannelException cce) {
        cancel();
      }
    }
      
    public boolean cancel() {
      return super.cancel();
    }

    public void receiveException(P2PSocket<Identifier> socket, IOException ioe) {
      if (i != null) incomingPilots.remove(i);
      socket.close();
    }

    @Override
    public void run() {
      // nothing for now, not scheduled
      
      // TODO Auto-generated method stub
      
    }    
  }
}
