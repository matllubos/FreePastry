package rice.p2p.glacier;

import rice.p2p.commonapi.*;
import rice.p2p.glacier.VersionKey;

/**
 * DESCRIBE THE CLASS
 *
 * @version $Id$
 * @author ahae
 */
public class FragmentKey implements Id, Comparable {
  /**
   * DESCRIBE THE FIELD
   */
  protected VersionKey key;
  /**
   * DESCRIBE THE FIELD
   */
  protected int id;

  /**
   * Constructor for FragmentKey.
   *
   * @param key DESCRIBE THE PARAMETER
   * @param id DESCRIBE THE PARAMETER
   */
  public FragmentKey(VersionKey key, int id) {
    this.id = id;
    this.key = key;
  }

  /**
   * Gets the Between attribute of the FragmentKey object
   *
   * @param ccw DESCRIBE THE PARAMETER
   * @param cw DESCRIBE THE PARAMETER
   * @return The Between value
   */
  public boolean isBetween(Id ccw, Id cw) {
    System.err.println("FragmentKey::isBetween() called");
    System.exit(1);
    return false;
  }

  /**
   * Gets the VersionKey attribute of the FragmentKey object
   *
   * @return The VersionKey value
   */
  public VersionKey getVersionKey() {
    return key;
  }

  /**
   * Gets the FragmentID attribute of the FragmentKey object
   *
   * @return The FragmentID value
   */
  public int getFragmentID() {
    return id;
  }

  /**
   * Gets the PeerKey attribute of the FragmentKey object
   *
   * @param otherId DESCRIBE THE PARAMETER
   * @return The PeerKey value
   */
  public FragmentKey getPeerKey(int otherId) {
    return new FragmentKey(key, otherId);
  }

  /**
   * DESCRIBE THE METHOD
   *
   * @param peer DESCRIBE THE PARAMETER
   * @return DESCRIBE THE RETURN VALUE
   */
  public boolean equals(Object peer) {
    if (!(peer instanceof FragmentKey)) {
      return false;
    }

    FragmentKey sk = (FragmentKey) peer;
    return (sk.key.equals(this.key) && (sk.id == this.id));
  }

  /**
   * DESCRIBE THE METHOD
   *
   * @return DESCRIBE THE RETURN VALUE
   */
  public byte[] toByteArray() {
    System.err.println("FragmentKey::toByteArray() called");
    System.exit(1);
    return null;
  }

  /**
   * DESCRIBE THE METHOD
   *
   * @return DESCRIBE THE RETURN VALUE
   */
  public String toStringFull() {
    return key.toStringFull() + ":" + id;
  }

  /**
   * DESCRIBE THE METHOD
   *
   * @return DESCRIBE THE RETURN VALUE
   */
  public String toString() {
    return key.toString() + ":" + id;
  }

  /**
   * DESCRIBE THE METHOD
   *
   * @param nid DESCRIBE THE PARAMETER
   * @return DESCRIBE THE RETURN VALUE
   */
  public Distance longDistanceFromId(Id nid) {
    System.err.println("FragmentKey::longDistanceFromId() called");
    System.exit(1);
    return null;
  }

  /**
   * DESCRIBE THE METHOD
   *
   * @param nid DESCRIBE THE PARAMETER
   * @return DESCRIBE THE RETURN VALUE
   */
  public Distance distanceFromId(Id nid) {
    System.err.println("FragmentKey::distanceFromId() called");
    System.exit(1);
    return null;
  }

  /**
   * Adds a feature to the ToId attribute of the FragmentKey object
   *
   * @param offset The feature to be added to the ToId attribute
   * @return DESCRIBE THE RETURN VALUE
   */
  public Id addToId(Distance offset) {
    System.err.println("FragmentKey::addToId() called");
    System.exit(1);
    return null;
  }

  /**
   * DESCRIBE THE METHOD
   *
   * @param nid DESCRIBE THE PARAMETER
   * @return DESCRIBE THE RETURN VALUE
   */
  public boolean clockwise(Id nid) {
    System.err.println("FragmentKey::clockwise() called");
    System.exit(1);
    return false;
  }

  /**
   * DESCRIBE THE METHOD
   *
   * @param o DESCRIBE THE PARAMETER
   * @return DESCRIBE THE RETURN VALUE
   */
  public int compareTo(Object o) {
    int keyResult = key.compareTo(((FragmentKey) o).key);
    if (keyResult != 0) {
      return keyResult;
    }

    if (this.id < ((FragmentKey) o).id) {
      return -1;
    }
    if (this.id > ((FragmentKey) o).id) {
      return 1;
    }

    return 0;
  }

  /**
   * DESCRIBE THE METHOD
   *
   * @return DESCRIBE THE RETURN VALUE
   */
  public int hashCode() {
    return (key.hashCode() + id);
  }
}
