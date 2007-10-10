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
package rice.pastry.transport;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.mpisws.p2p.transport.MessageCallback;
import org.mpisws.p2p.transport.MessageRequestHandle;
import org.mpisws.p2p.transport.P2PSocket;
import org.mpisws.p2p.transport.P2PSocketReceiver;
import org.mpisws.p2p.transport.SocketCallback;
import org.mpisws.p2p.transport.SocketRequestHandle;
import org.mpisws.p2p.transport.TransportLayer;
import org.mpisws.p2p.transport.TransportLayerCallback;
import org.mpisws.p2p.transport.commonapi.CommonAPITransportLayer;
import org.mpisws.p2p.transport.commonapi.RawMessageDeserializer;
import org.mpisws.p2p.transport.commonapi.TransportLayerNodeHandle;
import org.mpisws.p2p.transport.liveness.LivenessListener;
import org.mpisws.p2p.transport.liveness.LivenessProvider;
import org.mpisws.p2p.transport.liveness.PingListener;
import org.mpisws.p2p.transport.multiaddress.MultiInetSocketAddress;
import org.mpisws.p2p.transport.priority.PriorityTransportLayer;
import org.mpisws.p2p.transport.proximity.ProximityListener;
import org.mpisws.p2p.transport.proximity.ProximityProvider;
import org.mpisws.p2p.transport.util.SocketRequestHandleImpl;

import rice.environment.Environment;
import rice.environment.logging.Logger;
import rice.p2p.commonapi.appsocket.AppSocket;
import rice.p2p.commonapi.appsocket.AppSocketReceiver;
import rice.p2p.commonapi.exception.AppNotRegisteredException;
import rice.p2p.commonapi.exception.AppSocketException;
import rice.p2p.commonapi.exception.NoReceiverAvailableException;
import rice.p2p.commonapi.rawserialization.InputBuffer;
import rice.p2p.commonapi.rawserialization.RawMessage;
import rice.p2p.util.MRHAdapter;
import rice.pastry.ExponentialBackoffScheduledMessage;
import rice.pastry.Id;
import rice.pastry.NodeHandle;
import rice.pastry.NodeHandleFactory;
import rice.pastry.PastryNode;
import rice.pastry.ScheduledMessage;
import rice.pastry.boot.Bootstrapper;
import rice.pastry.client.PastryAppl;
import rice.pastry.join.InitiateJoin;
import rice.pastry.leafset.InitiateLeafSetMaintenance;
import rice.pastry.leafset.LeafSet;
import rice.pastry.messaging.Message;
import rice.pastry.messaging.MessageDispatch;
import rice.pastry.messaging.PJavaSerializedMessage;
import rice.pastry.messaging.PRawMessage;
import rice.pastry.routing.InitiateRouteSetMaintenance;
import rice.pastry.routing.Router;
import rice.pastry.routing.RoutingTable;
import rice.pastry.socket.SocketNodeHandle;

