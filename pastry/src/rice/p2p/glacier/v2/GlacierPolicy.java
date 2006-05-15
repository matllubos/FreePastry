package rice.p2p.glacier.v2;

import rice.p2p.commonapi.*;
import rice.p2p.glacier.*;
import rice.p2p.past.PastContent;
import rice.p2p.past.rawserialization.PastContentDeserializer;
import rice.Continuation;

public interface GlacierPolicy {

  public boolean checkSignature(Manifest manifest, VersionKey key);
  
  public Fragment[] encodeObject(PastContent obj, boolean[] generateFragment);
  
  public Manifest[] createManifests(VersionKey key, PastContent obj, Fragment[] fragments, long expiration);

  public Manifest updateManifest(VersionKey key, Manifest manifest, long newExpiration);

  public PastContent decodeObject(Fragment[] fragments, Endpoint endpoint, PastContentDeserializer pcd);
 
  public void prefetchLocalObject(VersionKey key, Continuation command);
 
}
