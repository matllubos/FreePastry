package rice.p2p.aggregation;

import rice.p2p.commonapi.*;
import rice.p2p.past.*;
import rice.p2p.past.gc.*;

class AggregateHandle implements PastContentHandle, GCPastContentHandle {

  protected Id id;
  protected NodeHandle handle;
  protected long version;
  protected long expiration;

  public AggregateHandle(NodeHandle handle, Id id, long version, long expiration) {
    this.id = id;
    this.handle = handle;
    this.version = version;
    this.expiration = expiration;
  }
  
  public Id getId() {
    return id;
  }

  public NodeHandle getNodeHandle() {
    return handle;
  }

  public long getVersion() {
    return version;
  }

  public long getExpiration() {
    return expiration;
  }
  
}

