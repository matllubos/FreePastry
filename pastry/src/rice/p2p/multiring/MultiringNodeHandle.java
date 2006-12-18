
package rice.p2p.multiring;

import rice.p2p.commonapi.*;
import rice.p2p.commonapi.rawserialization.*;

import java.io.*;
import java.util.*;

/**
 * @(#) MultiringNodeHandle.java
 *
 * @author Alan Mislove
 */
public class MultiringNodeHandle extends NodeHandle implements Observer  {
  
  // serailver
  static final long serialVersionUID = -2972303779251779984L;

  /**
   * The internal handle
   */
  protected NodeHandle handle;
  
  /**
   * The handle's ringId
   */
  protected Id ringId;
  
  /**
    * Constructor
   *
   * @param handle The handle to wrap
   * @param ringId The handle's ringId
   */
  public MultiringNodeHandle(Id ringId, NodeHandle handle) {
    this.handle = handle;
    this.ringId = ringId;
    
    handle.addObserver(this);
  }
  
  /**
   * Returns the internal handle
   *
   * @return The internal handle
   */
  protected NodeHandle getHandle() {
    return handle;
  }
  
  /**
   * Returns the ringId of this node handle
   *
   * @return This node's ringId
   */
  public Id getRingId() {
    return ringId;
  }
  
  /**
   * Returns this node's id.
   *
   * @return The corresponding node's id.
   */
  public Id getId() {
    return RingId.build(ringId, handle.getId());
  }
  
  /**
   * Returns whether or not this node is currently alive
   *
   * @return Whether or not this node is currently alive
   */
  public boolean isAlive() {
    return handle.isAlive();
  }
  
  /**
   * Returns the current proximity value of this node
   * @deprecated use Node.proximity(NodeHandle)
   * @return The current proximity value of this node
   */
  @SuppressWarnings("deprecation")
  public int proximity() {
    return handle.proximity();
  }
  
  /**
   * Observable callback.  Simply rebroadcasts
   *
   * @param o the updated object
   * @param obj The paramter
   */
  public void update(Observable o, Object obj) {
    setChanged();
    notifyObservers(obj);
  }
  
  /**
   * Prints out the string
   *
   * @return A string
   */
  public String toString() {
    return "{MNH " + handle.toString() +"@"+ringId+"}";
  }
  
  /**
   * Returns whether or not this object is equal to the provided one
   *
   * @param o The object to compare to
   * @return Whether or not this is object is equal
   */
  public boolean equals(Object o) {
    if (! (o instanceof MultiringNodeHandle))
      return false;
    
    return (((MultiringNodeHandle) o).handle.equals(handle) && ((MultiringNodeHandle) o).ringId.equals(ringId));
  }
  
  /**
   * Returns the hashCode
   *
   * @return hashCode
   */
  public int hashCode() {
    return (handle.hashCode() + ringId.hashCode());
  }
  
  /**
   * ReadObject overridden in order to maintain observer.
   *
   * @param ois The InputStream
   */
  private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
    ois.defaultReadObject();
    
    handle.addObserver(this);
  }

  /**
   * Requests that the underlying transport layer check to ensure
   * that the remote node is live.  If the node is found to be live, nothing
   * happens, but if the node does not respond, the transport layer
   * make take steps to verfify that the node is dead.  Such steps
   * could include finding an alteranate route to the node.
   *
   * @return Whether or not the node is currently alive
   */
  public boolean checkLiveness() {
    return handle.checkLiveness();
  }

  public MultiringNodeHandle(InputBuffer buf, Endpoint endpoint) throws IOException {
    ringId = endpoint.readId(buf, buf.readShort());
    handle = endpoint.readNodeHandle(buf);
    handle.addObserver(this);
  }
  
  public void serialize(OutputBuffer buf) throws IOException {
    buf.writeShort(ringId.getType());
    ringId.serialize(buf);
    handle.serialize(buf);
  }
}


