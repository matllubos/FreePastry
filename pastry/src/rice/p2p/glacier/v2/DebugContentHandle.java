package rice.p2p.glacier.v2;

import rice.p2p.past.*;
import rice.p2p.past.gc.*;
import rice.p2p.commonapi.*;

public class DebugContentHandle implements PastContentHandle, GCPastContentHandle {
  protected Id myId;
  protected rice.p2p.commonapi.NodeHandle myNodeHandle;
  protected long myExpiration;
  protected long myVersion;
  
  DebugContentHandle(Id id, long version, long expiration, NodeHandle nodeHandle) {
    myId = id;
    myNodeHandle = nodeHandle;
    myExpiration = expiration;
    myVersion = version;
  }
  
  public Id getId() {
    return myId;
  }
  
  public NodeHandle getNodeHandle() {
    return myNodeHandle;
  }
  
  public long getVersion() {
    return myVersion;
  }
  
  public long getExpiration() {
    return myExpiration;
  }
}
