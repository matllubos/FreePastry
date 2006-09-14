
package rice.p2p.commonapi;

import java.io.IOException;
import java.util.List;

import rice.*;
import rice.environment.Environment;
import rice.p2p.commonapi.appsocket.AppSocketReceiver;
import rice.p2p.commonapi.rawserialization.*;

/**
 * @(#) Endpoint.java
 *
 * Interface which represents a node in a peer-to-peer system, regardless of
 * the underlying protocol.  This represents the *local* node, upon which applications
 * can call methods.  
 *
 * @version $Id$
 *
 * @author Alan Mislove
 * @author Peter Druschel
 */
public interface Endpoint extends NodeHandleReader {

  /**
   * Returns this node's id, which is its identifier in the namespace.
   *
   * @return The local node's id
   */
  public Id getId();
  
  /**
   * This method makes an attempt to route the message to the root of the given id.
   * The hint handle will be the first hop in the route. If the id field is null, then
   * the message is routed directly to the given node, and delivers the message
   * there.  If the hint field is null, then this method makes an attempt to route
   * the message to the root of the given id.  Note that one of the id and hint fields can
   * be null, but not both.
   *
   * @param id The destination Id of the message.
   * @param message The message to deliver
   * @param hint The first node to send this message to, optional
   */
  void route(Id id, Message message, NodeHandle hint);

  /**
   * Same as the other call, but uses the Raw serialization rather than java serialization.
   * @param id
   * @param message
   * @param hint
   */
  void route(Id id, RawMessage message, NodeHandle hint);

  /**
   * This call produces a list of nodes that can be used as next hops on a route towards
   * the given id, such that the resulting route satisfies the overlay protocol's bounds
   * on the number of hops taken.  If the safe flag is specified, then the fraction of
   * faulty nodes returned is no higher than the fraction of faulty nodes in the overlay.
   *
   * @param id The destination id.
   * @param num The number of nodes to return.
   * @param safe Whether or not to return safe nodes.
   */
  NodeHandleSet localLookup(Id id, int num, boolean safe);

  /**
   * This methods returns an unordered set of nodehandles on which are neighbors of the local
   * node in the id space.  Up to num handles are returned.
   *
   * @param num The number of desired handle to return.
   */
  NodeHandleSet neighborSet(int num);

  /**
   * This method returns an ordered set of nodehandles on which replicas of an object with
   * a given id can be stored.  The call returns nodes up to and including a node with maxRank.
   *
   * This method is equivalent to calling
   *
   * replicaSet(id, maxRank, getLocalNodeHandle(), neighborSet(Integer.MAX_VALUE));
   *
   * @param id The object's id.
   * @param maxRank The number of desired replicas.
   */
  NodeHandleSet replicaSet(Id id, int maxRank);
  
  /**
   * This methods returns an ordered set of nodehandles on which replicas of an object with
   * a given id can be stored.  The call returns nodes up to and including a node with maxRank.
   * This call also allows the application to provide a remote "center" node, as well as
   * other nodes in the vicinity. 
   *
   * @param id The object's id.
   * @param maxRank The number of desired replicas.
   * @param handle The root handle of the remove set
   * @param set The set of other nodes around the root handle
   */
  NodeHandleSet replicaSet(Id id, int maxRank, NodeHandle root, NodeHandleSet set);

  /**
   * This operation provides information about ranges of keys for which the node is currently
   * a rank-root. The operations returns null if the range could not be determined, the range
   * otherwise. It is an error to query the range of a node not present in the neighbor set as
   * returned bythe update upcall or the neighborSet call. Certain implementations may return
   * an error if rank is greater than zero. Some protocols may have multiple, disjoint ranges
   * of keys for which a given node is responsible. The parameter lkey allows the caller to
   * specify which region should be returned. If the node referenced by is responsible for key
   * lkey, then the resulting range includes lkey. Otherwise, the result is the nearest range
   * clockwise from lkey for which is responsible.
   *
   * @param handle The handle whose range to check.
   * @param rank The root rank.
   * @param lkey An "index" in case of multiple ranges.
   */
  IdRange range(NodeHandle handle, int rank, Id lkey) throws RangeCannotBeDeterminedException;
  
