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
package rice.pastry.socket.nat.rendezvous;

import java.net.InetSocketAddress;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Map;

import org.mpisws.p2p.transport.MessageCallback;
import org.mpisws.p2p.transport.MessageRequestHandle;
import org.mpisws.p2p.transport.priority.PriorityTransportLayer;
import org.mpisws.p2p.transport.rendezvous.ChannelOpener;
import org.mpisws.p2p.transport.rendezvous.RendezvousContact;
import org.mpisws.p2p.transport.rendezvous.RendezvousStrategy;
import org.mpisws.p2p.transport.rendezvous.RendezvousTransportLayer;
import org.mpisws.p2p.transport.rendezvous.RendezvousTransportLayerImpl;
import org.mpisws.p2p.transport.util.MessageRequestHandleImpl;
import org.mpisws.p2p.transport.util.OptionsFactory;

import rice.Continuation;
import rice.environment.logging.Logger;
import rice.p2p.commonapi.Cancellable;
import rice.p2p.commonapi.Id;
import rice.p2p.commonapi.MessageReceipt;
import rice.p2p.commonapi.NodeHandle;
import rice.p2p.commonapi.rawserialization.InputBuffer;
import rice.p2p.commonapi.rawserialization.MessageDeserializer;
import rice.p2p.util.AttachableCancellable;
import rice.pastry.PastryNode;
import rice.pastry.client.PastryAppl;
import rice.pastry.leafset.LeafSet;
import rice.pastry.messaging.Message;
import rice.pastry.routing.RouteMessage;
import rice.pastry.routing.RouteMessageNotification;
import rice.pastry.socket.SocketNodeHandle;
import rice.pastry.transport.PMessageNotification;
import rice.pastry.transport.PMessageReceipt;
import rice.selector.SelectorManager;

/**
 * TODO: make not abstract
 * 
 * @author Jeff Hoye
 *
 */
public class RendezvousApp extends PastryAppl implements RendezvousStrategy<RendezvousSocketNodeHandle> {
  protected LeafSet leafSet;
  protected SelectorManager selectorManager;
  protected RendezvousTransportLayer<RendezvousSocketNodeHandle> tl;
  
  
  public RendezvousApp(PastryNode pn) {
    super(pn,null,0,null);
    setDeserializer(new MessageDeserializer() {
    
      public rice.p2p.commonapi.Message deserialize(InputBuffer buf, short type,
          int priority, NodeHandle sender) throws IOException {
        byte version;
        switch (type) {
        case ByteBufferMsg.TYPE:
          version = buf.readByte();
          if (version == 0) { // version 0
            int length = buf.readInt();
            byte[] msg = new byte[length];
            buf.read(msg);
            return new ByteBufferMsg(ByteBuffer.wrap(msg),(RendezvousSocketNodeHandle)sender,priority,getAddress());
          } else {
            throw new IllegalArgumentException("Unknown version for ByteBufferMsg: "+version);
          }
        case PilotForwardMsg.TYPE:
          version = buf.readByte();
          if (version == 0) { // version 0            
            RendezvousSocketNodeHandle target = (RendezvousSocketNodeHandle)thePastryNode.readNodeHandle(buf);
            ByteBufferMsg subMsg = (ByteBufferMsg)deserialize(buf, ByteBufferMsg.TYPE, priority, sender);
            return new PilotForwardMsg(getAddress(), subMsg, target);
          } else {
            throw new IllegalArgumentException("Unknown version for PilotForwardMsg: "+version);
          }
        default:
          throw new IllegalArgumentException("Unknown type: "+type);            
        }
      }    
    }); // this constructor doesn't auto-register
    leafSet = pn.getLeafSet();
    selectorManager = pn.getEnvironment().getSelectorManager();
  }
  
