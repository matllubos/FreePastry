package rice.pastry.standard;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import org.mpisws.p2p.transport.liveness.LivenessListener;
import org.mpisws.p2p.transport.priority.QueueOverflowException;

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
   * The max times to try to reroute a message.
   */
  public static final int MAX_RETRIES = 10;
    
  /**
   * These messages should be rapidly rerouted if the node goes suspected.
   */
  Map<NodeHandle, Collection<RouterNotification>> pending;
  
  public RapidRerouter(PastryNode thePastryNode, MessageDispatch dispatch) {
    super(thePastryNode, dispatch);
    pending = new HashMap<NodeHandle, Collection<RouterNotification>>();
    
    thePastryNode.addLivenessListener(this);
  }
  
  
  @Override
  protected void sendTheMessage(RouteMessage rm, NodeHandle handle) {
//    logger.log("sendTheMessage("+rm+","+handle+") reroute:"+rm.getOptions().rerouteIfSuspected());
    if (rm.getOptions().multipleHopsAllowed() && rm.getOptions().rerouteIfSuspected()) {
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
  
  protected void rerouteMe(RouteMessage rm, NodeHandle oldDest, Exception ioe) {        
    if (logger.level <= Logger.FINE) logger.log("rerouteMe("+rm+" oldDest:"+oldDest+")");

    rm.numRetries++;
    if (rm.numRetries > MAX_RETRIES) {
      // TODO: Notify some kind of Error Handler
      boolean dontPrint = false;
      if (ioe == null) {
        dontPrint = rm.sendFailed(new TooManyRouteAttempts(rm, MAX_RETRIES));
      } else {
        dontPrint = rm.sendFailed(ioe);
      }
      if (dontPrint) {
        if (logger.level <= Logger.CONFIG) logger.log("rerouteMe() dropping "+rm+" after "+rm.numRetries+" attempts to (re)route.");
      } else {
        if (logger.level <= Logger.WARNING) logger.log("rerouteMe() dropping "+rm+" after "+rm.numRetries+" attempts to (re)route.");        
      }
      return;
    }

    // this is going to make forward() be called again, can prevent this with a check in getPrevNode().equals(localNode)
    rm.getOptions().setRerouteIfSuspected(SendOptions.defaultRerouteIfSuspected);
    route(rm);
  }
  
  private void addToPending(RouterNotification notifyMe, NodeHandle handle) {
    if (logger.level <= Logger.FINE) logger.log("addToPending("+notifyMe+" to:"+handle+")");
    synchronized(pending) {
      Collection<RouterNotification> c = pending.get(handle);
      if (c == null) {
        c = new HashSet<RouterNotification>();
        pending.put(handle, c);
      }
      c.add(notifyMe);
    }
  }

  /**
   * Return true if it was still pending.
   * 
   * @param notifyMe
   * @param handle
   * @return true if still pending
   */
  private boolean removeFromPending(RouterNotification notifyMe, NodeHandle handle) {
    synchronized(pending) {
      Collection<RouterNotification> c = pending.get(handle);
      if (c == null) {
        if (logger.level <= Logger.FINE) logger.log("removeFromPending("+notifyMe+","+handle+") had no pending messages for handle.");
        return false;
      }
      boolean ret = c.remove(notifyMe);
      if (c.isEmpty()) {
        pending.remove(handle);
      }
      if (!ret) {
        if (logger.level <= Logger.FINE) logger.log("removeFromPending("+notifyMe+","+handle+") msg was not there."); 
      }
      return ret;
    }    
  }
  
  public void livenessChanged(NodeHandle i, int val, Map<String, Integer> options) {
    if (val >= LIVENESS_SUSPECTED) {
      Collection<RouterNotification> rerouteMe;
      synchronized(pending) {
        rerouteMe = pending.remove(i);        
      }
      if (rerouteMe != null) {
        if (logger.level <= Logger.FINE) logger.log("removing all messages to:"+i);
        for (RouterNotification rn : rerouteMe) {
          rn.cancel();
          rerouteMe(rn.rm, rn.dest, null);
        }
      }
    }
  }
  
  @Override
  public void destroy() {
    super.destroy();
    thePastryNode.removeLivenessListener(this);
  }

  class RouterNotification implements Cancellable, PMessageNotification {
    RouteMessage rm;
    NodeHandle dest;
    PMessageReceipt cancellable;

    public RouterNotification(RouteMessage rm, NodeHandle handle) {
      this.rm = rm;
      this.dest = handle;
      if (logger.level <= Logger.FINE) logger.log("RN.ctor() "+rm+" to:"+dest);
    }

    public void setCancellable(PMessageReceipt receipt) {
      this.cancellable = receipt;
    }

    public void sendFailed(PMessageReceipt msg, Exception reason) {
      // what to do..., rapidly reroute? 
      cancellable = null;
      rm.setTLCancellable(null);
      if (reason instanceof QueueOverflowException) {
        if (rm.sendFailed(reason)) {
          if (logger.level <= Logger.CONFIG) logger.logException("sendFailed("+msg.getMessage()+")=>"+msg.getIdentifier(), reason);
        } else {
          if (logger.level <= Logger.FINE) {
            logger.logException("sendFailed("+msg.getMessage()+")=>"+msg.getIdentifier(), reason);
          } else {
            if (logger.level <= Logger.WARNING) logger.log("sendFailed("+msg.getMessage()+")=>"+msg.getIdentifier()+" "+reason);          
          }
        }
        return; 
      }
      if (removeFromPending(this, dest)) {
        if (logger.level <= Logger.WARNING) logger.logException("Send failed on message "+rm+" to "+dest+" rerouting."+msg, reason);
        rerouteMe(rm, dest, reason);
      } else {        
        if (rm.sendFailed(reason)) {
          if (logger.level <= Logger.CONFIG) logger.logException("sendFailed("+msg.getMessage()+")=>"+msg.getIdentifier(), reason);
        } else {
          if (logger.level <= Logger.WARNING) logger.logException("sendFailed("+msg.getMessage()+")=>"+msg.getIdentifier(), reason);          
        }
      }
    }

    public void sent(PMessageReceipt msg) {
      if (logger.level <= Logger.FINE) logger.log("Send success "+rm+" to:"+dest+" "+msg);
      cancellable = null;
      rm.setTLCancellable(null);
      removeFromPending(this, dest);      
      rm.sendSuccess();
    }

    public boolean cancel() {
      if (logger.level <= Logger.FINE) logger.log("cancelling "+this);
      return cancellable.cancel();
    }    
    
    public String toString() {
      return "RN{"+rm+"->"+dest+"}";
    }
  }


}
