package rice.p2p.glacier;

import rice.p2p.commonapi.*;
import java.util.StringTokenizer;
import rice.p2p.multiring.RingId;
import rice.pastry.Id;
import rice.p2p.multiring.MultiringIdFactory;

public class StorageKeyFactory implements IdFactory {

  private MultiringIdFactory FACTORY;

  public StorageKeyFactory(MultiringIdFactory factory)
  {
    FACTORY = factory;
  }

  public rice.p2p.commonapi.Id buildId(byte[] material)
  {
    System.err.println("StorageKeyFactory.buildId(byte[]) called");
    System.exit(1);
    return null;
  }

  /**
   * Builds a protocol-specific Id given the source data.
   *
   * @param material The material to use
   * @return The built Id.
   */
  public rice.p2p.commonapi.Id buildId(int[] material)
  {
    System.err.println("StorageKeyFactory.buildId(int[]) called");
    System.exit(1);
    return null;
  }

  /**
   * Builds a protocol-specific Id by using the hash of the given string as source data.
   *
   * @param string The string to use as source data
   * @return The built Id.
   */
  public rice.p2p.commonapi.Id buildId(String string)
  {
    System.err.println("StorageKeyFactory.buildId(String) called");
    System.exit(1);
    return null;
  }

  public rice.p2p.commonapi.Id buildIdFromToString(String string)
  {
    StringTokenizer stok = new StringTokenizer(string, "(,)- :");
    if (stok.countTokens()<4)
        return null;
        
    String ownerRingS = stok.nextToken();
    String ownerNodeS = stok.nextToken();
    String keyRingS = stok.nextToken();
    String keyNodeS = stok.nextToken();
    String fragmentIdS = stok.nextToken();
    RingId owner = FACTORY.buildRingId(new rice.pastry.Id(ownerRingS), new rice.pastry.Id(ownerNodeS));
    RingId key = FACTORY.buildRingId(new rice.pastry.Id(keyRingS), new rice.pastry.Id(keyNodeS));

    return new StorageKey(new FragmentKey(owner, key), Integer.valueOf(fragmentIdS).intValue());
  }

  /**
   * Builds a protocol-specific Id.Distance given the source data.
   *
   * @param material The material to use
   * @return The built Id.Distance.
   */
  public rice.p2p.commonapi.Id.Distance buildIdDistance(byte[] material)
  {
    System.err.println("StorageKeyFactory.buildIdDistance() called");
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
  public IdRange buildIdRange(rice.p2p.commonapi.Id cw, rice.p2p.commonapi.Id ccw)
  {
    System.err.println("StorageKeyFactory.buildIdRange() called");
    System.exit(1);
    return null;
  }
  
  /**
   * Creates an empty IdSet.
   *
   * @return an empty IdSet
   */
  public IdSet buildIdSet()
  {
    return new StorageKeySet();
  }
  
  /**
   * Creates an empty NodeHandleSet.
   *
   * @return an empty NodeHandleSet
   */
  public NodeHandleSet buildNodeHandleSet()
  {
    System.err.println("StorageKeyFactory.buildNodeHandleSet() called");
    System.exit(1);
    return null;
  }
}
