package rice.p2p.glacier.v1;

import rice.p2p.commonapi.Id;
import rice.p2p.glacier.v1.StorageManifest;

public interface GlacierPolicy {

  public Authenticator extractAuthenticator(Id key, StorageManifest manifest);
  
  public boolean shouldStore(Id key, StorageManifest current, StorageManifest candidate);
}
