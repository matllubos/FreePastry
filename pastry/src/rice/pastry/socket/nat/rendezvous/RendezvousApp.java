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
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Map;

import org.mpisws.p2p.transport.MessageCallback;
import org.mpisws.p2p.transport.MessageRequestHandle;
import org.mpisws.p2p.transport.priority.PriorityTransportLayer;
import org.mpisws.p2p.transport.rendezvous.ChannelOpener;
import org.mpisws.p2p.transport.rendezvous.RendezvousContact;
import org.mpisws.p2p.transport.rendezvous.RendezvousStrategy;

import rice.Continuation;
import rice.p2p.commonapi.Cancellable;
import rice.p2p.commonapi.NodeHandle;
import rice.p2p.util.AttachableCancellable;
import rice.pastry.PastryNode;
import rice.pastry.client.PastryAppl;
import rice.pastry.leafset.LeafSet;
import rice.pastry.messaging.Message;
import rice.pastry.routing.RouteMessage;
import rice.pastry.socket.SocketNodeHandle;
import rice.selector.SelectorManager;

/**
 * TODO: make not abstract
 * 
 * @author Jeff Hoye
 *
 */
public class RendezvousApp extends PastryAppl implements RendezvousStrategy<RendezvousSocketNodeHandle> {
  LeafSet leafSet;
  SelectorManager selectorManager;
  
  public RendezvousApp(PastryNode pn) {
    super(pn);
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
    // TODO Auto-generated method stub
    
  }

  public Cancellable openChannel(final RendezvousSocketNodeHandle target, 
      final RendezvousSocketNodeHandle rendezvous, 
      final byte[] credentials, 
      final Continuation<Integer, Exception> deliverResultToMe) {
    
    // we don't want state changing, so this can only be called on the selector
    if (!selectorManager.isSelectorThread()) {
      final AttachableCancellable ret = new AttachableCancellable();
      selectorManager.invoke(new Runnable() {
        public void run() {
          ret.attach(openChannel(target, rendezvous, credentials, deliverResultToMe));
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
      RendezvousSocketNodeHandle i, 
      ByteBuffer m, 
      MessageCallback<RendezvousSocketNodeHandle, ByteBuffer> deliverAckToMe, 
      Map<String, Object> options) {
    
    // TODO: use the new method in PastryAppl
    
    Message msg = new ByteBufferMsg(m, ((Integer)options.get(PriorityTransportLayer.OPTION_PRIORITY)), getAddress());
    
    RouteMessage rm = 
      new RouteMessage(
          i.getNodeId(),
          msg,
        (byte)thePastryNode.getEnvironment().getParameters().getInt("pastry_protocol_router_routeMsgVersion"));    
    rm.setPrevNode(thePastryNode.getLocalHandle());                                                                              
    
    return null;
  }


  
  
  
}
