
package rice.pastry.wire.messaging.datagram;

import java.io.*;
import java.net.*;

import rice.pastry.*;

/**
 * Class which wraps all messages to be sent through the UDP-based pastry
 * protocol. Adds on a "packet number" which should be repeated in the
 * AcknowledgementMessage ack packet.
 *
 * @author Alan Mislove, Jeff Hoye
 */
public abstract class DatagramMessage implements Serializable {

  // the "packet number"
  /**
   * DESCRIBE THE FIELD
   */
  protected int num;

  // the source of this message
  /**
   * DESCRIBE THE FIELD
   */
  protected NodeId source;

  // the destination of this message
  /**
   * DESCRIBE THE FIELD
   */
  protected NodeId destination;

  /**
   * Builds a DatagramMessage given a packet number
   *
   * @param num The "packet number"
   * @param source DESCRIBE THE PARAMETER
   * @param destination DESCRIBE THE PARAMETER
   */
  public DatagramMessage(NodeId source, NodeId destination, int num) {
    this.source = source;
    this.destination = destination;
    this.num = num;
  }

  /**
   * Returns the "packet number" of this transmission
   *
   * @return The packet number of this message.
   */
  public int getNum() {
    return num;
  }

  /**
   * Returns the NodeId from which this message came.
   *
   * @return This message's source
   */
  public NodeId getSource() {
    return source;
  }

  /**
   * Returns the NodeId which is the destination
   *
   * @return This message's destination
   */
  public NodeId getDestination() {
    return destination;
  }

  /**
   * Returns the approriate 'ack' message for this datagram transport message.
   *
   * @param address The address the ack will be sent to
   * @return The Ack value
   */
  public AcknowledgementMessage getAck(InetSocketAddress address) {
    return new AcknowledgementMessage(destination, source, num, address);
  }

  /**
   * Sets the "packet number" of this transmission
   *
   * @param num The packet number
   */
  public void setNum(int num) {
    this.num = num;
  }

  /**
   * DESCRIBE THE METHOD
   *
   * @return DESCRIBE THE RETURN VALUE
   */
  public String toString() {
    return "DatagramMsg from " + source + " to " + destination + " num " + num;
  }
}
