package rice.p2p.glacier.v2.messaging;

import rice.*;
import rice.p2p.commonapi.*;
import rice.p2p.glacier.VersionKey;

public class GlacierRefreshPatchMessage extends GlacierMessage {

  protected VersionKey[] keys;
  protected long[] lifetimes;
  protected byte[][] signatures;

  public GlacierRefreshPatchMessage(int uid, VersionKey[] keys, long[] lifetimes, byte[][] signatures, NodeHandle source, Id dest, char tag) {
    super(uid, source, dest, false, tag);

    this.keys = keys;
    this.lifetimes = lifetimes;
    this.signatures = signatures;
  }

  public int numKeys() {
    return keys.length;
  }

  public VersionKey getKey(int index) {
    return keys[index];
  }

  public VersionKey[] getAllKeys() {
    return keys;
  }

  public long getLifetime(int index) {
    return lifetimes[index];
  }

  public byte[] getSignature(int index) {
    return signatures[index];
  }

  public String toString() {
    return "[GlacierRefreshPatch for " + keys[0] + " ("+(numKeys()-1)+" more keys)]";
  }
}

