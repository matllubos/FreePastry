
package rice.serialization;

import java.lang.ref.*;
import java.util.*;

/**
 * Class which implements a Soft-Reference based HashMap, allowing the garbage
 * collector to collection stuff if memory pressure is tight.  Should be transparent
 * to applications, except that items may disappear.
 *
 * @author Alan Mislove
 */
public class SoftHashMap extends HashMap {
  
  /**
   * Returns whether or not the key is contained in this map.  Only returns true if
   * the softreference has not been GC'ed.
   *
   * @param key The key to check for
   * @return The result
   */
  public boolean containsKey(Object key) {
    if (! super.containsKey(key))
      return false;
    
    if (super.get(key) == null) {
      return true;
    } else {
      return (((SoftReference) super.get(key)).get() != null);
    }
  }
  
  /**
   * Returns whether or not the value is contained in this map.  Only returns true if
   * the softreference has not been GC'ed.
   *
   * @param value The value to check for
   * @return The result
   */
  public boolean containsValue(Object value) {
    if (value == null) {
      return super.containsValue(null);
    } else {
      return super.containsValue(new SoftReference(value));
    }
  }
  
  /**
   * Returns the object associated with the key.  May return null, if the soft reference
   * has been GC'ed.
   * 
   * @param key The key
   * @return The value
   */
  public Object get(Object key) {
    SoftReference value = (SoftReference) super.get(key);
    
    if (value == null) {
      return null;
    } else {
      Object result = value.get();
      
      if (result != null) {
        return result;
      } else {
        remove(key);
        return null;
      }
    }
  }
  
  /**
   * Adds an entry to the soft hash map.  May not persist for very long, though.
   *
   * @param key The key
   * @param value The value
   * @return The previous value of the key
   */
  public Object put(Object key, Object value) {
    Object result = get(key);
    
    if (value != null) {
      super.put(key, new SoftReference(value)); 
    } else {
      super.put(key, null);
    }
    
    return result;
  }
}
