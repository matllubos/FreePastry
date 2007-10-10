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
package rice.pastry;

import java.util.*;

import org.mpisws.p2p.transport.MessageRequestHandle;
import org.mpisws.p2p.transport.SocketRequestHandle;
import org.mpisws.p2p.transport.liveness.LivenessProvider;
import org.mpisws.p2p.transport.proximity.ProximityProvider;

import rice.*;
import rice.environment.Environment;
import rice.environment.logging.Logger;
import rice.p2p.commonapi.appsocket.AppSocketReceiver;
import rice.pastry.boot.Bootstrapper;
import rice.pastry.client.PastryAppl;
import rice.pastry.leafset.LeafSet;
import rice.pastry.messaging.*;
import rice.pastry.routing.*;
import rice.pastry.transport.PMessageNotification;
import rice.pastry.transport.PMessageReceipt;

/**
 * A Pastry node is single entity in the pastry network.
 * 
 * @version $Id$
 * 
 * @author Andrew Ladd
 */

public abstract class PastryNode extends Observable implements rice.p2p.commonapi.Node, Destructable, NodeHandleFactory, LivenessProvider<NodeHandle>, ProximityProvider<NodeHandle> {

  protected Id myNodeId;

  private Environment myEnvironment;

  private MessageDispatch myMessageDispatch;

  protected LeafSet leafSet;

  protected RoutingTable routeSet;

  protected NodeHandle localhandle;

  protected Vector apps;

  protected Logger logger;
  
  public abstract NodeHandle coalesce(NodeHandle newHandle);
    
  ReadyStrategy readyStrategy;
  
  protected boolean joinFailed = false;
  
  protected boolean isDestroyed = false;
  
  protected Router router;
  
  /**
   * Constructor, with NodeId. Need to set the node's ID before this node is
   * inserted as localHandle.localNode.
   */
  protected PastryNode(Id id, Environment e) {
    myEnvironment = e;
    myNodeId = id;
    
    readyStrategy = getDefaultReadyStrategy();
    
    apps = new Vector();
    logger = e.getLogManager().getLogger(getClass(), null);
  }
  
  /**
   * Simple Ready Strategy
   */
  public ReadyStrategy getDefaultReadyStrategy() {
    return new ReadyStrategy() {
      private boolean ready = false; 
      
      public void setReady(boolean r) {
        if (r != ready) {
          synchronized(PastryNode.this) {
            ready = r;
          }
          notifyReadyObservers();
        }
      }
      
      public boolean isReady() {
        return ready;  
      }
      
      public void start() {
        // don't need to do any initialization 
      }

      public void stop() {
        // don't need to do any initialization
      }
    };
  }
  
  public void setReadyStrategy(ReadyStrategy rs) {
//    logger.log("setReadyStrategy("+rs+")");
//    logger.logException("setReadyStrategy("+rs+")", new Exception());
    readyStrategy = rs; 
  }
  
  /**
   * Combined accessor method for various members of PastryNode. These are
   * generated by node factories, and assigned here.
   * 
   * Other elements specific to the wire protocol are assigned via methods
   * set{RMI,Direct}Elements in the respective derived classes.
   * 
   * @param lh
   *          Node handle corresponding to this node.
   * @param sm
   *          Security manager.
   * @param md
   *          Message dispatcher.
   * @param ls
   *          Leaf set.
   * @param rt
   *          Routing table.
   */
  public void setElements(NodeHandle lh, MessageDispatch md, LeafSet ls, RoutingTable rt, Router router) {
    localhandle = lh;
    setMessageDispatch(md);
    leafSet = ls;
    routeSet = rt;
    this.router = router;
  }

  public rice.p2p.commonapi.NodeHandle getLocalNodeHandle() {
    return localhandle;
  }

  public Environment getEnvironment() {
    return myEnvironment; 
  }
  
  public NodeHandle getLocalHandle() {
    return localhandle;
  }

  public Id getNodeId() {
    return myNodeId;
  }

  public boolean isReady() {
    return readyStrategy.isReady();
  }

  /**
   * FOR TESTING ONLY - DO NOT USE!
   */
  public MessageDispatch getMessageDispatch() {
    return myMessageDispatch;
  }

