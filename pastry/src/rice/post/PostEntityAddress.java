package rice.post;

import java.security.*;
import java.io.*;

import rice.p2p.commonapi.*;
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
  protected static Id getId(IdFactory factory, String string) {
    MultiringIdFactory mFactory = (MultiringIdFactory) factory;
    Id ringId = mFactory.getRingId();
    
    if ((string.indexOf("@") > 0) && (string.toLowerCase().trim().endsWith(".epostmail.org"))) {
      String ring = string.substring(string.indexOf("@")+1, string.toLowerCase().indexOf(".epostmail.org"));
      ring = ring.substring(0,1).toUpperCase() + ring.substring(1).toLowerCase();
      
      ringId = mFactory.buildNormalId(ring);
      byte[] ringData = ringId.toByteArray();
      
      for (int i=0; i<ringData.length - MultiringNodeCollection.BASE; i++) 
        ringData[i] = 0;
      
      ringId = mFactory.buildNormalId(ringData);
    } 
    
      MessageDigest md = null;
 
      try {
        md = MessageDigest.getInstance("SHA");
      } catch (NoSuchAlgorithmException e) {
        System.err.println("FATAL ERROR - No SHA support!");
      }

      md.update(string.getBytes());
      return mFactory.buildRingId(ringId, md.digest());
  }
}