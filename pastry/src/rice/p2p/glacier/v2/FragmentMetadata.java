package rice.p2p.glacier.v2;

import java.io.Serializable;

public class FragmentMetadata implements Serializable, Comparable {
  
  private static final long serialVersionUID = 3380538644355999384L;
  
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
  
  public int compareTo(Object object) {
    FragmentMetadata metadata = (FragmentMetadata) object;
    
    if (metadata.currentExpirationDate > currentExpirationDate) 
      return -1;
    else if (metadata.currentExpirationDate < currentExpirationDate) 
      return 1;
    else if (metadata.previousExpirationDate < previousExpirationDate)
      return -1;
    else if (metadata.previousExpirationDate > previousExpirationDate)
      return 1;
    else if (metadata.storedSince < storedSince)
      return -1;
    else if (metadata.storedSince > storedSince)
      return 1;
    else
      return 0;
  }
}
