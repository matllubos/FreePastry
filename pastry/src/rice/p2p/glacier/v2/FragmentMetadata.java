package rice.p2p.glacier.v2;

import java.io.Serializable;

public class FragmentMetadata implements Serializable {
  protected long currentExpirationDate;
  protected long previousExpirationDate;
  
  public FragmentMetadata(long currentExpirationDate, long previousExpirationDate) {
    this.currentExpirationDate = currentExpirationDate;
    this.previousExpirationDate = previousExpirationDate;
  }
  
  long getCurrentExpiration() {
    return currentExpirationDate;
  }
  
  long getPreviousExpiration() {
    return previousExpirationDate;
  }
}
