package rice.p2p.glacier.v2;

import java.io.Serializable;

public class FragmentMetadata implements Serializable {
  long currentExpirationDate;
  long lastExpirationDate;
  
  public FragmentMetadata(long currentExpirationDate, long lastExpirationDate) {
    this.currentExpirationDate = currentExpirationDate;
    this.lastExpirationDate = lastExpirationDate;
  }
  
  long getCurrentExpirationDate() {
    return currentExpirationDate;
  }
  
  long getLastExpirationDate() {
    return lastExpirationDate;
  }
}
