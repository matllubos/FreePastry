/*
 * Created on Mar 26, 2004
 *
 * To change the template for this generated file go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
package rice.pastry.churn;

import java.util.Collection;

import rice.pastry.NodeHandle;

/**
 * @author jeffh
 *
 * To change the template for this generated type comment go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
public interface Probe {
  public static final int STATE_NONE = 0;
  public static final int STATE_JOINED = 1;
  public static final int STATE_READY = 2;
  
  LivenessLeafSet getLeafset();
  NodeHandle getSender();
  Collection getFailedSet();
  
  int getState();

	/**
	 * @return true if this is a reply
	 */
	boolean isResponse();
  
  /**
   * @return true for request, false for response
   * can be false on a request if a response is not necessary
   */
  boolean requestResponse();
}