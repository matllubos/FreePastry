package rice.persistence;

import java.util.*;

import rice.Continuation;
import rice.environment.Environment;
import rice.environment.logging.Logger;
import rice.p2p.commonapi.Id;

public class LockManagerImpl implements LockManager {

  // map: Id -> List<Continuation>
  private HashMap locks;
  
  protected Logger logger;

  public LockManagerImpl(Environment env) {
    this.locks = new HashMap();
    this.logger =  env.getLogManager().getLogger(StorageManagerImpl.class,null);
  }

  
  public void lock(Id id, Continuation c) {
    Continuation torun = null;
    if (logger.level <= Logger.FINE) logger.log("locking on id "+id+" for continuation "+c);
    synchronized (this) {
      if (locks.containsKey(id)) {
        List locklist = ((List)locks.get(id));
        if (logger.level <= Logger.FINER) logger.log("locking on id "+id+"; blocked on "+locklist.size()+" earlier continuations");
        if (locklist.size() > 10 && logger.level <= Logger.INFO) logger.log("locking on id "+id+"; "+locklist.size()+" continuations in queue.  That seems large");
        locklist.add(c);
      } else {
        locks.put(id, new LinkedList());
        if (logger.level <= Logger.FINER) logger.log("locking on id "+id+"; no contention so running "+c);
        torun = c;
      }
    }
    if (torun != null)
      torun.receiveResult(null);
  }
  
  public void unlock(Id id) {
    Continuation torun = null;
    if (logger.level <= Logger.FINE) logger.log("unlocking on id "+id);
    synchronized (this) {
      if (locks.containsKey(id)) {
        if (((List)locks.get(id)).isEmpty()) {
          if (logger.level <= Logger.FINER) logger.log("unlocking on id "+id+"; last out the door -- removing lock ");
          locks.remove(id);
        } else {
          Continuation next = (Continuation)((List)locks.get(id)).remove(0);
          if (logger.level <= Logger.FINER) logger.log("unlocking on id "+id+"; starting next continuation "+next);
          torun = next;
        }
      } else {
        if (logger.level <= Logger.WARNING) logger.log("unlocking on id "+id+"; no lock currently held!!");
      }
    }
    if (torun != null)
      torun.receiveResult(null);
  }

}
