package rice.post;

import java.security.*;
import java.io.*;

import rice.environment.Environment;
import rice.environment.logging.Logger;
import rice.p2p.commonapi.*;
import rice.p2p.commonapi.rawserialization.*;
import rice.p2p.multiring.*;

/**
 * This class represents the abstract notion of the address
 * of an identity in the Post system.  This class is designed
 * to be extended have address for both Post users and groups
 * of users.
 * 
 * @version $Id$
 */
public abstract class PostEntityAddress implements Serializable {

  	static final long serialVersionUID = -7860373480614864296L;
  
  /**
   * Constructor
   */
  public PostEntityAddress() {
  }
    
  /**
   * @return The NodeId which this address maps to.
   */
  public abstract Id getAddress();

  /**
  * Utility method for creating the nodeId associated with a
   * specific string.
   *
   * @param string The string
   * @returns The corresponding nodeId.
   */
  protected static Id getId(IdFactory factory, String string, Environment env) {
    MultiringIdFactory mFactory = (MultiringIdFactory) factory;
    Id ringId = mFactory.getRingId();
    
    if ((string.indexOf("@") > 0) && (string.toLowerCase().trim().endsWith(".epostmail.org"))) {
      String ring = string.substring(string.indexOf("@")+1, string.toLowerCase().indexOf(".epostmail.org"));
      ring = ring.substring(0,1).toUpperCase() + ring.substring(1).toLowerCase();
      
      ringId = mFactory.buildNormalId(ring);
      byte[] ringData = ringId.toByteArray();
      
      for (int i=0; i<ringData.length - env.getParameters().getInt("p2p_multiring_base"); i++) 
        ringData[i] = 0;
      
      ringId = mFactory.buildNormalId(ringData);
    } 
    
      MessageDigest md = null;
 
      try {
        md = MessageDigest.getInstance("SHA");
      } catch (NoSuchAlgorithmException e) {
        Logger logger = env.getLogManager().getLogger(PostEntityAddress.class, null);
        if (logger.level <= Logger.SEVERE) logger.log("FATAL ERROR - No SHA support!");
      }

      md.update(string.getBytes());
      return mFactory.buildRingId(ringId, md.digest());
  }
  
  public abstract short getType();
  public abstract void serialize(OutputBuffer buf) throws IOException;
  
  public static PostEntityAddress build(InputBuffer buf, Endpoint endpoint, short type) throws IOException {
    switch(type) {
      case PostUserAddress.TYPE:
        return new PostUserAddress(buf, endpoint);
      case PostGroupAddress.TYPE:
        return new PostGroupAddress(buf, endpoint);
    }
    throw new RuntimeException("Unknown type:"+type);
  }
  
}
