package rice.p2p.glacier.v2.messaging;

import rice.*;
import rice.p2p.commonapi.*;
import rice.p2p.glacier.*;

/**
 * DESCRIBE THE CLASS
 *
 * @version $Id$
 * @author ahae
 */
public class GlacierQueryMessage extends GlacierMessage {
  protected FragmentKey keys[];

  public GlacierQueryMessage(int uid, FragmentKey keys[], NodeHandle source, Id dest, char tag) {
    super(uid, source, dest, false, tag);

    this.keys = keys;
  }

  public FragmentKey getKey(int index) {
    return keys[index];
  }

  public int numKeys() {
    return keys.length;
  }

  public String toString() {
    return "[GlacierQuery for " + keys[0] + " ("+(numKeys()-1)+" more keys)]";
  }
}