  /**
   * Can be called before you boot, will tell you if you are Firewalled.
   * Should send a message to the bootstrap, who forwards it to another node who sends you the request back.  Should
   * try UDP/TCP.
   * 
   * Returns your external address.
   * 
   * @param bootstrap
   * @param receiveResult
   */
  public void isNatted(NodeHandle bootstrap, Continuation<InetSocketAddress, Exception> receiveResult) {
    
  }

  @Override
  public void messageForAppl(Message msg) {
    if (msg instanceof ByteBufferMsg) {
      ByteBufferMsg bbm = (ByteBufferMsg)msg;
      try {
        tl.messageReceivedFromOverlay((RendezvousSocketNodeHandle)bbm.getSender(), bbm.buffer, null);
      } catch (IOException ioe) {
        if (logger.level <= Logger.WARNING) logger.logException("dropping "+bbm, ioe);
      }
      // TODO: Get a reference to the TL... is this an interface?  Should it be?
      // TODO: Deliver this to the TL.
      
  //    tl.messageReceived();
  //    throw new RuntimeException("Not implemented.");
      return;
    }
    if (msg instanceof PilotForwardMsg) {
      PilotForwardMsg pfm = (PilotForwardMsg)msg;
      if (logger.level <= Logger.FINER) logger.log("Forwarding message "+pfm);
      thePastryNode.send(pfm.getTarget(), pfm.getBBMsg(), null, null);
    }
  }

  public Cancellable openChannel(final RendezvousSocketNodeHandle target, 
      final RendezvousSocketNodeHandle rendezvous, 
      final RendezvousSocketNodeHandle source,
      final int uid,
      final Continuation<Integer, Exception> deliverResultToMe) {
    
    // we don't want state changing, so this can only be called on the selector
    if (!selectorManager.isSelectorThread()) {
      final AttachableCancellable ret = new AttachableCancellable();
      selectorManager.invoke(new Runnable() {
        public void run() {
          ret.attach(openChannel(target, rendezvous, source, uid, deliverResultToMe));
        }
      });
      return ret;
    }

    if (target.isConnected()) {
      // TODO: route directly there 
      return null;
    }
    
    // What if he is my nearest neighbor?  This fails an invariant.  Better at least track this...
    // also, there can be dead nodes in the leafset, due to the lease...  Better find that there is a live node between us and the leafset.
    if (leafSet.contains(target)) {
      // it's in the leafset, make sure there is a guy between us
      // find the nearest alive guy to target that is between us to send-direct the request to
      
      // this is the index of target
      int targetIndex = leafSet.getIndex(target);      
      
      // this is the nearest neighbor on the side of target
      int nearestNeighborIndex = 1;      
      if (targetIndex < 0) nearestNeighborIndex = -1;
      
      // this is the direction we count from target to nearestNeighbor
      int direction = -nearestNeighborIndex;

      for (int i = targetIndex+direction; i != nearestNeighborIndex; i++) {
        RendezvousSocketNodeHandle nh = (RendezvousSocketNodeHandle)leafSet.get(i);
        
        // we can send a message to nh
        if (nh.isConnected()) {
          // TODO: send the message to this node, if it fails, call this.openChannel() again...
        }
      }
    }
    
    
    // TODO Auto-generated method stub
    return null;
  }

