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
public class GlacierDataMessage extends GlacierMessage {
  /**
   * DESCRIBE THE FIELD
   */
  protected FragmentKey key;
  Fragment fragment;

  /**
   * Constructor for GlacierDataMessage.
   *
   * @param uid DESCRIBE THE PARAMETER
   * @param key DESCRIBE THE PARAMETER
   * @param fragment DESCRIBE THE PARAMETER
   * @param source DESCRIBE THE PARAMETER
   * @param dest DESCRIBE THE PARAMETER
   */
  public GlacierDataMessage(int uid, FragmentKey key, Fragment fragment, NodeHandle source, Id dest) {
    super(uid, source, dest);

    this.key = key;
    this.fragment = fragment;
  }

  /**
   * Gets the Key attribute of the GlacierDataMessage object
   *
   * @return The Key value
   */
  public FragmentKey getKey() {
    return key;
  }

  /**
   * Gets the Fragment attribute of the GlacierDataMessage object
   *
   * @return The Fragment value
   */
  public Fragment getFragment() {
    return fragment;
  }

  /**
   * DESCRIBE THE METHOD
   *
   * @return DESCRIBE THE RETURN VALUE
   */
  public String toString() {
    return "[GlacierData for " + key + "]";
  }
}

