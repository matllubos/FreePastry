package rice.p2p.glacier;

import rice.p2p.commonapi.*;
import rice.p2p.glacier.VersionKey;

public class FragmentKey implements Id, Comparable {

  protected VersionKey key;
  protected int id;

  public FragmentKey(VersionKey key, int id) {
    this.id = id;
    this.key = key;
  }

  public boolean isBetween(Id ccw, Id cw) {
    System.err.println("FragmentKey::isBetween() called");
    System.exit(1);
    return false;
  }

  public VersionKey getVersionKey() {
    return key;
  }

  public int getFragmentID() {
    return id;
  }

  public FragmentKey getPeerKey(int otherId) {
    return new FragmentKey(key, otherId);
  }

  public boolean equals(Object peer) {
    if (!(peer instanceof FragmentKey)) {
      return false;
    }

    FragmentKey sk = (FragmentKey) peer;
    return (sk.key.equals(this.key) && (sk.id == this.id));
  }

  public byte[] toByteArray() {
    byte[] v = key.toByteArray();
    byte[] result = new byte[v.length + 4];

    for (int i=0; i<v.length; i++)
      result[i] = v[i];

    result[v.length + 0] = (byte)(0xFF & (id>>24));
    result[v.length + 1] = (byte)(0xFF & (id>>16));
    result[v.length + 2] = (byte)(0xFF & (id>>8));
    result[v.length + 3] = (byte)(0xFF & (id));

    return result;
  }

  public String toStringFull() {
    return key.toStringFull() + ":" + id;
  }

  public String toString() {
    return key.toString() + ":" + id;
  }

  public Distance longDistanceFromId(Id nid) {
    System.err.println("FragmentKey::longDistanceFromId() called");
    System.exit(1);
    return null;
  }

  public Distance distanceFromId(Id nid) {
    System.err.println("FragmentKey::distanceFromId() called");
    System.exit(1);
    return null;
  }

  public Id addToId(Distance offset) {
    System.err.println("FragmentKey::addToId() called");
    System.exit(1);
    return null;
  }

  public boolean clockwise(Id nid) {
    System.err.println("FragmentKey::clockwise() called");
    System.exit(1);
    return false;
  }

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

  public int hashCode() {
    return (key.hashCode() + id);
  }
}