  public MessageRequestHandle<RendezvousSocketNodeHandle, ByteBuffer> sendMessage(
      final RendezvousSocketNodeHandle i, 
      final ByteBuffer m, 
      final MessageCallback<RendezvousSocketNodeHandle, ByteBuffer> deliverAckToMe, 
      final Map<String, Object> options) {
    if (logger.level <= Logger.FINE) logger.log("sendMessage("+i+","+m+","+deliverAckToMe+","+options+")");
    // TODO: use the new method in PastryAppl
    
    ByteBufferMsg msg = new ByteBufferMsg(m, thePastryNode.getLocalHandle(), ((Integer)options.get(PriorityTransportLayer.OPTION_PRIORITY)), getAddress());
    
    if (options.containsKey(RendezvousTransportLayerImpl.OPTION_USE_PILOT)) {
//      if (true) throw new RuntimeException("Not Implemented.");
      RendezvousSocketNodeHandle pilot = (RendezvousSocketNodeHandle)options.get(RendezvousTransportLayerImpl.OPTION_USE_PILOT);
      if (logger.level <= Logger.FINER) logger.log("sendMessage("+i+","+m+","+deliverAckToMe+","+options+") sending via "+pilot);      
      final MessageRequestHandleImpl<RendezvousSocketNodeHandle, ByteBuffer> ret = 
        new MessageRequestHandleImpl<RendezvousSocketNodeHandle, ByteBuffer>(i,m,options);
      ret.setSubCancellable(thePastryNode.send(pilot, new PilotForwardMsg(getAddress(),msg,i), new PMessageNotification(){      
        public void sent(PMessageReceipt msg) {
          deliverAckToMe.ack(ret);
        }
        public void sendFailed(PMessageReceipt msg, Exception reason) {
          deliverAckToMe.sendFailed(ret, reason);
        }      
      }, null));
      return ret;
    } else {
      
      final RouteMessage rm = 
        new RouteMessage(
            i.getNodeId(),
            msg,
          (byte)thePastryNode.getEnvironment().getParameters().getInt("pastry_protocol_router_routeMsgVersion"));    
  //    rm.setPrevNode(thePastryNode.getLocalHandle());
      rm.setDestinationHandle(i);
  
      rm.setTLOptions(options);
      
      // TODO: make PastryNode have a router that does this properly, rather than receiveMessage
      final MessageRequestHandle<RendezvousSocketNodeHandle, ByteBuffer> ret = new MessageRequestHandle<RendezvousSocketNodeHandle, ByteBuffer>() {
        
        public boolean cancel() {
          if (logger.level <= Logger.FINE) logger.log("sendMessage("+i+","+m+","+deliverAckToMe+","+options+").cancel()");
          return rm.cancel();
        }
      
        public ByteBuffer getMessage() {
          return m;
        }
      
        public RendezvousSocketNodeHandle getIdentifier() {
          return i;
        }
  
        public Map<String, Object> getOptions() {
          return options;
        }    
      };
      
      // NOTE: Installing this anyway if the LogLevel is high enough is kind of wild, but really useful for debugging
      if ((deliverAckToMe != null) || (logger.level <= Logger.FINE)) {
        rm.setRouteMessageNotification(new RouteMessageNotification() {
          public void sendSuccess(rice.pastry.routing.RouteMessage message, rice.pastry.NodeHandle nextHop) {
            if (logger.level <= Logger.FINE) logger.log("sendMessage("+i+","+m+","+deliverAckToMe+","+options+").sendSuccess():"+nextHop);
            if (deliverAckToMe != null) deliverAckToMe.ack(ret);
          }    
          public void sendFailed(rice.pastry.routing.RouteMessage message, Exception e) {
            if (logger.level <= Logger.FINE) logger.log("sendMessage("+i+","+m+","+deliverAckToMe+","+options+").sendFailed("+e+")");
            if (deliverAckToMe != null) deliverAckToMe.sendFailed(ret, e);
          }
        });
      }
      
  //    Map<String, Object> rOptions;
  //    if (options == null) {
  //      rOptions = new HashMap<String, Object>(); 
  //    } else {
  //      rOptions = new HashMap<String, Object>(options);
  //    }
  //    rOptions.put(PriorityTransportLayer.OPTION_PRIORITY, pm.getPriority());
  ////    logger.log("NumOptions = "+rOptions.size());
      
      rm.setTLOptions(options);
      
      thePastryNode.getRouter().route(rm);
      
      return ret;
    }
  }

  public String toString() {
    return "RendezvousApp{"+thePastryNode+"}";
  }
  
  public void setTransportLayer(
      RendezvousTransportLayer<RendezvousSocketNodeHandle> tl) {
    this.tl = tl;
  }


  
  
  
}
