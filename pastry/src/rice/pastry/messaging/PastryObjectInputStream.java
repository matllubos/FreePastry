/*
 * Created on May 10, 2004
 *
 * To change the template for this generated file go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
package rice.pastry.messaging;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;

import rice.pastry.*;

/**
 * coalesces NodeHandles on the fly during java deserialization
 * 
 * @author Jeff Hoye
 */
public class PastryObjectInputStream extends ObjectInputStream {

  protected PastryNode node;

  /**
   * @param arg0
   * @throws java.io.IOException
   */
  public PastryObjectInputStream(InputStream stream, PastryNode node)
      throws IOException {
    super(stream);
    this.node = node;
    enableResolveObject(true);
  }

  protected Object resolveObject(Object input) throws IOException {
    if (input instanceof NodeHandle) {
//      System.out.println("POIS.resolveObject("+input+"@"+System.identityHashCode(input)+"):"+node);
      if (node != null) {
        // coalesce
        input = node.coalesce((NodeHandle) input);
      }
    }

    return input;
  }
}
