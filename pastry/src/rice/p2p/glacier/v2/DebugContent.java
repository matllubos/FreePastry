package rice.p2p.glacier.v2;

import rice.p2p.past.*;
import rice.p2p.past.gc.*;
import rice.p2p.commonapi.*;
import rice.p2p.glacier.*;

public class DebugContent implements PastContent, GCPastContent {

  protected Id myId;
  protected boolean isMutable;
  protected long version;

  public DebugContent(Id id, boolean isMutable, long version)
  {
    myId = id;
    this.isMutable = isMutable;
    this.version = version;
  }

  public PastContent checkInsert(Id id, PastContent existingContent) throws PastException
  {
    if (!isMutable ||  !(existingContent instanceof DebugContent))
      return this;
      
    DebugContent dc = (DebugContent) existingContent;
    return (this.version > dc.version) ? this : dc;
  }

  public long getVersion() {
    return version;
  }
  
  public PastContentHandle getHandle(Past local)
  {
    return new DebugContentHandle(myId, version, GCPast.INFINITY_EXPIRATION, local.getLocalNodeHandle());
  }
  
  public GCPastContentHandle getHandle(GCPast local, long expiration)
  {
    return new DebugContentHandle(myId, version, expiration, local.getLocalNodeHandle());
  }
  
  public Id getId()
  {
    return myId;
  }
  
  public boolean isMutable()
  {
    return this.isMutable;
  }
}

