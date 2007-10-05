package rice.p2p;

import java.util.ArrayList;
import java.util.Collection;

import rice.p2p.commonapi.Cancellable;

/**
 * Allows you to cancel a group of things.
 * 
 * If you attach to a cancelled item, it gets cancelled now.
 * 
 * @author Jeff Hoye
 */
public class AttachableCancellable implements Cancellable {
  
  /**
   * If subCancellable = null, it's been cancelled.
   */
  Collection<Cancellable> subCancellable = new ArrayList<Cancellable>();
  
  /**
   * Returns false if any are false;
   */
  public boolean cancel() {
    Collection<Cancellable> delme; 
    synchronized(this) {
      if (subCancellable == null) return true;
      delme = subCancellable;
      subCancellable = null;
    }
    boolean ret = true;
    for (Cancellable c : delme) {
      if (!c.cancel()) ret = false;
    }
    return ret;
  }

  public void attach(Cancellable c) {
    if (c == null) return;
    boolean cancel = false;
    synchronized(this) {
      if (subCancellable == null) cancel = true;
      subCancellable.add(c);      
    } 
    if (cancel) {
      c.cancel();
    }
  }
  
  public void detach(Cancellable c) {
    if (c == null) return;
    synchronized(this) {
      if (subCancellable == null) return;
      subCancellable.remove(c);
    }
  }
} 
