package rice.ap3.messaging;

/**
 * @(#) AP3MessageID.java
 *
 * Defines an AP3MessageID to be used to uniquely
 * identify messages.
 *
 * @version $Id$
 * @author Gaurav Oberoi
 */
public interface AP3MessageID {

  /**
   * Indicates whether some other object is equal
   * to this one.
   */
  boolean equals(Object obj);

  /**
   * Returns the hashcode value of the object.
   */
  int hashCode();

  /**
   * Returns the string representation of the node.
   */
  public String toString();
}
