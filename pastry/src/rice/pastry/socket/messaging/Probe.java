/*
 * Created on Mar 26, 2004
 *
 * To change the template for this generated file go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
package rice.pastry.socket.messaging;

import java.util.Collection;

import rice.pastry.socket.SocketNodeHandle;

/**
 * @author jeffh
 *
 * To change the template for this generated type comment go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
public interface Probe {
  Collection getLeafset();
  Collection getFaildset();
  SocketNodeHandle getRemoteNode();
}
