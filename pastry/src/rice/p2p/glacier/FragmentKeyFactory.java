package rice.p2p.glacier;
import java.util.StringTokenizer;

import rice.p2p.commonapi.*;
import rice.p2p.multiring.MultiringIdFactory;
import rice.p2p.multiring.RingId;
import rice.pastry.Id;

/**
 * DESCRIBE THE CLASS
 *
 * @version $Id$
 * @author ahae
 */
public class FragmentKeyFactory implements IdFactory {

  private MultiringIdFactory FACTORY;

  /**
   * Constructor for FragmentKeyFactory.
   *
   * @param factory DESCRIBE THE PARAMETER
   */
  public FragmentKeyFactory(MultiringIdFactory factory) {
    FACTORY = factory;
  }

  /**
   * DESCRIBE THE METHOD
   *
   * @param material DESCRIBE THE PARAMETER
   * @return DESCRIBE THE RETURN VALUE
   */
  public rice.p2p.commonapi.Id buildId(byte[] material) {
    System.err.println("FragmentKeyFactory.buildId(byte[]) called");
    System.exit(1);
    return null;
  }

  /**
   * Builds a protocol-specific Id given the source data.
   *
   * @param material The material to use
   * @return The built Id.
   */
  public rice.p2p.commonapi.Id buildId(int[] material) {
    System.err.println("FragmentKeyFactory.buildId(int[]) called");
    System.exit(1);
    return null;
  }

  /**
   * Builds a protocol-specific Id by using the hash of the given string as
   * source data.
   *
   * @param string The string to use as source data
   * @return The built Id.
   */
  public rice.p2p.commonapi.Id buildId(String string) {
    System.err.println("FragmentKeyFactory.buildId(String) called");
    System.exit(1);
    return null;
  }

  /**
   * DESCRIBE THE METHOD
   *
   * @param string DESCRIBE THE PARAMETER
   * @return DESCRIBE THE RETURN VALUE
   */
  public rice.p2p.commonapi.Id buildIdFromToString(String string) {
    StringTokenizer stok = new StringTokenizer(string, "(,)- :v");
    if (stok.countTokens() < 4) {
      return null;
    }

    String keyRingS = stok.nextToken();
    String keyNodeS = stok.nextToken();
    String versionS = stok.nextToken();
    String fragmentIdS = stok.nextToken();
    RingId key = FACTORY.buildRingId(rice.pastry.Id.build(keyRingS), rice.pastry.Id.build(keyNodeS));

    return new FragmentKey(new VersionKey(key, Integer.valueOf(versionS).intValue()), Integer.valueOf(fragmentIdS).intValue());
  }

  public rice.p2p.commonapi.Id buildIdFromToString(char[] chars, int offset, int length) {
    System.err.println("FragmentKeyFactory.buildIdFromToString(char[], int, int) called");
    System.exit(1);
    return null;
  }

  /**
   * Builds a protocol-specific Id.Distance given the source data.
   *
   * @param material The material to use
   * @return The built Id.Distance.
   */
  public rice.p2p.commonapi.Id.Distance buildIdDistance(byte[] material) {
    System.err.println("FragmentKeyFactory.buildIdDistance() called");
    System.exit(1);
    return null;
  }

  /**
   * Creates an IdRange given the CW and CCW ids.
   *
   * @param cw The clockwise Id
   * @param ccw The counterclockwise Id
   * @return An IdRange with the appropriate delimiters.
   */
  public IdRange buildIdRange(rice.p2p.commonapi.Id cw, rice.p2p.commonapi.Id ccw) {
    System.err.println("FragmentKeyFactory.buildIdRange() called");
    System.exit(1);
    return null;
  }

  /**
   * Creates an empty IdSet.
   *
   * @return an empty IdSet
   */
  public IdSet buildIdSet() {
    return new FragmentKeySet();
  }

  /**
   * Creates an empty NodeHandleSet.
   *
   * @return an empty NodeHandleSet
   */
  public NodeHandleSet buildNodeHandleSet() {
    System.err.println("FragmentKeyFactory.buildNodeHandleSet() called");
    System.exit(1);
    return null;
  }
  
  public int getIdToStringLength() {
    return FACTORY.getIdToStringLength() + 4;
  }
}
