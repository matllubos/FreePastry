
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
public class StandardAddress implements Address {

  //serial ver for backward compatibility
  private static final long serialVersionUID = 1564239935633411277L;
  
  protected int myCode;
  
  protected String name;

  public StandardAddress(int port) {
    this.myCode = port;
  }
  
  public StandardAddress(Class c, String instance, Environment env) {
    MessageDigest md = null;
    
    try {
      md = MessageDigest.getInstance("SHA");
    } catch ( NoSuchAlgorithmException e ) {
      Logger logger = env.getLogManager().getLogger(getClass(), null);
      if (logger.level <= Logger.SEVERE) logger.log(
        "No SHA support!" );
    }
    
    name = c.toString() + "-" + instance;

    md.update(name.getBytes());
    byte[] digest = md.digest();

    myCode = (digest[0] << 24) + (digest[1] << 16) +
             (digest[2] << 8) + digest[3];

  }

  public int hashCode() {
    return myCode;
  }

  public boolean equals(Object obj) {
    if (obj instanceof StandardAddress) {
      return ((StandardAddress) obj).myCode == myCode;
    } else {
      return false;
    }
  }
  
  public String toString() {
    return "[StandardAddress: " + name + "]";
  }
}

