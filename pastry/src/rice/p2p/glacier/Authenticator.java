package rice.p2p.glacier;
import java.io.Serializable;

import rice.p2p.commonapi.*;
import rice.p2p.glacier.StorageManifest;

/**
 * DESCRIBE THE CLASS
 *
 * @version $Id$
 * @author ahae
 */
public class Authenticator implements Serializable, StorageManifest {
  /**
   * DESCRIBE THE FIELD
   */
  protected byte[] objectHash;
  /**
   * DESCRIBE THE FIELD
   */
  protected byte[][] fragmentHash;
  /**
   * DESCRIBE THE FIELD
   */
  protected int version;

  /**
   * Constructor for Authenticator.
   *
   * @param objectHash DESCRIBE THE PARAMETER
   * @param fragmentHash DESCRIBE THE PARAMETER
   * @param version DESCRIBE THE PARAMETER
   */
  public Authenticator(byte[] objectHash, byte[][] fragmentHash, int version) {
    this.objectHash = objectHash;
    this.fragmentHash = fragmentHash;
    this.version = version;
  }

  /**
   * Gets the ObjectHash attribute of the Authenticator object
   *
   * @return The ObjectHash value
   */
  public byte[] getObjectHash() {
    return objectHash;
  }

  /**
   * Gets the FragmentHash attribute of the Authenticator object
   *
   * @param fragmentID DESCRIBE THE PARAMETER
   * @return The FragmentHash value
   */
  public byte[] getFragmentHash(int fragmentID) {
    return fragmentHash[fragmentID];
  }

  /**
   * Gets the Version attribute of the Authenticator object
   *
   * @return The Version value
   */
  public int getVersion() {
    return version;
  }
}
