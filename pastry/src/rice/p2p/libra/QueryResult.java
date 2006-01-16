package rice.p2p.libra;

import java.net.*;

/**
 * A structure for storing all of the useful information returned from a GNP Query packet.
 */
public class QueryResult {
  /** A flag for the stability of the queried host */
  private final boolean stable;
  /** The address and hostname of the queried host */
  private final InetAddress ip;
  /** The coordinate of the queried host */
  private final double[] coord;
  /** The space that the coordinate exists in */
  private final String space;
  
  /**
   * Constructor
   * @param stable A flag for the stability of the queried host
   * @param ip The address and hostname of the queried host
   * @param coord The coordinate of the queried host
   * @param space The space that the coordinate exists in
   */
  public QueryResult(boolean stable, InetAddress ip, double [] coord, String space) {
    this.stable = stable;
    this.ip = ip;
    this.coord = coord;
    this.space = space;
  }
  
  /**
   * Accessor for the stability flag
   */
  public boolean isStable() {
    return stable;
  }
  
  /**
   * Accessor for the address of the queried host
   */
  public InetAddress getIP() {
    return ip;
  }
  
  /**
   * Accessor for the coordinates of the queried host
   */
  public double[] getCoordinates() {
    return coord;
  }
  
  /** 
   * Accessor for the space that the coordinate exists in
   */
  public String getSpace() {
    return space;
  }
}
