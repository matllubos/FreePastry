package rice.ap3.messaging;

import java.util.Random;

/**
 * @(#) AP3MessageIDImpl.java
 *
 * Implements AP3MessageID providing a key to be used to uniquely
 * identify messages.
 *
 * @version $Id$
 * @author Gaurav Oberoi
 */
public class AP3MessageIDImpl 
  implements AP3MessageID {

  /**
   * Internally, the ID is represented by a 
   * Long.
   */
  private Long _idCode;

  /**
   * Used to randomly generate Longs.
   */
  private static Random _rand = new Random();

  /**
   * Constructor. Returns a randomly generated ID
   */
  AP3MessageIDImpl() {
    _idCode = new Long(AP3MessageIDImpl._rand.nextLong());
  }

  /**
   * Indicates whether some other object is equal
   * to this one.
   */
  public boolean equals(Object obj) {
    return ((obj instanceof AP3MessageIDImpl) &&
	    (((AP3MessageIDImpl) obj).getIDCode().equals(_idCode)));
  }

  /**
   * Returns the hashcode value of the object.
   */
  public int hashCode() {
    return _idCode.intValue();
  }

  /**
   * Returns the internal representation of the id.
   * Used for .equals().
   */
  protected Long getIDCode() {
    return _idCode;
  }    
}
