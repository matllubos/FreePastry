
package rice.pastry.standard;

import rice.environment.Environment;
import rice.environment.logging.Logger;
import rice.pastry.*;
import rice.pastry.messaging.*;

import java.security.*;

/**
 * Constructs an address for a specific class and instance name.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */
public class StandardAddress {

  //serial ver for backward compatibility
  private static final long serialVersionUID = 1564239935633411277L;
  
  public static int getAddress(Class c, String instance, Environment env) {
    MessageDigest md = null;
    
    try {
      md = MessageDigest.getInstance("SHA");
    } catch ( NoSuchAlgorithmException e ) {
      Logger logger = env.getLogManager().getLogger(StandardAddress.class, null);
      if (logger.level <= Logger.SEVERE) logger.log(
        "No SHA support!" );
    }
    
    String name = c.toString() + "-" + instance;

    md.update(name.getBytes());
    byte[] digest = md.digest();

    int myCode = (digest[0] << 24) + (digest[1] << 16) +
             (digest[2] << 8) + digest[3];

    return myCode;
  }
}

