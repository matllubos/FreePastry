
package rice.pastry.wire.messaging.datagram;

import java.io.*;

import rice.pastry.*;

/**
 * Message which is a "transport" message in the datagram protocol. It simply
 * wraps an internal message for sending across the wire.
 *
 * @author Alan Mislove, Jeff Hoye
 */
public class DatagramTransportMessage extends DatagramMessage {

  // the wrapped object
  private Object o;

  /**
   * Builds a DatagramMessage given an object to wrap and a packet number
   *
   * @param o The object to wrap
   * @param num The "packet number"
   * @param source DESCRIBE THE PARAMETER
   * @param destination DESCRIBE THE PARAMETER
   */
  public DatagramTransportMessage(NodeId source, NodeId destination, int num, Object o) {
    super(source, destination, num);
    this.o = o;
  }

  /**
   * Returns the iternal wrapped object.
   *
   * @return The internal object
   */
  public Object getObject() {
    return o;
  }

  /**
   * DESCRIBE THE METHOD
   *
   * @return DESCRIBE THE RETURN VALUE
   */
  public String toString() {
    return "DatagramTransportMsg num " + num + " wrapping " + o;
  }
}
