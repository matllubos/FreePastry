package rice;

import java.util.*;

/**
 * This class serves as a unique identifer for a result which
 * is to be returned at a later tme.
 *
 * @version $Id$
 */
public final class ResultIdentifier  {

  private static Random rng = new Random();
  
  protected long timestamp;

  protected long random;
  
  private ResultIdentifier(long timestamp, long random) {
    this.timestamp = timestamp;
    this.random = random;
  }

  public boolean equals(Object o) {
    if (o instanceof ResultIdentifier) {
      return ((this.timestamp == ((ResultIdentifier)o).timestamp) ||
              (this.random == ((ResultIdentifier)o).random));
    } else {
      return false;
    }
  }

  public static ResultIdentifier generateIdentifier() {
    return new ResultIdentifier(System.currentTimeMillis(), rng.nextLong());
  }

}

