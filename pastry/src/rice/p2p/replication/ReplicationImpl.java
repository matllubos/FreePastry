/*************************************************************************

"Free Pastry" Peer-to-Peer Application Development Substrate 

Copyright 2002, Rice University. All rights reserved. 

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

- Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

- Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

- Neither  the name  of Rice  University (RICE) nor  the names  of its
contributors may be  used to endorse or promote  products derived from
this software without specific prior written permission.

This software is provided by RICE and the contributors on an "as is"
basis, without any representations or warranties of any kind, express
or implied including, but not limited to, representations or
warranties of non-infringement, merchantability or fitness for a
particular purpose. In no event shall RICE or contributors be liable
for any direct, indirect, incidental, special, exemplary, or
consequential damages (including, but not limited to, procurement of
substitute goods or services; loss of use, data, or profits; or
business interruption) however caused and on any theory of liability,
whether in contract, strict liability, or tort (including negligence
or otherwise) arising in any way out of the use of this software, even
if advised of the possibility of such damage.

********************************************************************************/

package rice.p2p.replication;

import java.util.*;
import java.util.logging.*;

import rice.p2p.commonapi.*;
import rice.p2p.replication.messaging.*;

/**
 * @(#) ReplicationImpl.java
 *
 * This is the implementation of the replication manager.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */
public class ReplicationImpl implements Replication, Application {
  
  /**
   * The amount of time to wait between replications
   */
  public static int MAINTENANCE_INTERVAL = 180000;
  
  /**
   * this application's endpoint
   */
  protected Endpoint endpoint;
  
  /**
   * the logger which we will use
   */
  protected Logger log = Logger.getLogger(this.getClass().getName());
  
  /**
   * the local node handle
   */
  protected NodeHandle handle;
  
  /**
   * The factory for create IdSets and IdRanges
   */
  protected IdFactory factory;
  
  /**
   * This replication's client
   */
  protected ReplicationClient client;
  
  /**
   * The replication factor for this replication
   */
  protected int replicationFactor;
  
  /**
   * Constructor
   *
   * @param node The node below this Replication implementation
   * @param client The client for this Replication
   * @param replicationFactor The replication factor for this instance
   * @param instance The unique instance name of this Replication
   */
  public ReplicationImpl(Node node, ReplicationClient client, int replicationFactor, String instance) {
    this.client = client;
    this.replicationFactor = replicationFactor;
    this.factory = node.getIdFactory();
    
    this.endpoint = node.registerApplication(this, instance);
    this.handle = endpoint.getLocalNodeHandle();
    
  //  log.addHandler(new ConsoleHandler());
  //  log.setLevel(Level.FINER);
  //  log.getHandlers()[0].setLevel(Level.FINER);
    
    log.finer(endpoint.getId() + ": Starting up ReplicationImpl with client " + client + " and factor " + replicationFactor);
    
    // inject the first reminder message, which will cause the replication to begin
    // and the next maintenance message to be scheduled
    endpoint.scheduleMessage(new ReminderMessage(handle), MAINTENANCE_INTERVAL, MAINTENANCE_INTERVAL);
  }
  
  /**
   * Internal method which takes returns set A + set B, or all of the members
   * of set A and set B.
   *
   * @param a The first set
   * @param b The second set
   * @return The merge, a+b
   */
  private IdSet merge(IdSet a, IdSet b) {
    IdSet result = factory.buildIdSet();
    Iterator i = a.getIterator();
    
    while (i.hasNext()) {
      result.addId((Id) i.next());
    }
    
    i = b.getIterator();
    
    while (i.hasNext()) {
      result.addId((Id) i.next());
    }
    
    return result;
  }
  
  /**
   * Internal method which takes returns set A - set B, or all of the members
   * of set A which are not in set B.
   *
   * @param a The first set
   * @param b The second set
   * @return The difference, a-b
   */
  private IdSet difference(IdSet a, IdSet b) {
    IdSet result = factory.buildIdSet();
    Iterator i = a.getIterator();
    
    while (i.hasNext()) {
      Id id = (Id) i.next();
      
      if (! b.isMemberId(id)) 
        result.addId(id);
    }
    
    return result;
  }
  