public class TLPastryNode extends PastryNode implements 
    TransportLayerCallback<NodeHandle, RawMessage>, 
    LivenessListener<NodeHandle>,
    ProximityListener<NodeHandle> {
  
  public static final byte CONNECTION_UNKNOWN_ERROR = -1;
  public static final byte CONNECTION_UNKNOWN = -100;
  public static final byte CONNECTION_OK = 0;
  public static final byte CONNECTION_NO_APP = 1;
  public static final byte CONNECTION_NO_ACCEPTOR = 2;
  
  TransportLayer<NodeHandle, RawMessage> tl;
  ProximityProvider<NodeHandle> proxProvider;
  
  Deserializer deserializer;
  Bootstrapper bootstrapper;
  
  public TLPastryNode(Id id, Environment e) {
    super(id, e);
  }

  public void registerReceiver(int address,
      PastryAppl receiver) {
    if (logger.level <= Logger.FINE) logger.log("registerReceiver("+address+","+receiver+"):"+receiver.getDeserializer());
    deserializer.setDeserializer(address, receiver.getDeserializer());
    super.registerReceiver(address, receiver);
  }
  
  @Override
  public NodeHandle coalesce(NodeHandle newHandle) {
    if (logger.level <= Logger.FINER) logger.log("coalesce("+newHandle+")");
    return handleFactory.coalesce(newHandle);
  }

  @Override
  public SocketRequestHandle connect(NodeHandle i2, final AppSocketReceiver deliverSocketToMe,
      final PastryAppl appl, int timeout) {
    
    final SocketNodeHandle i = (SocketNodeHandle)i2;
    
    final SocketRequestHandleImpl<SocketNodeHandle> handle = new SocketRequestHandleImpl<SocketNodeHandle>(i, null);

    // use the proper application address
    final ByteBuffer b = ByteBuffer.allocate(4);
    b.asIntBuffer().put(appl.getAddress());
    b.clear();
    
    
    handle.setSubCancellable(tl.openSocket(i, 
      new SocketCallback<NodeHandle>(){    
        public void receiveResult(SocketRequestHandle<NodeHandle> c, 
            P2PSocket<NodeHandle> result) {
          
          if (c != handle.getSubCancellable()) throw new RuntimeException("c != handle.getSubCancellable() (indicates a bug in the code) c:"+c+" sub:"+handle.getSubCancellable());
          
          if (logger.level <= Logger.FINER) logger.log("openSocket("+i+"):receiveResult("+result+")");
          result.register(false, true, new P2PSocketReceiver<NodeHandle>() {        
            public void receiveSelectResult(P2PSocket<NodeHandle> socket,
                boolean canRead, boolean canWrite) throws IOException {
              if (canRead || !canWrite) throw new IOException("Expected to write! "+canRead+","+canWrite);
              
              // write the appId
              if (socket.write(b) == -1) {
                deliverSocketToMe.receiveException(new SocketAdapter(socket, getEnvironment()), new ClosedChannelException());
                return;
              }
              
              // keep working or pass up the new socket
              if (b.hasRemaining()) {
                // keep writing
                socket.register(false, true, this); 
              } else {
                // read the response
                final ByteBuffer answer = ByteBuffer.allocate(1);
                socket.register(true, false, new P2PSocketReceiver<NodeHandle>(){
                
                  public void receiveSelectResult(P2PSocket<NodeHandle> socket, boolean canRead, boolean canWrite) throws IOException {
                    
                    if (socket.read(answer) == -1) {
                      deliverSocketToMe.receiveException(new SocketAdapter(socket, getEnvironment()), new ClosedChannelException());
                      return;
                    };
                    
                    if (answer.hasRemaining()) {
                      socket.register(true, false, this);
                    } else {
                      answer.clear();
                      
                      byte connectResult = answer.get();
                      //System.out.println(this+"Read "+connectResult);
                      switch(connectResult) {
                        case CONNECTION_OK:
                          // on connector side
                          deliverSocketToMe.receiveSocket(new SocketAdapter(socket, getEnvironment()));                     
                          return;
                        case CONNECTION_NO_APP:
                          deliverSocketToMe.receiveException(new SocketAdapter(socket, getEnvironment()), new AppNotRegisteredException(appl.getAddress()));
                          return;
                        case CONNECTION_NO_ACCEPTOR:
                          deliverSocketToMe.receiveException(new SocketAdapter(socket, getEnvironment()), new NoReceiverAvailableException());            
                          return;
                        default:
                          deliverSocketToMe.receiveException(new SocketAdapter(socket, getEnvironment()), new AppSocketException("Unknown error "+connectResult));
                          return;
                      }
                    }                    
                  }
                
                  public void receiveException(P2PSocket<NodeHandle> socket, IOException ioe) {
                    deliverSocketToMe.receiveException(new SocketAdapter(socket, getEnvironment()), ioe);
                  }                
                });
              }
            }
          
            public void receiveException(P2PSocket<NodeHandle> socket,
                IOException e) {
              deliverSocketToMe.receiveException(new SocketAdapter(socket, getEnvironment()), e);
            }        
          }); 
        }    
    
        public void receiveException(SocketRequestHandle<NodeHandle> s, IOException ex) {
          // TODO: return something with a proper toString()
          deliverSocketToMe.receiveException(null, ex);
        }    
      }, 
    null));
    
    return handle;
  }
  
  public void incomingSocket(P2PSocket<NodeHandle> s) throws IOException {
    
    // read the appId
    final ByteBuffer appIdBuffer = ByteBuffer.allocate(4);
    
    s.register(true, false, new P2PSocketReceiver<NodeHandle>() {
    
      public void receiveSelectResult(
          P2PSocket<NodeHandle> socket,
          boolean canRead, boolean canWrite) throws IOException {
        // read the appId
        if (socket.read(appIdBuffer) == -1) {
          if (logger.level <= Logger.WARNING) logger.log("AppId Socket from "+socket+" closed unexpectedly.");
          return;
        }
        
        if (appIdBuffer.hasRemaining()) {
          // read the rest;
          socket.register(true, false, this);
        } else {
          appIdBuffer.clear();
          final int appId = appIdBuffer.asIntBuffer().get();

//          logger.log("Read AppId:"+appId);
          // we need to write the result, and there is a timing issure on the appl, so we need to first request to write, then do everything
          // the alternative approach is to return a dummy socket (or a wrapper) and cache any registration request until we write the response
          socket.register(false, true, new P2PSocketReceiver<NodeHandle>(){
          
            public void receiveSelectResult(P2PSocket<NodeHandle> socket, 
                boolean canRead, boolean canWrite) throws IOException {

              PastryAppl acceptorAppl = getMessageDispatch().getDestinationByAddress(appId);

              ByteBuffer toWrite = ByteBuffer.allocate(1);
              boolean success = false;
              
              if (acceptorAppl == null) {
                if (logger.level <= Logger.WARNING) logger.log("Sending error to connecter "+socket+" "+new AppNotRegisteredException(appId));
                toWrite.put(CONNECTION_NO_APP);
                toWrite.clear();
//                logger.log("incomingSocket("+socket+") rSR(): writing1:"+toWrite);
                socket.write(toWrite);
                socket.close();
              } else {  
                synchronized(acceptorAppl) {
                // try to register with the application
                  if (acceptorAppl.canReceiveSocket()) {
                    toWrite.put(CONNECTION_OK);
                    toWrite.clear();
                    success = true;
                  } else {
                    if (logger.level <= Logger.WARNING) logger.log("Sending error to connecter "+socket+" "+new NoReceiverAvailableException());
                    toWrite.put(CONNECTION_NO_ACCEPTOR);                    
                    toWrite.clear();
                  }
                  
//                  logger.log("rSR(): writing2:"+toWrite);
                  socket.write(toWrite);
                  if (toWrite.hasRemaining()) {
                    // this sucks, because the snychronization with the app-receiver becomes all wrong, this shouldn't normally happen
                    if (logger.level <= Logger.WARNING) logger.log("couldn't write 1 bite!!! "+toWrite);
                    socket.close();
                    return;
                  }
                  
                  if (success) {
//                    logger.log("rSR(): delivering socket to receiver:"+toWrite);
                    acceptorAppl.finishReceiveSocket(new SocketAdapter(socket, getEnvironment()));
                  }
                } // sync
              } // if (acceptorAppl!=null)              
            } // rSR()
          
            public void receiveException(P2PSocket<NodeHandle> socket, IOException ioe) {
              if (logger.level <= Logger.WARNING) logger.logException("incomingSocket("+socket+")", ioe);
              return;
            }          
          });
        }
      }
    
      public void receiveException(
          P2PSocket<NodeHandle> socket,
          IOException ioe) {
        if (logger.level <= Logger.WARNING) logger.logException("incomingSocket("+socket+")",ioe);
      }
    
    });
  }

  protected void acceptAppSocket(int appId) throws AppSocketException {
    PastryAppl acceptorAppl = getMessageDispatch().getDestinationByAddress(appId);
    if (acceptorAppl == null) throw new AppNotRegisteredException(appId);
    if (!acceptorAppl.canReceiveSocket()) throw new NoReceiverAvailableException();
  }



  // join retransmission stuff
  protected ScheduledMessage joinEvent;

  public ExponentialBackoffScheduledMessage scheduleMsgExpBackoff(Message msg, long delay, long initialPeriod, double expBase) {
    ExponentialBackoffScheduledMessage sm = new ExponentialBackoffScheduledMessage(this,msg,getEnvironment().getSelectorManager().getTimer(),delay,initialPeriod,expBase);
    return sm;
  }

  
  @Override
  public void initiateJoin(Collection<NodeHandle> bootstrap) {
    if (logger.level <= Logger.CONFIG) logger.log(
      "initiateJoin("+bootstrap+")");
    if (bootstrap == null || bootstrap.isEmpty()) {      
      // no bootstrap node, so ready immediately
      setReady();
    } else {
      // schedule (re-)transmission of the join message at an exponential backoff
      joinEvent = scheduleMsgExpBackoff(new InitiateJoin(bootstrap), 0, 15000, 2);
    }
  }

  @Override
  public void nodeIsReady() {
    if (joinEvent != null) {
      joinEvent.cancel();
      joinEvent = null;
    }
    // cancel join retransmissions
  }

  @Override
  public int proximity(NodeHandle nh) {
    return proxProvider.proximity(nh);
  }
  /**
   * Schedule the specified message to be sent to the local node after a
   * specified delay. Useful to provide timeouts.
   *
   * @param msg a message that will be delivered to the local node after the
   *      specified delay
   * @param delay time in milliseconds before message is to be delivered
   * @return the scheduled event object; can be used to cancel the message
   */
  public ScheduledMessage scheduleMsg(Message msg, long delay) {
    ScheduledMessage sm = new ScheduledMessage(this, msg);
    getEnvironment().getSelectorManager().getTimer().schedule(sm, delay);
    return sm;
  }


  /**
   * Schedule the specified message for repeated fixed-delay delivery to the
   * local node, beginning after the specified delay. Subsequent executions take
   * place at approximately regular intervals separated by the specified period.
   * Useful to initiate periodic tasks.
   *
   * @param msg a message that will be delivered to the local node after the
   *      specified delay
   * @param delay time in milliseconds before message is to be delivered
   * @param period time in milliseconds between successive message deliveries
   * @return the scheduled event object; can be used to cancel the message
   */
  public ScheduledMessage scheduleMsg(Message msg, long delay, long period) {
    ScheduledMessage sm = new ScheduledMessage(this, msg);
    getEnvironment().getSelectorManager().getTimer().schedule(sm, delay, period);
    return sm;
  }

  /**
   * Schedule the specified message for repeated fixed-rate delivery to the
   * local node, beginning after the specified delay. Subsequent executions take
   * place at approximately regular intervals, separated by the specified
   * period.
   *
   * @param msg a message that will be delivered to the local node after the
   *      specified delay
   * @param delay time in milliseconds before message is to be delivered
   * @param period time in milliseconds between successive message deliveries
   * @return the scheduled event object; can be used to cancel the message
   */
  public ScheduledMessage scheduleMsgAtFixedRate(Message msg, long delay, long period) {
    ScheduledMessage sm = new ScheduledMessage(this, msg);
    getEnvironment().getSelectorManager().getTimer().scheduleAtFixedRate(sm, delay, period);
    return sm;
  }

  @Override
  public PMessageReceipt send(NodeHandle handle, 
      final Message msg, 
      final PMessageNotification deliverAckToMe, 
      Map<String, Integer> tempOptions) {
    
    // set up the priority field in the options
    if (tempOptions != null && tempOptions.containsKey(PriorityTransportLayer.OPTION_PRIORITY)) {
      // already has the priority;
    } else {
      if (tempOptions == null) {
        tempOptions = new HashMap<String, Integer>(); 
      } else {
        tempOptions = new HashMap<String, Integer>(tempOptions);
      }
      tempOptions.put(PriorityTransportLayer.OPTION_PRIORITY, msg.getPriority());
    }
    
    final Map<String, Integer> options = tempOptions;
    
    if (handle.equals(localhandle)) {
      receiveMessage(msg);
      PMessageReceipt ret = new PMessageReceipt() {

        public boolean cancel() {
          return false;
        }

        public NodeHandle getIdentifier() {
          return localhandle;
        }

        public Map<String, Integer> getOptions() {
          return options;
        }

        public Message getMessage() {
          return msg;
        }
        public String toString() {
          return "TLPN$PMsgRecpt{"+msg+","+localhandle+"}";
        }
      }; 
      if (deliverAckToMe != null) deliverAckToMe.sent(ret);
      return ret;
    }
    
    PRawMessage rm;
    if (msg instanceof PRawMessage) {
      rm = (PRawMessage)msg; 
    } else {
      rm = new PJavaSerializedMessage(msg); 
    }
    
    final PMessageReceiptImpl ret = new PMessageReceiptImpl(msg);
    ret.setInternal(tl.sendMessage(handle, rm, deliverAckToMe == null ? null : 
      new MessageCallback<NodeHandle, RawMessage>(){
    
      public void sendFailed(MessageRequestHandle<NodeHandle, RawMessage> msg, IOException reason) {        
        if (ret.internal == null) ret.setInternal(msg);
        deliverAckToMe.sendFailed(ret, reason);
      }
    
      public void ack(MessageRequestHandle<NodeHandle, RawMessage> msg) {
        if (ret.internal == null) ret.setInternal(msg);
        deliverAckToMe.sent(ret);
      }
    
    }, options));
    return ret;
  }
  
  public void messageReceived(NodeHandle i, RawMessage m, Map<String, Integer> options) throws IOException {
    if (m.getType() == 0 && (m instanceof PJavaSerializedMessage)) {
      receiveMessage(((PJavaSerializedMessage)m).getMessage());
    } else {
      receiveMessage((Message)m);
    }
  }


  public NodeHandle readNodeHandle(InputBuffer buf) throws IOException {
    return handleFactory.readNodeHandle(buf);
  }
  
//  public void setElements(NodeHandle lh, MessageDispatch md, LeafSet ls, RoutingTable rt, Router router, Bootstrapper bootstrapper) {
//    super.setElements(lh, md, ls, rt, router);
//    this.bootstrapper = bootstrapper;
//  }

  
  
  @Override
  public Bootstrapper getBootstrapper() {
    return bootstrapper;
  }



  // TODO: this all needs to go!

  // Period (in seconds) at which the leafset and routeset maintenance tasks, respectively, are invoked.
  // 0 means never.
  protected int leafSetMaintFreq, routeSetMaintFreq;

  protected ScheduledMessage leafSetRoutineMaintenance = null;
  protected ScheduledMessage routeSetRoutineMaintenance = null;
  
  // The address (ip + port) of this pastry node
  private NodeHandleFactory handleFactory;
  protected LivenessProvider<NodeHandle> livenessProvider;

  public void setSocketElements(NodeHandle localhandle,
      int lsmf, int rsmf, 
      TransportLayer<NodeHandle, RawMessage> tl,
      LivenessProvider<NodeHandle> livenessProvider,
      ProximityProvider<NodeHandle> proxProvider,
      Deserializer deserializer, 
      NodeHandleFactory handleFactory) {
    this.localhandle = localhandle;
    this.leafSetMaintFreq = lsmf;
    this.routeSetMaintFreq = rsmf;
    this.handleFactory = handleFactory;
    this.proxProvider = proxProvider;
    proxProvider.addProximityListener(this);
    
    this.tl = tl;
    this.livenessProvider = livenessProvider;
    this.deserializer = deserializer;
    tl.setCallback(this);
    livenessProvider.addLivenessListener(this);
  }

  public void setBootstrapper(Bootstrapper boot) {
    this.bootstrapper = boot;
  }
  
  /**
   * Called after the node is initialized.
   * 
   * @param bootstrap The node which this node should boot off of.
   */
  public void doneNode(Collection<NodeHandle> bootstrap) { 
    if (logger.level <= Logger.INFO) logger.log("doneNode:"+bootstrap);
//    doneNode(bootstrap.toArray(new NodeHandle[1]));
//  }
//
//  public void doneNode(NodeHandle[] bootstrap) {
    if (logger.level <= Logger.INFO) logger.log("doneNode:"+bootstrap);
    if (routeSetMaintFreq > 0) {
      // schedule the routeset maintenance event
      routeSetRoutineMaintenance = scheduleMsgAtFixedRate(new InitiateRouteSetMaintenance(),
        routeSetMaintFreq * 1000, routeSetMaintFreq * 1000);
      if (logger.level <= Logger.CONFIG) logger.log(
          "Scheduling routeSetMaint for "+routeSetMaintFreq * 1000+","+routeSetMaintFreq * 1000);
    }
    if (leafSetMaintFreq > 0) {
      // schedule the leafset maintenance event
      leafSetRoutineMaintenance = scheduleMsgAtFixedRate(new InitiateLeafSetMaintenance(),
        leafSetMaintFreq * 1000, leafSetMaintFreq * 1000);
      if (logger.level <= Logger.CONFIG) logger.log(
          "Scheduling leafSetMaint for "+leafSetMaintFreq * 1000+","+leafSetMaintFreq * 1000);
    }
    
    initiateJoin(bootstrap);
  }
  
  public String toString() {
    return "TLPastryNode"+localhandle;
  }
  
  @Override
  public void destroy() {
    super.destroy();
    if (getEnvironment().getSelectorManager().isSelectorThread()) {
      tl.destroy();
    } else {
      getEnvironment().getSelectorManager().invoke(new Runnable() {
        public void run() {
          tl.destroy();
        }
      });
    }    
  }

  public void livenessChanged(NodeHandle i, int val, Map<String, Integer> options) {
    if (val == LIVENESS_ALIVE) {
      i.update(NodeHandle.DECLARED_LIVE);
    } else {
      if (val >= LIVENESS_DEAD) {
        i.update(NodeHandle.DECLARED_DEAD);
      }
    }
    
    notifyLivenessListeners((NodeHandle)i, val, options);
  }
  
  Collection<LivenessListener<NodeHandle>> livenessListeners = new ArrayList<LivenessListener<NodeHandle>>();
  public void addLivenessListener(LivenessListener<NodeHandle> name) {
    synchronized(livenessListeners) {
      livenessListeners.add(name);
    }    
  }
  
  public boolean removeLivenessListener(LivenessListener<NodeHandle> name) {
    synchronized(livenessListeners) {
      return livenessListeners.remove(name);
    }    
  }
  
  protected void notifyLivenessListeners(NodeHandle i, int val, Map<String, Integer> options) {
    if (logger.level <= Logger.FINE) logger.log("notifyLivenessListeners("+i+","+val+")"); 
    ArrayList<LivenessListener<NodeHandle>> temp;
    synchronized(livenessListeners) {
      temp = new ArrayList<LivenessListener<NodeHandle>>(livenessListeners);
    }
    for (LivenessListener<NodeHandle> ll : temp) {
      ll.livenessChanged(i, val, options);
    }
  }

  public boolean checkLiveness(NodeHandle i, Map<String, Integer> options) {    
    return livenessProvider.checkLiveness(i, options);
  }

  public int getLiveness(NodeHandle i, Map<String, Integer> options) {
    return livenessProvider.getLiveness(i, options);
  }


  public void proximityChanged(NodeHandle i, int val, Map<String, Integer> options) {
    SocketNodeHandle handle = ((SocketNodeHandle)i);
    handle.update(NodeHandle.PROXIMITY_CHANGED);     
  }

  public LivenessProvider<NodeHandle> getLivenessProvider() {
    return livenessProvider;
  }

  public ProximityProvider<NodeHandle> getProxProvider() {
    return proxProvider;
  }
  
  public TransportLayer<NodeHandle, RawMessage> getTL() {
    return tl;
  }

  public void clearState(NodeHandle i) {
    livenessProvider.clearState(i);
  }

  public void addProximityListener(ProximityListener<NodeHandle> listener) {
    proxProvider.addProximityListener(listener);
  }

  public boolean removeProximityListener(ProximityListener<NodeHandle> listener) {
    return proxProvider.removeProximityListener(listener);
  }
}
