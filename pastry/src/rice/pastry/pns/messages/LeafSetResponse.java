package rice.pastry.pns.messages;

import rice.pastry.leafset.LeafSet;
import rice.pastry.messaging.Message;

public class LeafSetResponse extends Message {
  public LeafSet leafset;
  
  public LeafSetResponse(LeafSet leafset, int dest) {
    super(dest);
    this.leafset = leafset;
  }
}
