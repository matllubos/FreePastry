package rice.p2p.glacier;

import java.io.IOException;

import rice.p2p.commonapi.*;
import rice.p2p.commonapi.rawserialization.*;
import rice.p2p.glacier.VersionKey;
import rice.p2p.util.MathUtils;

/**
 * DESCRIBE THE CLASS
 *
 * @version $Id$
 * @author ahae
 */
public class FragmentKey implements Id, Comparable {
  public static final short TYPE = 42;
  
  /**
   * DESCRIBE THE FIELD
   */
  protected VersionKey key;
  /**
   * DESCRIBE THE FIELD
   */
  protected int id;
  
  private static final long serialVersionUID = 5373228569261524536L;

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
    throw new RuntimeException("FragmentKey.isBetween() is not supported!");
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
    byte[] result = new byte[getByteArrayLength()];

    toByteArray(result, 0);

    return result;
  }
  
  /**
   * Stores the byte[] value of this Id in the provided byte array
   *
   * @return A byte[] representing this Id
   */
  public void toByteArray(byte[] result, int offset) {
    key.toByteArray(result, offset);
    MathUtils.intToByteArray(id, result, offset+key.getByteArrayLength());
  }
  
  /**
    * Returns the length of the byte[] representing this Id
   *
   * @return The length of the byte[] representing this Id
   */
  public int getByteArrayLength() {
    return key.getByteArrayLength() + 4;
  }

  /**
   * DESCRIBE THE METHOD
   *
   * @return DESCRIBE THE RETURN VALUE
   */
  public String toStringFull() {
    return key.toStringFull() + "#" + id;
  }

  /**
   * DESCRIBE THE METHOD
   *
   * @return DESCRIBE THE RETURN VALUE
   */
  public String toString() {
    return key.toString() + "#" + id;
  }

  /**
   * DESCRIBE THE METHOD
   *
   * @param nid DESCRIBE THE PARAMETER
   * @return DESCRIBE THE RETURN VALUE
   */
  public Distance longDistanceFromId(Id nid) {
    throw new RuntimeException("FragmentKey.longDistanceFromId() is not supported!");
  }

  /**
   * DESCRIBE THE METHOD
   *
   * @param nid DESCRIBE THE PARAMETER
   * @return DESCRIBE THE RETURN VALUE
   */
  public Distance distanceFromId(Id nid) {
    throw new RuntimeException("FragmentKey.distanceFromId() is not supported!");
  }

  /**
   * Adds a feature to the ToId attribute of the FragmentKey object
   *
   * @param offset The feature to be added to the ToId attribute
   * @return DESCRIBE THE RETURN VALUE
   */
  public Id addToId(Distance offset) {
    throw new RuntimeException("FragmentKey.addToId() is not supported!");
  }

  /**
   * DESCRIBE THE METHOD
   *
   * @param nid DESCRIBE THE PARAMETER
   * @return DESCRIBE THE RETURN VALUE
   */
  public boolean clockwise(Id nid) {
    throw new RuntimeException("FragmentKey.clockwise() is not supported!");
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
  
  public FragmentKey(InputBuffer buf, Endpoint endpoint) throws IOException {
    id = buf.readInt();
    key = new VersionKey(buf, endpoint);
  }

  public void serialize(OutputBuffer buf) throws IOException {
    buf.writeInt(id);
    key.serialize(buf);
//    throw new RuntimeException("FragmentKey.serialize() is not supported!");
  }

  public short getType() {
    return TYPE;
  }
}
