
package rice.p2p.past.gc;

import java.io.*;

import rice.p2p.past.*;

/**
 * @(#) GCPastMetadata.java
 * 
 * Class which is used as the metadata storage for the GC past implementation.
 * Basically wraps the long timestamp.
 *
 * @version $Id$
 * @author Peter Druschel 
 */
public class GCPastMetadata implements Serializable, Comparable {
  
  // serialver for backwards compatibility
  private static final long serialVersionUID = -2432306227012003387L;

  // the expiration time
  protected long expiration;
  
  /**
   * Constructor.
   *
   * @param msg The string representing the error.
   */
  public GCPastMetadata(long expiration) {
    this.expiration = expiration;
  }
  
  /**
   * Method which returns the expiration time
   *
   * @return The contained expiration time
   */
  public long getExpiration() {
    return expiration;
  }
  
  /**
   * Method which sets the expiration time
   *
   * @param The new expiration time
   */
  public GCPastMetadata setExpiration(long expiration) {
    return new GCPastMetadata(expiration);
  }
  
  public boolean equals(Object o) {
    return ((GCPastMetadata) o).expiration == expiration;
  }
  
  public int hashCode() {
    return (int) expiration;
  }
  
  /**
   * Comparable, returns -1 if less, 0 if equal, and 1 if greater
   *
   * @param other The object ot compare to
   * @return the comparison
   */
  public int compareTo(Object other) {
    GCPastMetadata metadata = (GCPastMetadata) other;
    
    if (metadata.expiration > expiration) 
      return -1;
    else if (metadata.expiration < expiration) 
      return 1;
    else
      return 0;
  }
  
  public String toString() {
    return "GCPMetadata " + expiration;
  }
  
  private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
    ois.defaultReadObject();
    
    if (expiration == 1096560000000L)
      expiration = GCPastImpl.DEFAULT_EXPIRATION;
  }
}





