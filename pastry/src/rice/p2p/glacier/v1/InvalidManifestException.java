package rice.p2p.glacier.v1;

import rice.p2p.glacier.GlacierException;

public class InvalidManifestException extends GlacierException {
  public InvalidManifestException(String msg)
  {
    super(msg);
  }
}
