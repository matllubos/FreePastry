package rice.p2p.glacier;

import rice.p2p.commonapi.*;
import rice.p2p.glacier.*;

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
