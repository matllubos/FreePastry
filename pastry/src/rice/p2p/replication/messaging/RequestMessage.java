
package rice.p2p.replication.messaging;

import java.io.IOException;

import rice.p2p.commonapi.*;
import rice.p2p.commonapi.rawserialization.*;
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
  public static final short TYPE = 2;

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
  

  /***************** Raw Serialization ***************************************/
  public short getType() {
    return TYPE; 
  }
  
  public void serialize(OutputBuffer buf) throws IOException {
    buf.writeByte((byte)0); // version
    super.serialize(buf);

    buf.writeInt(filters.length);
    for (int i = 0; i < filters.length; i++) {
      filters[i].serialize(buf); 
    }

    buf.writeInt(ranges.length);
    for (int i = 0; i < ranges.length; i++) {
      ranges[i].serialize(buf); 
    }
  }
  
  public static RequestMessage build(InputBuffer buf, Endpoint endpoint) throws IOException {
    byte version = buf.readByte();
    switch(version) {
      case 0:
        return new RequestMessage(buf, endpoint);
      default:
        throw new IOException("Unknown Version: "+version);
    }
  }
    
  private RequestMessage(InputBuffer buf, Endpoint endpoint) throws IOException {
    super(buf, endpoint);
    
    filters = new IdBloomFilter[buf.readInt()];
    for (int i = 0; i < filters.length; i++) {
      filters[i] = new IdBloomFilter(buf);
    }
    
    ranges = new IdRange[buf.readInt()];
    for (int i = 0; i < ranges.length; i++) {
      ranges[i] = endpoint.readIdRange(buf);
    }    
  }
}

 