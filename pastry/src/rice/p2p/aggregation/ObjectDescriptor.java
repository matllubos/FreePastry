package rice.p2p.aggregation;

import java.io.Serializable;
import rice.p2p.commonapi.Id;

public class ObjectDescriptor implements Serializable {
  
  public Id key;
  public long version;
  public long currentLifetime;
  public long refreshedLifetime;
  public int size;
  
  public ObjectDescriptor(Id key, long version, long currentLifetime, long refreshedLifetime, int size) {
    this.key = key;
    this.currentLifetime = currentLifetime;
    this.refreshedLifetime = refreshedLifetime;
    this.size = size;
    this.version = version;
  }
  
  public String toString() {
    return "objDesc["+key.toStringFull()+"v"+version+", lt="+currentLifetime+", rt="+refreshedLifetime+", size="+size+"]";
  }
  
  public boolean isAliveAt(long pointInTime) {
    return (currentLifetime > pointInTime) || (refreshedLifetime > pointInTime);
  }
};

