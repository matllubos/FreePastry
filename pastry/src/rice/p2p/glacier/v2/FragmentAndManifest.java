package rice.p2p.glacier.v2;

import rice.p2p.glacier.Fragment;
import rice.p2p.glacier.v2.Manifest;
import rice.p2p.glacier.FragmentKey;
import java.io.Serializable;

public class FragmentAndManifest implements Serializable {
  public Fragment fragment;
  public Manifest manifest;
  public FragmentKey key;
  public FragmentMetadata metadata;
  
  public FragmentAndManifest(FragmentKey key, Fragment fragment, Manifest manifest, FragmentMetadata metadata) {
    this.key = key;
    this.fragment = fragment;
    this.manifest = manifest;
    this.metadata = metadata;
  }
}