  public void setMessageDispatch(MessageDispatch md) {
    myMessageDispatch = md;
    addDestructable(myMessageDispatch);
  }

  public Destructable addDestructable(Destructable d) {
    destructables.add(d);    
    return d;
  }

  public boolean removeDestructable(Destructable d) {
    return destructables.remove(d);    
  }

  /**
   * Overridden by derived classes, and invoked when the node has joined
   * successfully.
   * 
   * This one is for backwards compatability. It will soon be deprecated.
   */
  public abstract void nodeIsReady();

  /**
   * Overridden by derived classes, and invoked when the node has joined
   * successfully. This should probably be abstract, but maybe in a later
   * version.
   * 
   * @param state
   *          true when the node is ready, false when not
   */
  public void nodeIsReady(boolean state) {

  }

  /**
   * Overridden by derived classes to initiate the join process
   * 
   * @param bootstrap
   *          Node handle to bootstrap with.
   */
  public abstract void initiateJoin(Collection<NodeHandle> bootstrap);

  public void setReady() {
    setReady(true);
  }

  public void setReady(boolean ready) {
    readyStrategy.setReady(ready); 
  }
  
  /**
   * This variable makes it so notifyReady() is only called on the apps once.
   * Deprecating
   */
  private boolean neverBeenReady = true;

  public void notifyReadyObservers() {
  
    // It is possible to have the setReady() invoked more than once if the
    // message denoting the termination of join protocol is duplicated.
    boolean ready = readyStrategy.isReady();
    //      if (r == false)
    if (logger.level <= Logger.INFO) logger.log("PastryNode.notifyReadyObservers("+ready+")");

    if (ready) {
      nodeIsReady(); // deprecate this
      nodeIsReady(true);

      setChanged();
      notifyObservers(Boolean.valueOf(true));

      if (neverBeenReady) {
        // notify applications
        // we iterate over private copy to allow addition of new apps in the
        // context of notifyReady()
        Vector tmpApps = new Vector(apps);
        Iterator it = tmpApps.iterator();
        while (it.hasNext())
          ((PastryAppl) (it.next())).notifyReady();
        neverBeenReady = false;
      }

      // signal any apps that might be waiting for the node to get ready
      synchronized (this) {
        // NN: not a problem, because we already changed the state in the calling method
        notifyAll();
      }
    } else {
      nodeIsReady(false);
      setChanged();
      notifyObservers(new Boolean(false));

      //        Vector tmpApps = new Vector(apps);
      //        Iterator it = tmpApps.iterator();
      //        while (it.hasNext())
      //           ((PastryAppl) (it.next())).notifyFaulty();
    }
  }

  /**
   * Called by the layered Pastry application to check if the local pastry node
   * is the one that is currently closest to the object key id.
   * 
   * @param key
   *          the object key id
   * 
   * @return true if the local node is currently the closest to the key.
   */
  public boolean isClosest(Id key) {

    if (leafSet.mostSimilar(key) == 0)
      return true;
    else
      return false;
  }

  public LeafSet getLeafSet() {
    return leafSet;
  }

  public RoutingTable getRoutingTable() {
    return routeSet;
  }

  /**
   * Add a leaf set observer to the Pastry node.
   * 
   * @deprecated use addLeafSetListener
   * @param o the observer.
   */
  public void addLeafSetObserver(Observer o) {
    leafSet.addObserver(o);
  }

  /**
   * Delete a leaf set observer from the Pastry node.
   * 
   * @deprecated use deleteLeafSetListener
   * @param o the observer.
   */
  public void deleteLeafSetObserver(Observer o) {
    leafSet.deleteObserver(o);
  }

  public void addLeafSetListener(NodeSetListener listener) {
    leafSet.addNodeSetListener(listener);
  }
  public void deleteLeafSetListener(NodeSetListener listener) {
    leafSet.deleteNodeSetListener(listener);
  }
  /**
   * Add a route set observer to the Pastry node.
   * 
   * @deprecated use addRouteSetListener
   * @param o the observer.
   */
  public void addRouteSetObserver(Observer o) {
    routeSet.addObserver(o);
  }

