/*
 * Created on Jun 30, 2006
 */
package rice.p2p.util.rawserialization;

import java.io.IOException;

/**
 * Wrapper exception to hold the name of the message that caused the serialization problem.
 * 
 * @author Jeff Hoye
 */
public class JavaSerializationException extends IOException {
  
  Object cantSerialize;

  public JavaSerializationException(Object o, Exception e) {
    super("Error serializing "+o);
    initCause(e);
    this.cantSerialize = o;
  }
  
  /**
   * @return the object that caused the serialization problem
   */
  public Object getUnserializable() {
    return cantSerialize;
  }
}

