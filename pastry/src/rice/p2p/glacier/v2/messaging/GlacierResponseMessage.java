package rice.p2p.glacier.v2.messaging;

import rice.*;
import rice.p2p.commonapi.*;
import rice.p2p.glacier.*;

public class GlacierResponseMessage extends GlacierMessage {
  protected FragmentKey[] keys;
  protected boolean[] haveIt;
  protected boolean[] authoritative;

  public GlacierResponseMessage(int uid, FragmentKey key, boolean haveIt, boolean authoritative, NodeHandle source, Id dest, boolean isResponse) {
    this(uid, new FragmentKey[] { key }, new boolean[] { haveIt }, new boolean[] { authoritative }, source, dest, isResponse);
  }

  public GlacierResponseMessage(int uid, FragmentKey[] keys, boolean[] haveIt, boolean[] authoritative, NodeHandle source, Id dest, boolean isResponse) {
    super(uid, source, dest, isResponse);

    this.keys = keys;
    this.haveIt = haveIt;
    this.authoritative = authoritative;
  }

  public int numKeys() {
    return keys.length;
  }

  public FragmentKey getKey(int index) {
    return keys[index];
  }

  public boolean getHaveIt(int index) {
    return haveIt[index];
  }

  public boolean getAuthoritative(int index) {
    return authoritative[index];
  }

  public String toString() {
    return "[GlacierResponse for " + keys[0] + " ("+(numKeys()-1)+" more keys)]";
  }
}

