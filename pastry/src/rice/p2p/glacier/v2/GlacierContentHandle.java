package rice.p2p.glacier.v2;

import java.io.Serializable;

import rice.*;
import rice.p2p.past.PastContentHandle;
import rice.p2p.past.gc.GCPastContentHandle;
import rice.p2p.commonapi.*;

public class GlacierContentHandle implements PastContentHandle, GCPastContentHandle {

  protected Id id;
  protected NodeHandle nodeHandle;
  protected Manifest manifest;
  protected long version;

  public GlacierContentHandle(Id id, long version, NodeHandle nodeHandle, Manifest manifest) {
    this.id = id;
    this.nodeHandle = nodeHandle;
    this.manifest = manifest;
    this.version = version;
  }

  public Id getId() {
    return id;
  }
  
  public NodeHandle getNodeHandle() {
    return nodeHandle;
  }
  
  public long getVersion() {
    return version;
  }
  
  public long getExpiration() {
    return manifest.expirationDate;
  }
  
  public Manifest getManifest() {
    return manifest;
  }
}





