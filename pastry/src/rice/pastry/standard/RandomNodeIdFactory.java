package rice.pastry.standard;

import rice.environment.Environment;
import rice.environment.logging.Logger;
import rice.environment.random.RandomSource;
import rice.pastry.*;

import java.security.*;

/**
 * Constructs random node ids by SHA'ing consecutive numbers, with random
 * starting value.
 * 
 * @version $Id$
 * 
 * @author Andrew Ladd
 * @author Peter Druschel
 */

public class RandomNodeIdFactory implements NodeIdFactory {
  private long next;

  Environment environment;

  protected Logger logger;
  /**
   * Constructor.
   */

  public RandomNodeIdFactory(Environment env) {
    this.environment = env;
    next = env.getRandomSource().nextLong();
    this.logger = env.getLogManager().getLogger(getClass(), null);
  }

  /**
   * generate a nodeId
   * 
   * @return the new nodeId
   */

  public Id generateNodeId() {

    //byte raw[] = new byte[NodeId.nodeIdBitLength >> 3];
    //rng.nextBytes(raw);

    byte raw[] = new byte[8];
    long tmp = ++next;
    for (int i = 0; i < 8; i++) {
      raw[i] = (byte) (tmp & 0xff);
      tmp >>= 8;
    }

    MessageDigest md = null;
    try {
      md = MessageDigest.getInstance("SHA");
    } catch (NoSuchAlgorithmException e) {
      if (logger.level <= Logger.SEVERE) logger.log(
          "No SHA support!");
      throw new RuntimeException("No SHA support!",e);
    }

    md.update(raw);
    byte[] digest = md.digest();

    Id nodeId = Id.build(digest);

    return nodeId;
  }

}

