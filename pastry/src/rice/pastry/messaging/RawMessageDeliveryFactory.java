/*
 * Created on Feb 24, 2006
 */
package rice.pastry.messaging;

import rice.p2p.commonapi.rawserialization.InputBuffer;

public interface RawMessageDeliveryFactory {
  public RawMessageDelivery build(InputBuffer buf);
}
