package rice.p2p.glacier.v1;
import java.io.Serializable;
import java.util.Date;
import rice.p2p.commonapi.Id;

import rice.p2p.glacier.*;

/**
 * DESCRIBE THE CLASS
 *
 * @version $Id$
 * @author ahae
 */
public class FileInfo implements Serializable {

  /**
   * DESCRIBE THE FIELD
   */
  public VersionKey key;
  /**
   * DESCRIBE THE FIELD
   */
  public StorageManifest manifest;
  /**
   * DESCRIBE THE FIELD
   */
  public boolean[][] holderKnown;
  /**
   * DESCRIBE THE FIELD
   */
  public Id[][] holderId;
  /**
   * DESCRIBE THE FIELD
   */
  public boolean[][] holderDead;
  /**
   * DESCRIBE THE FIELD
   */
  public boolean[][] holderCertain;
  /**
   * DESCRIBE THE FIELD
   */
  public Date[][] lastHeard;
  /**
   * DESCRIBE THE FIELD
   */
  public int[] holderPointer;
  /**
   * DESCRIBE THE FIELD
   */
  public final static int maxHoldersPerFragment = 8;

  /**
   * Constructor for FileInfo.
   *
   * @param key DESCRIBE THE PARAMETER
   * @param manifest DESCRIBE THE PARAMETER
   * @param numFragments DESCRIBE THE PARAMETER
   */
  public FileInfo(VersionKey key, StorageManifest manifest, int numFragments) {
    this.key = key;
    this.manifest = manifest;

    holderKnown = new boolean[numFragments][maxHoldersPerFragment];
    holderId = new Id[numFragments][maxHoldersPerFragment];
    holderDead = new boolean[numFragments][maxHoldersPerFragment];
    holderCertain = new boolean[numFragments][maxHoldersPerFragment];
    lastHeard = new Date[numFragments][maxHoldersPerFragment];
    holderPointer = new int[numFragments];

    for (int i = 0; i < numFragments; i++) {
      holderPointer[i] = 0;
      for (int j = 0; j < maxHoldersPerFragment; j++) {
        holderKnown[i][j] = false;
      }
    }
  }

  /**
   * Gets the NextAvailableSlotFor attribute of the FileInfo object
   *
   * @param fragmentID DESCRIBE THE PARAMETER
   * @return The NextAvailableSlotFor value
   */
  public int getNextAvailableSlotFor(int fragmentID) {
    int triesLeft = maxHoldersPerFragment;
    int i = holderPointer[fragmentID];

    while (triesLeft-- > 0) {
      if (!holderKnown[fragmentID][i] || holderDead[fragmentID][i]) {
        holderPointer[fragmentID] = (i + 1) % maxHoldersPerFragment;
        return i;
      }

      i = (i + 1) % maxHoldersPerFragment;
    }

    return -1;
  }

  /**
   * DESCRIBE THE METHOD
   *
   * @param fragmentID DESCRIBE THE PARAMETER
   * @return DESCRIBE THE RETURN VALUE
   */
  public boolean haveFragment(int fragmentID) {
    for (int i = 0; i < maxHoldersPerFragment; i++) {
      if (holderKnown[fragmentID][i] && !holderDead[fragmentID][i] &&
        (holderId[fragmentID][i] == null)) {
        return true;
      }
    }

    return false;
  }

  /**
   * DESCRIBE THE METHOD
   *
   * @param fragmentID DESCRIBE THE PARAMETER
   * @return DESCRIBE THE RETURN VALUE
   */
  public boolean anyLiveHolder(int fragmentID) {
    for (int i = 0; i < maxHoldersPerFragment; i++) {
      if (holderKnown[fragmentID][i] && !holderDead[fragmentID][i]) {
        return true;
      }
    }

    return false;
  }
}
