package rice.past.messaging;

/**
 * @(#) PASTMessageID.java
 *
 * Defines a PASTMessageID to be used to uniquely
 * identify messages.
 *
 * @version $Id$
 * @author Charles Reis
 */
public interface PASTMessageID {
  
  /**
   * Indicates whether some other object is equal
   * to this one.
   */
  public boolean equals(Object obj);
  
  /**
   * Returns the hashcode value of the object.
   */
  public int hashCode();
  
  /**
   * Returns the string representation of the node.
   */
  public String toString();
}
