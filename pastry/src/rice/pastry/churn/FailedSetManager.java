/*
 * Created on Aug 1, 2004
 *
 * To change the template for this generated file go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
package rice.pastry.churn;

import java.util.Collection;

/**
 * @author Jeff Hoye
 */
public interface FailedSetManager {
  /**
   * @return Collection of NodeHandle
   */
  public Collection getFailedSet();
}
