package rice.p2p.aggregation;

import rice.p2p.commonapi.Id;

class AggregateDescriptor {
  
  public Id key;
  public long currentLifetime;
  public ObjectDescriptor[] objects;
  public Id[] pointers;
  public boolean marker;
  public int referenceCount;

  public AggregateDescriptor(Id key, long currentLifetime, ObjectDescriptor[] objects, Id[] pointers) {
    this.key = key;
    this.currentLifetime = currentLifetime;
    this.objects = objects;
    this.pointers = pointers;
    this.marker = false;
    this.referenceCount = 0;
  }
  
  public int lookupNewest(Id id) {
    int result = -1;
    for (int i=0; i<objects.length; i++)
      if (objects[i].key.equals(id))
        if ((result == -1) || (objects[i].version > objects[result].version))
          result = i;
    return result;
  }

  public int lookupSpecific(Id id, long version) {
    for (int i=0; i<objects.length; i++)
      if (objects[i].key.equals(id) && (objects[i].version == version))
        return i;
        
    return -1;
  }
  
  public void addReference() {
    referenceCount ++;
  }
  
  public int objectsAliveAt(long pointInTime) {
    int result = 0;
    for (int i=0; i<objects.length; i++)
      if (objects[i].isAliveAt(pointInTime))
        result ++;
    return result;
  }
  
  public int bytesAliveAt(long pointInTime) {
    int result = 0;
    for (int i=0; i<objects.length; i++)
      if (objects[i].isAliveAt(pointInTime))
        result += objects[i].size;
    return result;
  }
}  

