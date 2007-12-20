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
package rice.tutorial.lookup;

import java.util.HashMap;

import rice.Continuation;
import rice.environment.logging.Logger;
import rice.p2p.commonapi.*;

/**
 * The LookupService provides a way to find out what nodes are responsible for
 * a given Id.  It does this by routing a request across the ring, doing a
 * replicaSet lookup at the destination, and sending the result back.  It
 * is perhaps the simplest useful application.
 * 
 * @author jstewart
 *
 */
public class LookupService implements Application {

  private Endpoint endpoint;
  private int seqno;
  private HashMap<Integer,Continuation<NodeHandleSet,Exception>> pending;
  private long timeout;
  private Logger logger;

  /**
   * Build a LookupService application with a specified timeout
   * 
   * @param node the local node
   * @param timeout query timeout in milliseconds
   */
  public LookupService(Node node, long timeout) {
    this.pending = new HashMap<Integer,Continuation<NodeHandleSet,Exception>>();
    this.seqno = 42;
    
    this.endpoint = node.buildEndpoint(this, null);
    endpoint.register();

    this.logger = endpoint.getEnvironment().getLogManager().getLogger(getClass(), null);

    // timeouts should really be adaptive, but that takes more code than this simple app is worth
    if (timeout > 0) {
      this.timeout = timeout;
    } else {
      // get a default from the parameters file
      this.timeout = endpoint.getEnvironment().getParameters().getLong("lookup_service.timeout");
      if (this.timeout <= 0)
        this.timeout = 30000;  // a sane but very large value
    }
  }
  
  /**
   * Build a LookupService application with the default lookup timeout
   * 
   * @param node the local node
   */
  public LookupService(Node node) {
    this(node, -1);
  }
  
  /**
   * This Application always forwards its messages
   * 
   * @see rice.p2p.commonapi.Application#forward(rice.p2p.commonapi.RouteMessage)
   */
  public boolean forward(RouteMessage message) {
    return true;
  }

  public void deliver(Id id, Message message) {
    if (message instanceof NodeLookupQuery) {
      NodeLookupQuery query = (NodeLookupQuery) message;
      
      if (logger.level <= Logger.FINER) logger.log("NodeLookup query received for "+id+" with sequence number "+query.seqno);

      // this is the only interesting line of code in this method, if you can call it interesting
      // get our local replica set and ship it back in a response message
      endpoint.route(null, new NodeLookupResponse(endpoint.replicaSet(id, query.numNodes), query.seqno), query.source);
    
    } else if (message instanceof NodeLookupResponse) {
      NodeLookupResponse response = (NodeLookupResponse) message;
      
      if (pending.containsKey(response.seqno)) {
        
        if (logger.level <= Logger.FINER) logger.log("NodeLookup response received with sequence number "+response.seqno);
        
        pending.remove(response.seqno).receiveResult(response.nodes);
        
      } else {
        if (logger.level <= Logger.INFO) logger.log("NodeLookup received response for non-existent or expired lookup "+response.seqno);
      }
    
    } else if (message instanceof NodeLookupTimeout) {
      NodeLookupTimeout response = (NodeLookupTimeout) message;
    
      // if it's not in pending, then a message has already been delivered
      if (pending.containsKey(response.seqno)) {
        
        if (logger.level <= Logger.FINE) logger.log("NodeLookup timed out with sequence number "+response.seqno);
        
        pending.remove(response.seqno).receiveException(new NodeLookupTimeoutException());
        
      }
      
    } else {
      if (logger.level <= Logger.WARNING) logger.log("NodeLookup received unexpected message "+message);
    }
  }

  public void update(NodeHandle handle, boolean joined) {
    // do nothing
    // this is part of the Application interface, but we don't care about updates
  }

