/*
 * Created on May 10, 2004
 *
 * To change the template for this generated file go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
package rice.p2p.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;

import rice.p2p.commonapi.*;

/**
 * coalesces NodeHandles on the fly during java deserialization
 * 
 * @author Jeff Hoye
 */
public class JavaDeserializer extends ObjectInputStream {

  protected Endpoint endpoint;

  /**
   * @param arg0
   * @throws java.io.IOException
   */
  public JavaDeserializer(InputStream stream, Endpoint endpoint)
      throws IOException {
    super(stream);
    this.endpoint = endpoint;
    enableResolveObject(true);
  }

  protected Object resolveObject(Object input) throws IOException {
    if (input instanceof NodeHandle) {
//      System.out.println("POIS.resolveObject("+input+"@"+System.identityHashCode(input)+"):"+node);
      if (endpoint != null) {
        // coalesce
        input = endpoint.coalesce((NodeHandle) input);
      }
    }

    return input;
  }
}
