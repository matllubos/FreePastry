package rice.p2p.glacier.v2;

import rice.p2p.commonapi.Id;
import rice.p2p.glacier.v2.*;
import rice.p2p.glacier.*;
import rice.Continuation;
import java.io.Serializable;

public interface GlacierPolicy {

  public boolean checkSignature(Manifest manifest, VersionKey key);
  
  public Fragment[] encodeObject(Serializable obj);
  
  public Manifest[] createManifests(VersionKey key, Serializable obj, Fragment[] fragments, long expiration);

  public Serializable decodeObject(Fragment[] fragments);
 
  public void prefetchLocalObject(VersionKey key, Continuation command);
 
}
