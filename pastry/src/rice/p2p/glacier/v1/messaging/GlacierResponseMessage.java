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
public class GlacierResponseMessage extends GlacierMessage {
  /**
   * DESCRIBE THE FIELD
   */
  protected FragmentKey key;
  boolean haveIt;

  /**
   * Constructor for GlacierResponseMessage.
   *
   * @param uid DESCRIBE THE PARAMETER
   * @param key DESCRIBE THE PARAMETER
   * @param haveIt DESCRIBE THE PARAMETER
   * @param source DESCRIBE THE PARAMETER
   * @param dest DESCRIBE THE PARAMETER
   */
  public GlacierResponseMessage(int uid, FragmentKey key, boolean haveIt, NodeHandle source, Id dest) {
    super(uid, source, dest);

    this.key = key;
    this.haveIt = haveIt;
  }
  
  public int getPriority() {
    return LOW_PRIORITY;
  }

  /**
   * Gets the Key attribute of the GlacierResponseMessage object
   *
   * @return The Key value
   */
  public FragmentKey getKey() {
    return key;
  }

  /**
   * Gets the HaveIt attribute of the GlacierResponseMessage object
   *
   * @return The HaveIt value
   */
  public boolean getHaveIt() {
    return haveIt;
  }

  /**
   * DESCRIBE THE METHOD
   *
   * @return DESCRIBE THE RETURN VALUE
   */
  public String toString() {
    return "[GlacierResponse for " + key + " - " + (haveIt ? "has it" : "does not have it") + "]";
  }
}

