package rice.pastry.standard;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import org.mpisws.p2p.transport.liveness.LivenessListener;

import rice.environment.logging.Logger;
import rice.p2p.commonapi.Cancellable;
import rice.pastry.NodeHandle;
import rice.pastry.PastryNode;
import rice.pastry.messaging.Message;
import rice.pastry.messaging.MessageDispatch;
import rice.pastry.routing.RouteMessage;
import rice.pastry.routing.SendOptions;
import rice.pastry.transport.PMessageNotification;
import rice.pastry.transport.PMessageReceipt;

/**
 * The superclass makes the routing decisions.  This class handles the rapid-rerouting.
 * 
 * @author Jeff Hoye
 *
 */
public class RapidRerouter extends StandardRouter implements LivenessListener<NodeHandle> {


  /**
   * These messages should be rapidly rerouted if the node goes suspected.
   */
  Map<NodeHandle, Collection<RouterNotification>> pending;
  
  Logger logger;
  
  public RapidRerouter(PastryNode thePastryNode, MessageDispatch dispatch) {
    super(thePastryNode, dispatch);
    logger = thePastryNode.getEnvironment().getLogManager().getLogger(RapidRerouter.class,null);
    pending = new HashMap<NodeHandle, Collection<RouterNotification>>();
    
    thePastryNode.addLivenessListener(this);
  }
  
  
  @Override
  protected void sendTheMessage(RouteMessage rm, NodeHandle handle) {
//    logger.log("sendTheMessage("+rm+","+handle+") reroute:"+rm.getOptions().rerouteIfSuspected());
    if (rm.getOptions().rerouteIfSuspected()) {
      // can be rapidly rerouted
      if (handle.getLiveness() >= LIVENESS_SUSPECTED) {
//        if (logger.level <= Logger.WARNING) logger.log("Reroutable message "+rm+" sending to non-alive node:"+handle+" liveness:"+handle.getLiveness());
        super.sendTheMessage(rm, handle);
        return;
      }
      
      RouterNotification notifyMe = new RouterNotification(rm, handle);
      addToPending(notifyMe, handle);
      rm.setTLCancellable(notifyMe);
      notifyMe.setCancellable(thePastryNode.send(handle, rm, notifyMe, rm.getTLOptions()));
    } else {
      super.sendTheMessage(rm, handle);
    }
  }
  
  protected void rerouteMe(RouteMessage rm, NodeHandle oldDest) {        
    // this is going to make forward() be called again, can prevent this with a check in getPrevNode().equals(localNode)
    rm.getOptions().setRerouteIfSuspected(SendOptions.defaultRerouteIfSuspected);
    route(rm);
  }
  
  class RouterNotification implements Cancellable, PMessageNotification {
    RouteMessage rm;
    NodeHandle dest;
    PMessageReceipt cancellable;

    public RouterNotification(RouteMessage rm, NodeHandle handle) {
      this.rm = rm;
      this.dest = handle;
    }

    public void setCancellable(PMessageReceipt receipt) {
      this.cancellable = receipt;
    }

    public void sendFailed(PMessageReceipt msg, Exception reason) {
      // what to do..., rapidly reroute? 
      if (logger.level <= Logger.WARNING) logger.logException("Send failed on message "+rm+" to "+dest+" rerouting.", reason);
      cancellable = null;
      rm.setTLCancellable(null);
      removeFromPending(this, dest);
      rerouteMe(rm, dest);
    }

    public void sent(PMessageReceipt msg) {
      if (logger.level <= Logger.FINE) logger.log("Send success "+rm+" to:"+dest+" rerouting.");
      cancellable = null;
      rm.setTLCancellable(null);
      removeFromPending(this, dest);      
      rm.sendSuccess();
    }

    public boolean cancel() {
      return cancellable.cancel();
    }    
  }

  private void addToPending(RouterNotification notifyMe, NodeHandle handle) {
    synchronized(pending) {
      Collection<RouterNotification> c = pending.get(handle);
      if (c == null) {
        c = new HashSet<RouterNotification>();
        pending.put(handle, c);
      }
      c.add(notifyMe);
    }
  }

  private void removeFromPending(RouterNotification notifyMe, NodeHandle handle) {
    synchronized(pending) {
      Collection<RouterNotification> c = pending.get(handle);
      c.remove(notifyMe);
      if (c.isEmpty()) {
        pending.remove(handle);
      }
    }    
  }
  
  public void livenessChanged(NodeHandle i, int val) {
    if (val >= LIVENESS_SUSPECTED) {
      Collection<RouterNotification> rerouteMe;
      synchronized(pending) {
        rerouteMe = pending.remove(i);        
      }
      if (rerouteMe != null) {
        for (RouterNotification rn : rerouteMe) {
          rerouteMe(rn.rm, rn.dest);
        }
      }
    }
  }
  
  @Override
  public void destroy() {
    super.destroy();
    thePastryNode.removeLivenessListener(this);
  }


}
