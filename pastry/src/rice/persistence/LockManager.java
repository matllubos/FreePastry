package rice.persistence;

import rice.Continuation;
import rice.p2p.commonapi.Id;

public interface LockManager {
  public void lock(Id id, Continuation c);
  
  public void unlock(Id id);
}
