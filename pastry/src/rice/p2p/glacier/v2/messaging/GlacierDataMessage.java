package rice.p2p.glacier.v2.messaging;

import rice.*;
import rice.p2p.commonapi.*;
import rice.p2p.glacier.v2.StorageManifest;
import rice.p2p.glacier.Fragment;
import rice.p2p.glacier.FragmentKey;

public class GlacierDataMessage extends GlacierMessage {

  protected FragmentKey[] keys;
  protected Fragment[] fragments;
  protected StorageManifest[] manifests;

  public GlacierDataMessage(int uid, FragmentKey key, Fragment fragment, StorageManifest manifest, NodeHandle source, Id dest, boolean isResponse) {
    this(uid, new FragmentKey[] { key }, new Fragment[] { fragment }, new StorageManifest[] { manifest }, source, dest, isResponse);
  }

  public GlacierDataMessage(int uid, FragmentKey[] keys, Fragment[] fragments, StorageManifest[] manifests, NodeHandle source, Id dest, boolean isResponse) {
    super(uid, source, dest, isResponse);

    this.keys = keys;
    this.fragments = fragments;
    this.manifests = manifests;
  }

  public int numKeys() {
    return keys.length;
  }

  public FragmentKey getKey(int index) {
    return keys[index];
  }

  public Fragment getFragment(int index) {
    return fragments[index];
  }

  public StorageManifest getManifest(int index) {
    return manifests[index];
  }

  public String toString() {
    return "[GlacierData for " + keys[0] + " ("+(numKeys()-1)+" more keys)]";
  }
}

