package rice.pastry;

import java.util.*;

/**
 * @(#) PastrySeed.java
 * 
 * The static methods in this class help to SET and GET the value of the seed
 * that will be used by all instances of random number generators. Setting the
 * value of the seed at the start of the simulation helps in producing
 * deterministic results which will help in debugging applications written on
 * Pastry. By default, the GET method returns an arbitrary integer as the seed
 * unless the SET method has been previously invoked.
 * 
 * @version $Id$
 * 
 * @author Animesh Nandi
 * @author Atul Singh
 */

public class PastrySeed {

  private static boolean deterministic = false; // default value;

  private static int seedValue = 0; // default value;

  public static int getSeed() {

    int seed;

    if (deterministic)
      return seedValue;
    else
      return (int) System.currentTimeMillis();
  }

  public static void setSeed(int value) {
    // Sets the deterministic flag to true, so that subsequent getSeed()
    // will return this value;
    deterministic = true;
    seedValue = value;
    return;
  }

}

