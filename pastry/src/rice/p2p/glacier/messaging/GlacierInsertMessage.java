package rice.p2p.glacier.messaging;

import rice.*;
import rice.p2p.commonapi.*;
import rice.p2p.glacier.*;

/**
 * DESCRIBE THE CLASS
 *
 * @version $Id$
 * @author ahae
 */
public class GlacierInsertMessage extends GlacierMessage {
  /**
   * DESCRIBE THE FIELD
   */
  public Id knownHolder[];
  /**
   * DESCRIBE THE FIELD
   */
  public int knownHolderFragmentID[];
  /**
   * DESCRIBE THE FIELD
   */
  public boolean knownHolderCertain[];
  /**
   * DESCRIBE THE FIELD
   */
  protected FragmentKey key;
  StorageManifest manifest;
  Fragment fragment;

  /**
   * Constructor for GlacierInsertMessage.
   *
   * @param uid DESCRIBE THE PARAMETER
   * @param key DESCRIBE THE PARAMETER
   * @param manifest DESCRIBE THE PARAMETER
   * @param fragment DESCRIBE THE PARAMETER
   * @param knownHolder DESCRIBE THE PARAMETER
   * @param knownHolderFragmentID DESCRIBE THE PARAMETER
   * @param knownHolderCertain DESCRIBE THE PARAMETER
   * @param source DESCRIBE THE PARAMETER
   * @param dest DESCRIBE THE PARAMETER
   */
  public GlacierInsertMessage(int uid, FragmentKey key, StorageManifest manifest, Fragment fragment, Id[] knownHolder, int[] knownHolderFragmentID, boolean[] knownHolderCertain, NodeHandle source, Id dest) {
    super(uid, source, dest);

    this.key = key;
    this.manifest = manifest;
    this.fragment = fragment;
    this.knownHolder = knownHolder;
    this.knownHolderFragmentID = knownHolderFragmentID;
    this.knownHolderCertain = knownHolderCertain;
  }

  /**
   * Gets the Key attribute of the GlacierInsertMessage object
   *
   * @return The Key value
   */
  public FragmentKey getKey() {
    return key;
  }

  /**
   * Gets the StorageManifest attribute of the GlacierInsertMessage object
   *
   * @return The StorageManifest value
   */
  public StorageManifest getStorageManifest() {
    return manifest;
  }

  /**
   * Gets the Fragment attribute of the GlacierInsertMessage object
   *
   * @return The Fragment value
   */
  public Fragment getFragment() {
    return fragment;
  }

  /**
   * DESCRIBE THE METHOD
   *
   * @return DESCRIBE THE RETURN VALUE
   */
  public String toString() {
    return "[GlacierInsert for " + key + "]";
  }
}

