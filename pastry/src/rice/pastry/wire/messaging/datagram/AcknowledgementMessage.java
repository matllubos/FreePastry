
package rice.pastry.wire.messaging.datagram;

import java.io.*;
import java.net.*;

import rice.pastry.*;

/**
 * Class which represents an "ack" packet in the UDP version of the pastry wire
 * protocol. Each DatagramMessage is assigned a integer number (increasing), and
 * each "ack" packet sent back contains that number.
 *
 * @author Alan Mislove, Jeff Hoye
 */
public class AcknowledgementMessage extends DatagramMessage {

  // the address the ack in going to be sent to
  private InetSocketAddress address;

  /**
   * Constructor.
   *
   * @param address The destination of the "ack" packet
   * @param num The number of the original DatagramMessage.
   * @param source DESCRIBE THE PARAMETER
   * @param destination DESCRIBE THE PARAMETER
   */
  public AcknowledgementMessage(NodeId source, NodeId destination, int num, InetSocketAddress address) {
    super(source, destination, num);
    this.address = address;
  }

  /**
   * Returns the address of the destination of this ack message.
   *
   * @return The destination address of the ack message.
   */
  public InetSocketAddress getAddress() {
    return address;
  }

  /**
   * DESCRIBE THE METHOD
   *
   * @return DESCRIBE THE RETURN VALUE
   */
  public String toString() {
    return "AckMsg to " + getDestination() + " from " + getSource() + " num " + num;
  }
}
