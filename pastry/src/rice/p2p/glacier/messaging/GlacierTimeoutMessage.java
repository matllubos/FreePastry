package rice.p2p.glacier.messaging;

import rice.*;
import rice.p2p.commonapi.*;
import rice.p2p.glacier.*;

public class GlacierTimeoutMessage extends GlacierMessage {

  /**
   * Constructor which takes a unique integer Id and the local id
   *
   * @param uid The unique id
   * @param local The local nodehandle
   */
  public GlacierTimeoutMessage(int uid, NodeHandle local) {
    super(uid, local, local.getId());
  }

  /**
  * Returns a string representation of this message
   *
   * @return A string representing this message
   */
  public String toString() {
    return "[GlacierTimeoutMessage "+getUID()+"]";
  }
}

