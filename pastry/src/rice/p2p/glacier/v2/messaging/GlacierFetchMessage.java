package rice.p2p.glacier.v2.messaging;

import rice.*;
import rice.p2p.commonapi.*;
import rice.p2p.glacier.*;

public class GlacierFetchMessage extends GlacierMessage {
  protected FragmentKey key;
  protected int request;

  public static final int FETCH_FRAGMENT = 1;
  public static final int FETCH_MANIFEST = 2;
  public static final int FETCH_FRAGMENT_AND_MANIFEST = FETCH_FRAGMENT | FETCH_MANIFEST;

  public GlacierFetchMessage(int uid, FragmentKey key, int request, NodeHandle source, Id dest, char tag) {
    super(uid, source, dest, false, tag);

    this.key = key;
    this.request = request;
  }

  public int getRequest() {
    return request;
  }

  public FragmentKey getKey() {
    return key;
  }

  public String toString() {
    return "[GlacierFetch for " + key + ", req="+request+"]";
  }
}

