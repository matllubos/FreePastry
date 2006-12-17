package rice.pastry.socket;

import java.io.IOException;
import java.util.*;

import rice.Continuation;
import rice.Continuation.ExternalContinuation;
import rice.environment.Environment;
import rice.environment.logging.Logger;
import rice.p2p.commonapi.appsocket.AppSocketReceiver;
import rice.p2p.commonapi.exception.*;
import rice.p2p.commonapi.rawserialization.InputBuffer;
import rice.pastry.*;
import rice.pastry.client.PastryAppl;
import rice.pastry.dist.DistPastryNode;
import rice.pastry.messaging.*;
import rice.pastry.socket.messaging.LeafSetResponseMessage;
import rice.selector.TimerTask;

/**
 * An Socket-based Pastry node, which has two threads - one thread for
 * performing route set and leaf set maintainance, and another thread for
 * listening on the sockets and performing all non-blocking I/O.
 * 
 * @version $Id$
 * @author Alan Mislove
 */
public class SocketPastryNode extends DistPastryNode {

  // The address (ip + port) of this pastry node
  private EpochInetSocketAddress address;

  // The SocketManager, controlling the sockets
  SocketSourceRouteManager srManager;

  /**
   * Constructor
   * 
   * @param id The NodeId of this Pastry node.
   */
  public SocketPastryNode(Id id, Environment e) {
    super(id, e);
  }

  /**
   * Returns the SocketSourceRouteManager for this pastry node.
   * 
   * @return The SocketSourceRouteManager for this pastry node.
   */
  public SocketSourceRouteManager getSocketSourceRouteManager() {
    return srManager;
  }

  /**
   * Helper method which allows the WirePastryNodeFactory to initialize a number
   * of the pastry node's elements.
   * 
   * @param address The address of this pastry node.
   * @param manager The socket manager for this pastry node.
   * @param lsmf Leaf set maintenance frequency. 0 means never.
   * @param rsmf Route set maintenance frequency. 0 means never.
   * @param sManager The new SocketElements value
   * @param pingManager The new SocketElements value
   * @param pool The new SocketElements value
   */
  public void setSocketElements(EpochInetSocketAddress address,
      int lsmf, int rsmf) {
    this.address = address;
    this.leafSetMaintFreq = lsmf;
    this.routeSetMaintFreq = rsmf;
  }

  /**
   * Called after the node is initialized.
   * 
   * @param bootstrap The node which this node should boot off of.
   */
  public void doneNode(NodeHandle[] bootstrap) {
    super.doneNode(bootstrap);
    initiateJoin(bootstrap);
  }

  /**
   * Prints out a String representation of this node
   * 
   * @return a String
   */
  public String toString() {
    return "SocketNodeHandle (" + getNodeId() + "/" + address + ")";
  }

  /**
   * Makes this node resign from the network. Is designed to be used for
   * debugging and testing.
   * 
   * If run on the SelectorThread, then destroys now. Other threads cause a task
   * to be placed on the selector, and destroyed asap. Make sure to call
   * super.destroy() !!!
   */
  public void destroy() {
    if (getEnvironment().getSelectorManager().isSelectorThread()) {
      // destroy now
      try {
        super.destroy();
        if (srManager != null) srManager.destroy();
      } catch (IOException e) {
        getEnvironment().getLogManager().getLogger(SocketPastryNode.class,
            "ERROR: Got exception " + e + " while resigning node!");
      }
    } else {
      // schedule to be destroyed on the selector
      getEnvironment().getSelectorManager().invoke(new Runnable() {
        public void run() {
          destroy();
        }
      });
    }
  }

  public NodeHandle coalesce(NodeHandle newHandle) {
    return srManager.coalesce((SocketNodeHandle)newHandle);
  }

  public void setSocketSourceRouteManager(SocketSourceRouteManager srManager) {
    this.srManager = srManager;
  }

