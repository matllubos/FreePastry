
package rice.p2p.replication.messaging;

import rice.p2p.commonapi.*;
import rice.p2p.replication.*;
import rice.p2p.util.*;

/**
 * @(#) RequestMessage.java
 *
 * This class represents a request for a set of keys in the replication
 * system.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */
public class RequestMessage extends ReplicationMessage {
  
  // the list of ranges for this message
  protected IdRange[] ranges;
  
  // the list of hashes for this message
  protected IdBloomFilter[] filters;
  
  /**
   * Constructor which takes a unique integer Id
   *
   * @param source The source address
   * @param topic The topic
   */
  public RequestMessage(NodeHandle source, IdRange[] ranges, IdBloomFilter[] filters) {
    super(source);
    
    this.ranges = ranges;
    this.filters = filters;
  }
  
  /**
   * Method which returns this messages' ranges
   *
   * @return The ranges of this message
   */
  public IdRange[] getRanges() {
    return ranges;
  }
  
  /**
   * Method which returns this messages' bloom filters
   *
   * @return The bloom filters of this message
   */
  public IdBloomFilter[] getFilters() {
    return filters;
  }
}

