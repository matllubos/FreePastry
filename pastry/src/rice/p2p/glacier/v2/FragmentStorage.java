package rice.p2p.glacier.v2;

import java.io.Serializable;
import java.util.Hashtable;
import rice.p2p.glacier.*;
import rice.p2p.commonapi.Id;
import rice.p2p.commonapi.IdSet;
import rice.p2p.commonapi.IdRange;
import rice.Continuation;
import rice.persistence.StorageManager;

public class FragmentStorage {
  
  protected StorageManager storage;
  protected Hashtable metadataCache;
  
  public FragmentStorage(StorageManager storage) {
    this.storage = storage;
    this.metadataCache = new Hashtable();
  }
  
  public IdSet scan() {
    return storage.scan();
  }
  
  public boolean exists(Id id) {
    return storage.exists(id);
  }
  
  public void getObject(Id id, Continuation c) {
    final Continuation cf = c;
    storage.getObject(id, new Continuation() {
      public void receiveResult(Object o) {
        if (o != null) {
          FragmentAndManifest fam = (FragmentAndManifest) o;
          metadataCache.put(fam.key, fam.metadata);
        }
        
        cf.receiveResult(o);
      }
      public void receiveException(Exception e) {
        cf.receiveException(e);
      }
    });
  }
  
  public void store(Id id, FragmentAndManifest fam, Continuation c) {
    metadataCache.put(fam.key, fam.metadata);
    storage.store(id, fam, c);
  }
  
  public void unstore(Id id, Continuation c) {
    storage.unstore(id, c);
  }
}
  