  /**
   * Delete a route set observer from the Pastry node.
   * 
   * @deprecated use deleteRouteSetListener
   * @param o the observer.
   */
  public void deleteRouteSetObserver(Observer o) {
    routeSet.deleteObserver(o);
  }

  public void addRouteSetListener(NodeSetListener listener) {
    routeSet.addNodeSetListener(listener);
  }

  public void removeRouteSetListener(NodeSetListener listener) {
    routeSet.removeNodeSetListener(listener);
  }

  /**
   * message receiver interface. synchronized so that the external message
   * processing thread and the leafset/route maintenance thread won't interfere
   * with application messages.
   */
  public synchronized void receiveMessage(Message msg) {
    if (isDestroyed) return;
    if (logger.level <= Logger.FINE) logger.log("receiveMessage("+msg+")");
    myMessageDispatch.dispatchMessage(msg);
  }
  
  public synchronized void receiveMessage(RawMessageDelivery delivery) {
    myMessageDispatch.dispatchMessage(delivery); 
  }
  
  /**
   * Registers a message receiver with this Pastry node.
   * 
   * @param cred
   *          the credentials.
   * @param address
   *          the address that the receiver will be at.
   * @param receiver
   *          the message receiver.
   */
  public void registerReceiver(int address,
      PastryAppl receiver) {
    myMessageDispatch.registerReceiver(address, receiver);
  }

  /**
   * Registers an application with this pastry node.
   * 
   * @param app
   *          the application
   */

  public void registerApp(PastryAppl app) {
    if (isReady())
      app.notifyReady();
    apps.add(app);
  }

  /**
   * Schedule the specified message to be sent to the local node after a
   * specified delay. Useful to provide timeouts.
   * 
   * @param msg
   *          a message that will be delivered to the local node after the
   *          specified delay
   * @param delay
   *          time in milliseconds before message is to be delivered
   * @return the scheduled event object; can be used to cancel the message
   */
  public abstract ScheduledMessage scheduleMsg(Message msg, long delay);

  /**
   * Schedule the specified message for repeated fixed-delay delivery to the
   * local node, beginning after the specified delay. Subsequent executions take
   * place at approximately regular intervals separated by the specified period.
   * Useful to initiate periodic tasks.
   * 
   * @param msg
   *          a message that will be delivered to the local node after the
   *          specified delay
   * @param delay
   *          time in milliseconds before message is to be delivered
   * @param period
   *          time in milliseconds between successive message deliveries
   * @return the scheduled event object; can be used to cancel the message
   */
  public abstract ScheduledMessage scheduleMsg(Message msg, long delay,
      long period);

  /**
   * Schedule the specified message for repeated fixed-rate delivery to the
   * local node, beginning after the specified delay. Subsequent executions take
   * place at approximately regular intervals, separated by the specified
   * period.
   * 
   * @param msg
   *          a message that will be delivered to the local node after the
   *          specified delay
   * @param delay
   *          time in milliseconds before message is to be delivered
   * @param period
   *          time in milliseconds between successive message deliveries
   * @return the scheduled event object; can be used to cancel the message
   */
  public abstract ScheduledMessage scheduleMsgAtFixedRate(Message msg,
      long delay, long period);

  public String toString() {
    return "Pastry node " + myNodeId.toString();
  }

  // Common API Support

  /**
   * This returns a VirtualizedNode specific to the given application and
   * instance name to the application, which the application can then use in
   * order to send an receive messages.
   * 
   * @deprecated use buildEndpoint() endpoint.register()
   * 
   * @param application
   *          The Application
   * @param instance
   *          An identifier for a given instance
   * @return The endpoint specific to this applicationk, which can be used for
   *         message sending/receiving.
   */
  public rice.p2p.commonapi.Endpoint registerApplication(
      rice.p2p.commonapi.Application application, String instance) {
    return new rice.pastry.commonapi.PastryEndpoint(this, application, instance, true);
  }

  public rice.p2p.commonapi.Endpoint buildEndpoint(
      rice.p2p.commonapi.Application application, String instance) {
    return new rice.pastry.commonapi.PastryEndpoint(this, application, instance, false);
  }

