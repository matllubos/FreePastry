
package rice.pastry.wire.messaging.datagram;

import java.io.*;
import rice.pastry.*;

import rice.pastry.wire.*;

/**
 * Class which represents a "ping" message sent through the udp pastry system.
 *
 * @author Alan Mislove
 */
public class PingMessage extends DatagramMessage {

  private transient WireNodeHandle handle;

  /**
   * Constructor
   *
   * @param source DESCRIBE THE PARAMETER
   * @param destination DESCRIBE THE PARAMETER
   * @param num DESCRIBE THE PARAMETER
   * @param handle DESCRIBE THE PARAMETER
   */
  public PingMessage(NodeId source, NodeId destination, int num, WireNodeHandle handle) {
    super(source, destination, num);
    this.handle = handle;
  }

  /**
   * DESCRIBE THE METHOD
   *
   * @return DESCRIBE THE RETURN VALUE
   */
  public String toString() {
    return "PingMessage from " + getSource() + " to " + getDestination();
  }

  /**
   * DESCRIBE THE METHOD
   *
   * @param oos DESCRIBE THE PARAMETER
   * @exception IOException DESCRIBE THE EXCEPTION
   */
  private void writeObject(ObjectOutputStream oos)
     throws IOException {

    handle.pingStarted();
    oos.defaultWriteObject();
  }
}