  public void send(NodeHandle handle, Message message) {
    SocketNodeHandle snh = (SocketNodeHandle) handle;
    if (getNodeId().equals(snh.getId())) {
      //debug("Sending message " + msg + " locally");
      receiveMessage(message);
    } else {
      if (logger.level <= Logger.FINER) logger.log(
          "Passing message " + message + " to the socket controller for writing");
      try {
        getSocketSourceRouteManager().send(snh.getEpochAddress(), message);
      } catch (IOException ioe) {
        throw new RuntimeException(ioe); 
      }
    }    
  }
  
  
  /**
   * Try multiple times... it turned out that trying only once was failing often.
   * 
   * @param bootstrap
   * @param c
   * @param timeout
   * @param tries
   */
  public void testFireWall(NodeHandle bootstrap, final Continuation c, int timeout, int tries) {
    for (int i = 0; i < tries; i++) {
      ExternalContinuation ec = new ExternalContinuation();
      testFireWall(bootstrap, ec, 5000);
      ec.sleep();
      Boolean resultB = (Boolean) ec.getResult();
      boolean result = resultB.booleanValue();
      if (result) {
        c.receiveResult(new Boolean(true)); 
        return;
      } 
      try {
        Thread.sleep(getEnvironment().getRandomSource().nextInt(timeout));
      } catch (InterruptedException ie) {
        throw new RuntimeException(ie); 
      }
    }
    c.receiveResult(new Boolean(false));
  }
  
  // this code tests the firewall using a ping
  HashSet fireWallContinuations = new HashSet();
  public void testFireWall(NodeHandle bootstrap, final Continuation c, int timeout) {
    if (logger.level <= Logger.FINER) logger.log("testFireWall("+bootstrap+","+timeout+")");
    synchronized(fireWallContinuations) {
      fireWallContinuations.add(c);
    }
    getEnvironment().getSelectorManager().getTimer().schedule(new TimerTask() {    
      public void run() {
        synchronized(fireWallContinuations) {
          if (fireWallContinuations.remove(c)) {
            c.receiveResult(new Boolean(false));
          }          
        }    
      }    
    }, timeout);
    
    SocketNodeHandle snh = (SocketNodeHandle)bootstrap;
    EpochInetSocketAddress[] rt = {snh.getEpochAddress(),address};
    SourceRoute sr = SourceRoute.build(rt);
    if (logger.level <= Logger.FINER) logger.log("testFireWall("+bootstrap+","+timeout+"):"+sr);
    srManager.getManager().getPingManager().ping(sr,new PingResponseListener() {
    
      public void pingResponse(SourceRoute path, long RTT, long timeHeardFrom) {
        synchronized(fireWallContinuations) {
          if (fireWallContinuations.remove(c)) {
            c.receiveResult(new Boolean(true));
          }          
        }    
      }    
    });

    
//    srManager.getAddressManager(snh.getEpochAddress()).getRouteManager(sr).send(new LeafSetResponseMessage(null));
  }

  public void connect(NodeHandle handle, AppSocketReceiver receiver, PastryAppl appl, int timeout) {
    SocketNodeHandle snh = (SocketNodeHandle) handle;
      if (logger.level <= Logger.FINER) logger.log(
          "Opening app socket "+appl.getAddress()+" to "+handle);
      getSocketSourceRouteManager().connect(snh.getEpochAddress(), appl.getAddress(), receiver, timeout);
  }

  public void acceptAppSocket(SocketAppSocket socket, int appId) throws AppSocketException {
    PastryAppl acceptorAppl = getMessageDispatch().getDestinationByAddress(appId);
    if (acceptorAppl == null) throw new AppNotRegisteredException();
    if (!acceptorAppl.receiveSocket(socket)) throw new NoReceiverAvailableException();
  }

  public NodeHandle readNodeHandle(InputBuffer buf) throws IOException {    
    return coalesce(SocketNodeHandle.build(buf));
  }

  
  public int proximity(NodeHandle nh) {
    if (nh.getNodeId().equals(getNodeId()))
      return 0;
    else
      return getSocketSourceRouteManager().proximity(((SocketNodeHandle) nh).eaddress);
  }

// public void testFireWallUDP(NodeHandle bootstrap) {
//  }
  
//  public synchronized void receiveMessage(Message msg) {
//    if (msg instanceof LeafSetResponseMessage) {
//      synchronized(fireWallContinuations) {
//        Iterator i = fireWallContinuations.iterator(); 
//        while(i.hasNext()) {
//          Continuation c = (Continuation)i.next(); 
//          c.receiveResult(new Boolean(true));
//        }
//        fireWallContinuations.clear();
//      }
//    } else {
//      super.receiveMessage(msg);
//    } 
//  }
}

