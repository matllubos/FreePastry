package rice.p2p.glacier.v2.messaging;

import rice.*;
import rice.p2p.commonapi.*;
import rice.p2p.glacier.*;
import rice.p2p.glacier.v2.BloomFilter;

public class GlacierSyncMessage extends GlacierMessage {
  protected IdRange range;
  protected int offsetFID;
  protected BloomFilter BloomFilter;

  public GlacierSyncMessage(int uid, IdRange range, int offsetFID, BloomFilter BloomFilter, NodeHandle source, Id dest, char tag) {
    super(uid, source, dest, false, tag);

    this.range = range;
    this.offsetFID = offsetFID;
    this.BloomFilter = BloomFilter;
  }

  public int getOffsetFID() {
    return offsetFID;
  }

  public IdRange getRange() {
    return range;
  }

  public BloomFilter getBloomFilter() {
    return BloomFilter;
  }

  public String toString() {
    return "[GlacierSync for range "+range+", offset "+offsetFID+"]";
  }
}

