package rice.p2p.glacier;

import java.io.*;

import rice.p2p.commonapi.*;
import rice.p2p.commonapi.rawserialization.*;
import rice.p2p.multiring.RingId;
import rice.p2p.util.MathUtils;

/**
 * DESCRIBE THE CLASS
 *
 * @version $Id$
 * @author ahae
 */
public class VersionKey implements Id, Serializable, Comparable {
  public static final short TYPE = 41;
  
  /**
   * DESCRIBE THE FIELD
   */
  protected Id id;
  /**
   * DESCRIBE THE FIELD
   */
  protected long version;
  
  private static final long serialVersionUID = -7473630685140924130L;

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
    id.toByteArray(result, offset);
    MathUtils.longToByteArray(version, result, offset + id.getByteArrayLength());
  }
  
  /**
   * Returns the length of the byte[] representing this Id
   *
   * @return The length of the byte[] representing this Id
   */
  public int getByteArrayLength() {
    return id.getByteArrayLength() + 8;
  }

  public boolean isBetween(Id ccw, Id cw) {
    throw new RuntimeException("VersionKey.isBetween() is not supported!");
  }
  
  public Distance longDistanceFromId(Id nid) {
    throw new RuntimeException("VersionKey.longDistanceFromId() is not supported!");
  }

  public Distance distanceFromId(Id nid) {
    throw new RuntimeException("VersionKey.distanceFromId() is not supported!");
  }
  
  public Id addToId(Distance offset) {
    throw new RuntimeException("VersionKey.addToId() is not supported!");
  }
  
  public boolean clockwise(Id nid) {
    throw new RuntimeException("VersionKey.clockwise() is not supported!");
  }
  
  public static VersionKey build(String s) {
    String[] sArray = s.split("v");
    return new VersionKey(RingId.build(sArray[0]), Long.parseLong(sArray[1]));
  }

  public VersionKey(InputBuffer buf, Endpoint endpoint) throws IOException {
    version = buf.readLong();
    id = endpoint.readId(buf, buf.readShort());
  }

  public void serialize(OutputBuffer buf) throws IOException {
    buf.writeLong(version);
    buf.writeShort(id.getType());
    id.serialize(buf);
  }
  
  public short getType() {
    return TYPE;
  }
}
