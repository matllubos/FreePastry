package rice.post;

import java.io.IOException;

import rice.environment.Environment;
import rice.p2p.commonapi.*;
import rice.p2p.commonapi.rawserialization.*;

/**
 * This class represents the abstract notion of the address
 * of an user in the Post system.
 * 
 * @version $Id$
 */
public class PostUserAddress extends PostEntityAddress {

  public static final short TYPE = 2;
  
  // serialver for backward compatibility
  static final long serialVersionUID = -5468003419549068547L;
  
  // the name of this user
  private String name;
  
  // the address of this user
  private Id address;
  
  /**
   * Constructor
   */
  public PostUserAddress(IdFactory factory, String name, Environment env) {
    this.name = name;
    address = getId(factory, name, env);
  }
  
  /**
   * Returns the address associated with this useraddress
   *
   * @return The corresponding address
   */
  public Id getAddress() {
    return address;
  }

  /**
   * Returns the name of this user
   *
   * @return The corresponding name
   */
  public String getName() {
    return name;
  }

  public String toString() {
    return name;
  }

  public boolean equals(Object o) {
    if (o instanceof PostUserAddress) {
      PostUserAddress ua = (PostUserAddress) o;
      return ua.getName().equals(name);
    }

    return false;
  }

  public int hashCode() {
    return name.hashCode();
  }
  
  public PostUserAddress(InputBuffer buf, Endpoint endpoint) throws IOException {
    address = endpoint.readId(buf, buf.readShort()); 
    name = buf.readUTF();
  }
  
  /**
   * Note that the TYPE is read in PostEntityAddress.build()
   */
  public void serialize(OutputBuffer buf) throws IOException {
    
    buf.writeShort(address.getType());
    address.serialize(buf); 
    
    buf.writeUTF(name);
  }

  public short getType() {
    return TYPE;
  }  
}
