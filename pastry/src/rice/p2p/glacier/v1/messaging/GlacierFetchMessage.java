package rice.p2p.glacier.v1.messaging;

import rice.*;
import rice.p2p.commonapi.*;
import rice.p2p.glacier.*;

/**
 * DESCRIBE THE CLASS
 *
 * @version $Id$
 * @author ahae
 */
public class GlacierFetchMessage extends GlacierMessage {
  /**
   * DESCRIBE THE FIELD
   */
  protected FragmentKey key;

  /**
   * Constructor for GlacierFetchMessage.
   *
   * @param uid DESCRIBE THE PARAMETER
   * @param key DESCRIBE THE PARAMETER
   * @param source DESCRIBE THE PARAMETER
   * @param dest DESCRIBE THE PARAMETER
   */
  public GlacierFetchMessage(int uid, FragmentKey key, NodeHandle source, Id dest) {
    super(uid, source, dest);

    this.key = key;
  }
  
  public int getPriority() {
    return LOW_PRIORITY;
  }

  /**
   * Gets the Key attribute of the GlacierFetchMessage object
   *
   * @return The Key value
   */
  public FragmentKey getKey() {
    return key;
  }

  /**
   * DESCRIBE THE METHOD
   *
   * @return DESCRIBE THE RETURN VALUE
   */
  public String toString() {
    return "[GlacierFetch for " + key + "]";
  }
}
