package rice.p2p.glacier.v2;

import java.io.Serializable;

public class FragmentMetadata implements Serializable {
  protected long currentExpirationDate;
  protected long previousExpirationDate;
  protected long storedSince;
  
  public FragmentMetadata(long currentExpirationDate, long previousExpirationDate, long storedSince) {
    this.currentExpirationDate = currentExpirationDate;
    this.previousExpirationDate = previousExpirationDate;
    this.storedSince = storedSince;
  }
  
  long getCurrentExpiration() {
    return currentExpirationDate;
  }
  
  long getPreviousExpiration() {
    return previousExpirationDate;
  }
  
  long getStoredSince() {
    return storedSince;
  }
}
