package rice.p2p.glacier.v2.messaging;

import rice.*;
import rice.p2p.commonapi.*;
import rice.p2p.glacier.*;

public class GlacierFetchMessage extends GlacierMessage {
  protected FragmentKey[] keys;
  protected int request;

  public static final int FETCH_FRAGMENT = 1;
  public static final int FETCH_MANIFEST = 2;
  public static final int FETCH_FRAGMENT_AND_MANIFEST = FETCH_FRAGMENT | FETCH_MANIFEST;

  public GlacierFetchMessage(int uid, FragmentKey key, int request, NodeHandle source, Id dest, char tag) {
    this(uid, new FragmentKey[] { key }, request, source, dest, tag);
  }

  public GlacierFetchMessage(int uid, FragmentKey[] keys, int request, NodeHandle source, Id dest, char tag) {
    super(uid, source, dest, false, tag);

    this.keys = keys;
    this.request = request;
  }

  public FragmentKey[] getAllKeys() {
    return keys;
  }

  public int getRequest() {
    return request;
  }

  public int getNumKeys() {
    return keys.length;
  }

  public FragmentKey getKey(int index) {
    return keys[index];
  }

  public String toString() {
    return "[GlacierFetch for " + keys[0] + " and "+(keys.length-1)+" other keys, req="+request+"]";
  }
}