  /**
   * Requests a replicaSet from a node across the ring.  This method will return via its 
   * continuation the set of <tt>num</tt> NodeHandles that are in <tt>id</tt>'s replicaSet.
   * Calling requestNodeHandles with a <tt>num</tt> of 1 will return the node currently
   * responsible for Id id.
   * 
   * If there is a network problem or the Id of interest is not currently owned by any node
   * (for example due to churn in the overlay), the continuation will receive a 
   * NodeLookupTimeoutException.
   * 
   * @param id the Id of interest
   * @param num the number of nodes to return
   * @param cont the continuation to return the result to
   */
  public void requestNodeHandles(Id id, int num, Continuation<NodeHandleSet,Exception> cont) {
    int seq = seqno++;
    pending.put(seq, cont);
    
    if (logger.level <= Logger.FINER) logger.log("NodeLookup being sent to id  "+id+" with sequence number "+seq);
    
    endpoint.route(id, new NodeLookupQuery(endpoint.getLocalNodeHandle(), num, seq), null);
    endpoint.scheduleMessage(new NodeLookupTimeout(seq), timeout);
  }
  
  /**
   * Find the primary replica of a key.  This is the node with the Id closest to the key in pastry.
   * This method will return via its continuation the NodeHandle that is closest to <tt>id</tt>.
   *
   * If there is a network problem or the Id of interest is not currently owned by any node
   * (for example due to churn in the overlay), the continuation will receive a 
   * NodeLookupTimeoutException.
   * 
   * @param id the Id of interest
   * @param cont the continuation to return the result to
   */
  public void requestNodeHandle(Id id, final Continuation<NodeHandle,Exception> cont) {
    int seq = seqno++;
    pending.put(seq, new Continuation<NodeHandleSet, Exception>() {
      public void receiveException(Exception exception) {
        cont.receiveException(exception);
      }

      public void receiveResult(NodeHandleSet result) {
        if (result.size() != 1) {
          // this really shouldn't happen
          receiveException(new IndexOutOfBoundsException("Expected 1 result, got "+result.size()));
        } else {
          cont.receiveResult(result.getHandle(0));
        }
      }
    });
    
    if (logger.level <= Logger.FINER) logger.log("NodeLookup being sent to id  "+id+" with sequence number "+seq);
    
    endpoint.route(id, new NodeLookupQuery(endpoint.getLocalNodeHandle(), 1, seq), null);
    endpoint.scheduleMessage(new NodeLookupTimeout(seq), timeout);
  }
  
  /**
   * This is a message used internally for implementing timeouts
   * 
   * @author jstewart
   *
   */
  public static class NodeLookupTimeout implements Message {
    private static final long serialVersionUID = 1648981192042898092L;
    int seqno;

    public NodeLookupTimeout(int seqno) {
      this.seqno = seqno;
    }

    public int getPriority() {
      return 0;
    }

  }

  /**
   * The query message
   * 
   * @author jstewart
   *
   */
  public static class NodeLookupQuery implements Message {
    private static final long serialVersionUID = -4882776401593706141L;
    protected NodeHandle source;
    protected int numNodes;
    protected int seqno;

    public NodeLookupQuery(NodeHandle localHandle, int num, int seqno) {
      this.numNodes = num;
      this.seqno = seqno;
      this.source = localHandle;
    }

    public int getPriority() {
      return 0;
    }
  }
  
  /**
   * The response message
   * 
   * @author jstewart
   *
   */
  public static class NodeLookupResponse implements Message {
    private static final long serialVersionUID = -3200682143184682743L;
    protected NodeHandleSet nodes;
    protected int seqno;

    public NodeLookupResponse(NodeHandleSet nodes, int seqno) {
      this.nodes = nodes;
      this.seqno = seqno;
    }

    public int getPriority() {
      return 0;
    }
  }

  /**
   * Returned by continuations handed to requestNodeHandles when a request
   * times out.
   * 
   * @author jstewart
   *
   */
  public static class NodeLookupTimeoutException extends Exception {
    private static final long serialVersionUID = -9138111846775601203L;
  }
}
