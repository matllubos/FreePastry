package rice.past.messaging;

import java.util.Random;
import java.io.Serializable;

/**
 * @(#) PASTMessageIDImpl.java
 *
 * Implements PASTMessageID providing a key to be used to uniquely
 * identify messages.
 *
 * @version $Id$
 * @author Charles Reis
 */
public class PASTMessageIDImpl 
  implements PASTMessageID, Serializable {
  
  /**
   * Internally, the ID is represented by a 
   * Long.
   */
  private Long _idCode;
  
  /**
   * Used to randomly generate Longs.
   */
  private static final Random _rand = new Random();
  
  /**
   * Constructor. Returns a randomly generated ID
   */
  PASTMessageIDImpl() {
    _idCode = new Long(_rand.nextLong());
  }
  
  /**
   * Indicates whether some other object is equal
   * to this one.
   */
  public boolean equals(Object obj) {
    return ((obj instanceof PASTMessageIDImpl) &&
            (((PASTMessageIDImpl) obj).getIDCode().equals(_idCode)));
  }
  
  /**
   * Returns the hashcode value of the object.
   */
  public int hashCode() {
    return _idCode.intValue();
  }
  
  /**
   * Returns the string representation of the node.
   */
  public String toString() {
    return _idCode.toString();
  }
  
  /**
   * Returns the internal representation of the id.
   * Used for .equals().
   */
  protected Long getIDCode() {
    return _idCode;
  }
}
