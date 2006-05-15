package rice.p2p.aggregation;

import java.io.IOException;

import rice.p2p.commonapi.*;
import rice.p2p.commonapi.rawserialization.*;
import rice.p2p.past.*;
import rice.p2p.past.gc.*;
import rice.p2p.past.gc.rawserialization.RawGCPastContentHandle;

public class AggregateHandle implements RawGCPastContentHandle {

  public static final short TYPE = 1;
  
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

  public short getType() {
    return TYPE;
  }
  
  public AggregateHandle(InputBuffer buf, Endpoint endpoint) throws IOException {
    version = buf.readLong();
    expiration = buf.readLong();
    id = endpoint.readId(buf, buf.readShort());
    handle = endpoint.readNodeHandle(buf);
  }

  public void serialize(OutputBuffer buf) throws IOException {
    buf.writeLong(version);
    buf.writeLong(expiration);
    buf.writeShort(id.getType());
    id.serialize(buf);
    handle.serialize(buf);
  }
}