  /**
   * Returns the range for which the local node is an i root, where i can range between
   * 0 and replicationFactor
   *
   * @return The *total* range
   */
  protected IdRange getTotalRange() {
    IdRange range = endpoint.range(handle, replicationFactor, handle.getId(), true);
    
    if (range == null)
      range = factory.buildIdRange(handle.getId(), handle.getId());
    
    return range;
  }
    
  /**
   * Internal method which updates the client about what his current range is
   */
  private void updateClient() {
    log.fine(endpoint.getId() + ": Updating client with range " + getTotalRange());
    client.setRange(getTotalRange());
  }
  
  /**
   * This internal method sends out the request messages to all of the nodes
   * which hold keys this node may be interested in
   */
  public void sendRequests() {
    log.finer(endpoint.getId() + ": Sending out requests"); 
    NodeHandleSet handles = endpoint.neighborSet(Integer.MAX_VALUE);
    IdRange ourRange = endpoint.range(handle, 0, handle.getId());
    Id ourHash = client.scan(ourRange).hash();
    
    for (int i=0; i<handles.size(); i++) {
      NodeHandle handle = handles.getHandle(i);
      IdRange range = endpoint.range(handle, 0, handle.getId()).intersectRange(getTotalRange());
      Id hash = client.scan(range).hash();

      if ((range != null) && (! range.intersectRange(getTotalRange()).isEmpty())) {
        log.finer(endpoint.getId() + ": Sending request to " + handle + " for range " + range);
        RequestMessage request = new RequestMessage(this.handle, new IdRange[] {range, ourRange}, new Id[] {hash, ourHash});
        log.finer(endpoint.getId() + ": About to pass to endpoint"); 
        endpoint.route(handle.getId(), request, handle);
        log.finer(endpoint.getId() + ": Done passing to endpoint"); 
      }
    }
    log.finer(endpoint.getId() + ": Done sending out requests"); 
  }

  
  // ----- COMMON API METHODS -----
  
  /**
   * This method is invoked on applications when the underlying node
   * is about to forward the given message with the provided target to
   * the specified next hop.  Applications can change the contents of
   * the message, specify a different nextHop (through re-routing), or
   * completely terminate the message.
   *
   * @param message The message being sent, containing an internal message
   * along with a destination key and nodeHandle next hop.
   *
   * @return Whether or not to forward the message further
   */
  public boolean forward(RouteMessage message) {
    return true;
  }
  
  /**
   * This method is called on the application at the destination node
   * for the given id.
   *
   * @param id The destination id of the message
   * @param message The message being sent
   */
  public void deliver(Id id, Message message) {
    log.fine(endpoint.getId() + ": Received message " + message);
    
    if (message instanceof RequestMessage) {
      RequestMessage rm = (RequestMessage) message;
      IdSet response = factory.buildIdSet();
      
      for (int i=0; i<rm.getRanges().length; i++) {
        IdSet set = client.scan(rm.getRanges()[i]);
        
        if (! set.hash().equals(rm.getHashes()[i]))
          response = merge(response, set);
      }
        
      if (response.numElements() > 0)
        endpoint.route(rm.getSource().getId(), new ResponseMessage(handle, response), rm.getSource());
    } else if (message instanceof ResponseMessage) {
      ResponseMessage rm = (ResponseMessage) message;
      IdSet keys = rm.getIdSet();
      IdSet exist = client.scan(getTotalRange());
      IdSet fetch = difference(keys, exist);
        
      if (fetch.numElements() > 0) 
        client.fetch(fetch);
    } else if (message instanceof ReminderMessage) {
      sendRequests(); 
      updateClient(); 
    } else {
      log.warning(endpoint.getId() + ": Received unknown message " + message + " - dropping on floor.");
    }
  }
  
  /**
   * This method is invoked to inform the application that the given node
   * has either joined or left the neighbor set of the local node, as the set
   * would be returned by the neighborSet call.
   *
   * @param handle The handle that has joined/left
   * @param joined Whether the node has joined or left
   */
  public void update(NodeHandle handle, boolean joined) {
    updateClient();
  }
  
}