  /**
   * This operation provides information about ranges of keys for which the node is currently
   * a rank-root. The operations returns null if the range could not be determined, the range
   * otherwise. It is an error to query the range of a node not present in the neighbor set as
   * returned bythe update upcall or the neighborSet call. Certain implementations may return
   * an error if rank is greater than zero. Some protocols may have multiple, disjoint ranges
   * of keys for which a given node is responsible. The parameter lkey allows the caller to
   * specify which region should be returned. If the node referenced by is responsible for key
   * lkey, then the resulting range includes lkey. Otherwise, the result is the nearest range
   * clockwise from lkey for which is responsible.
   *
   * @param handle The handle whose range to check.
   * @param rank The root rank.
   * @param lkey An "index" in case of multiple ranges.
   * @param cumulative Whether to return the cumulative or single range
   */
  IdRange range(NodeHandle handle, int rank, Id lkey, boolean cumulative) throws RangeCannotBeDeterminedException;

  /**
   * Returns a handle to the local node below this endpoint.  This node handle is serializable,
   * and can therefore be sent to other nodes in the network and still be valid.
   *
   * @return A NodeHandle referring to the local node.
   */
  NodeHandle getLocalNodeHandle();

  /**
   * Schedules a message to be delivered to this application after the provided number of
   * milliseconds.
   *
   * @param message The message to be delivered
   * @param delay The number of milliseconds to wait before delivering the message
   */
  CancellableTask scheduleMessage(Message message, long delay);
  
  /**
   * Schedules a message to be delivered to this application every period number of 
   * milliseconds, after delay number of miliseconds have passed.
   *
   * @param message The message to be delivered
   * @param delay The number of milliseconds to wait before delivering the fist message
   * @param delay The number of milliseconds to wait before delivering subsequent messages
   */
  CancellableTask scheduleMessage(Message message, long delay, long period);
    
  /**
   * Schedules a message to be delivered to this application every period number of 
   * milliseconds, after delay number of miliseconds have passed.
   *
   * @param message The message to be delivered
   * @param delay The number of milliseconds to wait before delivering the fist message
   * @param delay The number of milliseconds to wait before delivering subsequent messages
   */
  CancellableTask scheduleMessageAtFixedRate(Message message, long delay, long period);
    
  /**
   * Schedules a job for processing on the dedicated processing thread.  CPU intensive jobs, such
   * as encryption, erasure encoding, or bloom filter creation should never be done in the context
   * of the underlying node's thread, and should only be done via this method.  
   *
   * @param task The task to run on the processing thread
   * @param command The command to return the result to once it's done
   */
  void process(Executable task, Continuation command);
  
  /**
   * Returns a unique instance name of this endpoint, sort of a mailbox name for this
   * application.
   * 
   * @return The unique instance name of this application
   */
  public String getInstance();
  
  /**
   * Returns the environment.  This allows the nodes to be virtualized within the JVM
   * @return the environment for this node/app.
   */
  public Environment getEnvironment();

  /**
   * Set's the acceptor for this application.  If no acceptor is set, then when a remote
   * node's application opens a socket here, they will get an *Exception*
   *
   * @param receiver calls receiveSocket() when a new AppSocket is opened to this application
   * from a remote node.
   * Note that you must call accept() again after each socket is received to properly handle
   * socket backlogging
   */
  public void accept(AppSocketReceiver receiver);

  /**
   * Opens a connection to this application on a remote node.
   * If no acceptor is set, then receiver will get an *Exception*
   *
   * @param receiver calls receiveSocket() when a new AppSocket is opened to this application
   * on a remote node.
   */
  public void connect(NodeHandle handle, AppSocketReceiver receiver, int timeout);
  
  /**
   * To use a more efficient serialization format than Java Serialization
   * 
   * @param md
   */
  public void setDeserializer(MessageDeserializer md);
  
  /**
   * Returns the deserializer.  The default deserializer can deserialize rice.p2p.util.JavaSerializedMessage
   * @return
   */
  public MessageDeserializer getDeserializer();
  
  /**
   * To use a more efficient serialization format than Java Serialization
   * 
   * @param md
   */
  public Id readId(InputBuffer buf, short type) throws IOException;

  /**
   * Returns an ordered list of the nearest known neighbors.  
   * Note that proximity is usually an estimate, and can quickly become stale.
   * 
   * @param num
   * @return List of NodeHandle
   */
  List networkNeighbors(int num);
  
  /**
   * To use Raw Serialization
   * @param buf
   * @return
   * @throws IOException 
   */
  public IdRange readIdRange(InputBuffer buf) throws IOException;
  
  public NodeHandleSet readNodeHandleSet(InputBuffer buf, short type) throws IOException;
  
  public void register();
}




