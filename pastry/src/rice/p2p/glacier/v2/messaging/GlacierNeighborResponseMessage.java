package rice.p2p.glacier.v2.messaging;

import rice.*;
import rice.p2p.commonapi.*;

public class GlacierNeighborResponseMessage extends GlacierMessage {

  protected Id[] neighbors;
  protected long[] lastSeen;

  public GlacierNeighborResponseMessage(int uid, Id[] neighbors, long[] lastSeen, NodeHandle source, Id dest, char tag) {
    super(uid, source, dest, true, tag);

    this.neighbors = neighbors;
    this.lastSeen = lastSeen;
  }

  public int numNeighbors() {
    if ((neighbors == null) || (lastSeen == null))
      return 0;
      
    if (lastSeen.length < neighbors.length)
      return lastSeen.length;
      
    return neighbors.length;
  }

  public Id getNeighbor(int index) {
    return neighbors[index];
  }
  
  public long getLastSeen(int index) {
    return lastSeen[index];
  }

  public String toString() {
    return "[GlacierNeighborResponse with "+numNeighbors()+" keys]";
  }
}

