package rice.pastry.transport;

import rice.p2p.commonapi.MessageReceipt;

public interface PMessageNotification {

  public void sendFailed(PMessageReceipt msg, Exception reason);

  public void sent(PMessageReceipt msg);
}
