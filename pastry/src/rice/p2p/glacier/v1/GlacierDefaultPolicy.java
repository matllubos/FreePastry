package rice.p2p.glacier.v1;

import rice.p2p.commonapi.*;
import rice.p2p.glacier.v1.*;

public class GlacierDefaultPolicy implements GlacierPolicy {

  public Authenticator extractAuthenticator(Id key, StorageManifest manifest)
  {
    if (manifest instanceof Authenticator)
      return (Authenticator)manifest;
      
    return null;
  }
  
  public boolean shouldStore(Id key, StorageManifest current, StorageManifest candidate)
  {
    if (current == null)
      return true;
      
    return false;
  }
}