  /**
   * This returns a Endpoint specific to the given application and instance name
   * to the application, which the application can then use in order to send an
   * receive messages. This method allows advanced developers to specify which
   * "port" on the node they wish their application to register as. This "port"
   * determines which of the applications on top of the node should receive an
   * incoming message.
   * 
   * @param application
   *          The Application
   * @param port
   *          The port to use
   * @return The endpoint specific to this applicationk, which can be used for
   *         message sending/receiving.
   */
//  public rice.p2p.commonapi.Endpoint registerApplication(
//      rice.p2p.commonapi.Application application, int port) {
//    return new rice.pastry.commonapi.PastryEndpoint(this, application, port);
//  }

  /**
   * Returns the Id of this node
   * 
   * @return This node's Id
   */
  public rice.p2p.commonapi.Id getId() {
    return getNodeId();
  }

  /**
   * Returns a factory for Ids specific to this node's protocol.
   * 
   * @return A factory for creating Ids.
   */
  public rice.p2p.commonapi.IdFactory getIdFactory() {
    return new rice.pastry.commonapi.PastryIdFactory(getEnvironment());
  }

  /**
   * Schedules a job for processing on the dedicated processing thread, should
   * one exist. CPU intensive jobs, such as encryption, erasure encoding, or
   * bloom filter creation should never be done in the context of the underlying
   * node's thread, and should only be done via this method.
   * 
   * @param task
   *          The task to run on the processing thread
   * @param command
   *          The command to return the result to once it's done
   */
  public void process(Executable task, Continuation command) {
    try {
      myEnvironment.getProcessor().process(task, 
          command, 
          myEnvironment.getSelectorManager(), 
          myEnvironment.getTimeSource(), 
          myEnvironment.getLogManager());
      
//      command.receiveResult(task.execute());
    } catch (final Exception e) {
      command.receiveException(e);
    }
  }

  HashSet<Destructable> destructables = new HashSet<Destructable>();
  
  /**
   * Method which kills a PastryNode.  Note, this doesn't implicitly kill the environment.
   * 
   * Make sure to call super.destroy() !!!
   */
  public void destroy() {
    if (isDestroyed) return;
    if (logger.level <= Logger.INFO) logger.log("Destroying "+this);
    isDestroyed = true;
    Iterator<Destructable> i = destructables.iterator();
    while(i.hasNext()) {
      Destructable d = i.next();
      if (logger.level <= Logger.INFO - 5) logger.log("Destroying "+d);
      d.destroy(); 
    }
    getEnvironment().removeDestructable(this);
  }


  /**
   * Deliver message to the NodeHandle.
   * 
   * @param nh
   * @param m
   * @return
   */
  abstract public PMessageReceipt send(NodeHandle handle, Message message, 
      PMessageNotification deliverAckToMe, Map<String, Integer> options);
  
  /**
   * Called by PastryAppl to ask the transport layer to open a Socket to its counterpart on another node.
   * 
   * @param handle
   * @param receiver
   * @param appl
   */
  abstract public SocketRequestHandle connect(NodeHandle handle, AppSocketReceiver receiver, PastryAppl appl, int timeout);

  /**
   * The proximity of the node handle.
   * 
   * @param nh
   * @return
   */
  abstract public int proximity(NodeHandle nh);

  
  protected JoinFailedException joinFailedReason;
  public void joinFailed(JoinFailedException cje) {
    if (logger.level <= Logger.WARNING) logger.log("joinFailed("+cje+")");
    joinFailedReason = cje;
    synchronized(this) {
      joinFailed = true;
      this.notifyAll();
    }
    setChanged();
    this.notifyObservers(cje); 
  }

  /**
   * Returns true if there was a fatal error Joining
   * @return
   */
  public boolean joinFailed() {
    return joinFailed; 
  }
  
  public JoinFailedException joinFailedReason() {
    return joinFailedReason; 
  }

  abstract public Bootstrapper getBootstrapper();

  public Router getRouter() {
    return router;
  }
  
  public void addNetworkListener(NetworkListener nl) {
    // TODO: implement 
  }
  
}

