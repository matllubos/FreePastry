/*
 * Created on May 10, 2004
 *
 * To change the template for this generated file go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
package rice.pastry;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;

/**
 * @author jeffh
 *
 * To change the template for this generated type comment go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
public class PastryObjectInputStream extends ObjectInputStream {

  protected PastryNode node;

	/**
	 * @param arg0 
	 * @throws java.io.IOException
	 */
	public PastryObjectInputStream(InputStream stream, PastryNode node) throws IOException {
		super(stream);
    this.node = node;
    enableResolveObject(true);
	}  
  
	protected Object resolveObject(Object input) throws IOException {
    if (input instanceof LocalNodeI) {
      if (node != null)
        input = node.getLocalNodeI((LocalNodeI)input);
      ((LocalNodeI) input).setLocalNode(node);
    }
    
    return input;
	}

}
