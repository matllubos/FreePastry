/*
 * Created on Mar 26, 2004
 *
 * To change the template for this generated file go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
package rice.pastry.socket;

import rice.pastry.socket.messaging.Probe;

/**
 * @author Jeff Hoye
 *
 */
public interface ProbeListener {
  void probeReceived(Probe p);
}