
package rice.pastry.wire;

import java.net.*;

import rice.pastry.*;

/**
 * Wrapper class which contains an object to write and the address it needs to
 * be written to.
 *
 * @author Alan Mislove
 */
public class PendingWrite {

  // the destination nodeId
  private NodeId destination;

  // the destination address
  private InetSocketAddress address;

  // the object to write
  private Object o;

  /**
   * Contructs a PendingWrite from an address and an object
   *
   * @param address The destination address of this object
   * @param o The object to be written.
   * @param destination DESCRIBE THE PARAMETER
   */
  public PendingWrite(NodeId destination, InetSocketAddress address, Object o) {
    this.destination = destination;
    this.address = address;
    this.o = o;
  }

  /**
   * Returns the destination address of this write
   *
   * @return The destination address of this pending write.
   */
  public NodeId getDestination() {
    return destination;
  }

  /**
   * Returns the destination address of this write
   *
   * @return The destination address of this pending write.
   */
  public InetSocketAddress getAddress() {
    return address;
  }

  /**
   * Returns the object to be written
   *
   * @return The object to be written
   */
  public Object getObject() {
    return o;
  }
}
