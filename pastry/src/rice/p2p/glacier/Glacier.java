package rice.p2p.glacier;

import java.io.Serializable;
import rice.*;
import rice.p2p.past.PastContent;
import rice.p2p.glacier.*;
import rice.p2p.commonapi.*;

public interface Glacier {

  public Authenticator getAuthenticator(Serializable obj);
  
  public void insert(final PastContent obj, final StorageManifest manifest, final Continuation command) throws InvalidManifestException; 
}

