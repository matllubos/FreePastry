package rice.p2p.aggregation;

import java.io.Serializable;
import rice.p2p.commonapi.Id;

public class ObjectDescriptor implements Serializable, Comparable {
  
  private static final long serialVersionUID = -3035115249019556223L;
  
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

  public int compareTo(Object other) {
    ObjectDescriptor metadata = (ObjectDescriptor) other;
    
    int result = this.key.compareTo(metadata.key);
    if (result != 0)
      return result;
      
    if (metadata.version > this.version)
      return -1;
    if (metadata.version < this.version)
      return 1;
    
    if (metadata.currentLifetime > this.currentLifetime) 
      return -1;
    if (metadata.currentLifetime < this.currentLifetime) 
      return 1;

    if (metadata.refreshedLifetime > this.refreshedLifetime) 
      return -1;
    if (metadata.refreshedLifetime < this.refreshedLifetime) 
      return 1;

    if (metadata.size > this.size) 
      return -1;
    else if (metadata.size < this.size) 
      return 1;

    return 0;
  }
};

