package rice.p2p.glacier;

import java.io.Serializable;
import rice.p2p.commonapi.Id;
import rice.p2p.multiring.RingId;

/**
 * DESCRIBE THE CLASS
 *
 * @version $Id$
 * @author ahae
 */
public class VersionKey implements Id, Serializable, Comparable {
  /**
   * DESCRIBE THE FIELD
   */
  protected Id id;
  /**
   * DESCRIBE THE FIELD
   */
  protected long version;

  /**
   * Constructor for VersionKey.
   *
   * @param id DESCRIBE THE PARAMETER
   * @param version DESCRIBE THE PARAMETER
   */
  public VersionKey(Id id, long version) {
    this.id = id;
    this.version = version;
  }

  /**
   * Gets the Version attribute of the VersionKey object
   *
   * @return The Version value
   */
  public long getVersion() {
    return version;
  }

  /**
   * Gets the Id attribute of the VersionKey object
   *
   * @return The Id value
   */
  public Id getId() {
    return id;
  }

  /**
   * DESCRIBE THE METHOD
   *
   * @param peer DESCRIBE THE PARAMETER
   * @return DESCRIBE THE RETURN VALUE
   */
  public boolean equals(Object peer) {
    if (!(peer instanceof VersionKey)) {
      return false;
    }

    VersionKey fk = (VersionKey) peer;
    return ((fk.version == this.version) && fk.id.equals(this.id));
  }

  /**
   * DESCRIBE THE METHOD
   *
   * @return DESCRIBE THE RETURN VALUE
   */
  public String toString() {
    return id.toString() + "v" + version;
  }

  /**
   * DESCRIBE THE METHOD
   *
   * @return DESCRIBE THE RETURN VALUE
   */
  public String toStringFull() {
    return id.toStringFull() + "v" + version;
  }

  /**
   * DESCRIBE THE METHOD
   *
   * @param o DESCRIBE THE PARAMETER
   * @return DESCRIBE THE RETURN VALUE
   */
  public int compareTo(Object o) {
    int idResult = id.compareTo(((VersionKey) o).id);
    if (idResult != 0) {
      return idResult;
    }

    if ((version - ((VersionKey) o).version)<0)
      return -1;

    if ((version - ((VersionKey) o).version)>0)
      return 1;
      
    return 0;
  }

  /**
   * DESCRIBE THE METHOD
   *
   * @return DESCRIBE THE RETURN VALUE
   */
  public int hashCode() {
    return (id.hashCode() + (new Long(version).hashCode()));
  }
  
  public byte[] toByteArray() {
    byte[] v = id.toByteArray();
    byte[] result = new byte[v.length + 8];

    for (int i=0; i<v.length; i++)
      result[i] = v[i];

    result[v.length + 0] = (byte)(0xFF & (version>>56));
    result[v.length + 1] = (byte)(0xFF & (version>>48));
    result[v.length + 2] = (byte)(0xFF & (version>>40));
    result[v.length + 3] = (byte)(0xFF & (version>>32));
    result[v.length + 4] = (byte)(0xFF & (version>>24));
    result[v.length + 5] = (byte)(0xFF & (version>>16));
    result[v.length + 6] = (byte)(0xFF & (version>>8));
    result[v.length + 7] = (byte)(0xFF & (version));

    return result;
  }

  public boolean isBetween(Id ccw, Id cw) {
    System.err.println("VersionKey::isBetween() called");
    System.exit(1);
    return false;
  }
  
  public Distance longDistanceFromId(Id nid) {
    System.err.println("VersionKey::longDistanceFromId() called");
    System.exit(1);
    return null;
  }

  public Distance distanceFromId(Id nid) {
    System.err.println("VersionKey::distanceFromId() called");
    System.exit(1);
    return null;
  }
  
  public Id addToId(Distance offset) {
    System.err.println("VersionKey::addToId() called");
    System.exit(1);
    return null;
  }
  
  public boolean clockwise(Id nid) {
    System.err.println("VersionKey::clockwise() called");
    System.exit(1);
    return false;
  }
  
  public static VersionKey build(String s) {
    String[] sArray = s.split("v");
    return new VersionKey(RingId.build(sArray[0]), Long.parseLong(sArray[1]));
  }
}
