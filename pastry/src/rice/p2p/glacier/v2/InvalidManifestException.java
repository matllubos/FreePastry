package rice.p2p.glacier.v2;

import rice.p2p.glacier.GlacierException;

public class InvalidManifestException extends GlacierException {
  public InvalidManifestException(String msg)
  {
    super(msg);
  }
}
