/*
 * Created on May 12, 2004
 *
 * To change the template for this generated file go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
package rice.watchdog;

import java.io.Serializable;

/**
 * @author jeffh
 *
 * To change the template for this generated type comment go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
public interface PingMessageFactory {
  public Serializable getPingMessage();
	public void addressChanged();
}
