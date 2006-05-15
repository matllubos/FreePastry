package rice.post;

import java.io.IOException;

import rice.environment.Environment;
import rice.p2p.commonapi.*;
import rice.p2p.commonapi.rawserialization.*;
import rice.p2p.past.*;

/**
 * This class represents the abstract notion of the address
 * of an group of users in the Post system.
 * 
 * @version $Id$
 */
public class PostGroupAddress extends PostEntityAddress {

  public static final short TYPE = 1;
  
  private Id id;
  
  /**
   * Constructor
   */
  public PostGroupAddress(IdFactory factory, String name, Environment env) {
    id = getId(factory, name, env);
  }

  /**
    * Returns the address associated with this useraddress
   *
   * @return The corresponding address
   */
  public Id getAddress() {
    return id;
  }

  public boolean equals(Object o) {
    if (o instanceof PostGroupAddress) {
      PostGroupAddress ua = (PostGroupAddress) o;
      return ua.getAddress().equals(id);
    }

    return false;
  }

  public int hashCode() {
    return id.hashCode();
  }
  
  public PostGroupAddress(InputBuffer buf, Endpoint endpoint) throws IOException {
    id = endpoint.readId(buf, buf.readShort()); 
  }
  
  /**
   * Note that the TYPE is read in PostEntityAddress.build()
   */
  public void serialize(OutputBuffer buf) throws IOException {
    buf.writeShort(id.getType());
    id.serialize(buf); 
  }

  public short getType() {
    return TYPE;
  }
}
