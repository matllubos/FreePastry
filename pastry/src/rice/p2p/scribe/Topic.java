
package rice.p2p.scribe;

import java.io.*;

import rice.*;
import rice.p2p.commonapi.*;
import rice.p2p.commonapi.rawserialization.*;

/**
 * @(#) Topic.java
 *
 * This class represents a specific topic in the Scribe system.
 *
 * @version $Id$
 * @author Alan Mislove
 */
public class Topic implements Serializable {

  /**
   * The Id to which this topic is mapped
   */
  protected Id id;

  /**
   * Constructor which takes an Id for this topic
   *
   * @param id The Id for this topic
   */
  public Topic(Id id) {
    this.id = id;
  }

  /**
   * Constructor which takes a name for this topic
   *
   * @param factory The factory to use when creating the id
   * @param name The name for this topic
   */
  public Topic(IdFactory factory, String name) {
    this.id = getId(factory, name);
  }

  /**
   * Returns the Id to which this topic is mapped
   *
   * @return The id to which this topic is mapped
   */
  public Id getId() {
    return id;
  }

  /**
   * Returns the Id to which the string is mapped
   *
   * @param factory The factory to use when creating the id
   * @param name The string to map
   * @return The id to which this string is mapped
   */
  public static Id getId(IdFactory factory, String name) {
    return factory.buildId(name);
  }

  /**
   * Returns whether this is equal to o or not
   *
   * @param o The object to compare to
   * @return Whether or not they are equal
   */
  public boolean equals(Object o) {
    if (o instanceof Topic) {
      return ((Topic) o).id.equals(id);
    }

    return false;
  }

  /**
   * Returns the hashCode for this topic
   *
   * @return The hashcode for this topic
   */
  public int hashCode() {
    return id.hashCode();
  }

  /**
   * Returns a String representation of this topic
   *
   * @return A String representation of this topic
   */
  public String toString() {
    return "[TOPIC " + id + "]";
  }  

  public Topic(InputBuffer buf, Endpoint endpoint) throws IOException {
    id = endpoint.readId(buf, buf.readShort());
  }
  
  public void serialize(OutputBuffer buf) throws IOException {
    buf.writeShort(id.getType());
    id.serialize(buf); 
  }
}

