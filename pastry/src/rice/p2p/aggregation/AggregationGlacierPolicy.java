package rice.p2p.aggregation;

import rice.p2p.glacier.*;
import rice.p2p.glacier.v2.*;
import rice.persistence.StorageManager;
import rice.Continuation;
import java.util.Arrays;
import java.io.Serializable;

public class AggregationGlacierPolicy implements GlacierPolicy {

  protected StorageManager pastStore;

  public AggregationGlacierPolicy(StorageManager pastStore) {
    this.pastStore = pastStore;
  }

  public boolean checkSignature(StorageManifest manifest, VersionKey key) {
    if (manifest.getSignature() == null)
      return false;
      
    return Arrays.equals(manifest.getSignature(), key.toByteArray());
  }

  protected void signManifest(StorageManifest manifest, VersionKey key) {
    manifest.setSignature(key.toByteArray());
  }

  public Serializable decodeObject(Fragment[] fragments) {
    return null;
  }

  public Fragment[] encodeObject(Serializable obj) {
    return null;
  }

  public StorageManifest[] createManifests(VersionKey key, Serializable obj, Fragment[] fragments, long expiration) {
    return null;
  }

  public void prefetchLocalObject(VersionKey key, Continuation command) {
    pastStore.getObject(key.getId(), command);
  }
};
