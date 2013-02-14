/*******************************************************************************

"FreePastry" Peer-to-Peer Application Development Substrate

Copyright 2002-2007, Rice University. Copyright 2006-2007, Max Planck Institute 
for Software Systems.  All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

- Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

- Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

- Neither the name of Rice  University (RICE), Max Planck Institute for Software 
Systems (MPI-SWS) nor the names of its contributors may be used to endorse or 
promote products derived from this software without specific prior written 
permission.

This software is provided by RICE, MPI-SWS and the contributors on an "as is" 
basis, without any representations or warranties of any kind, express or implied 
including, but not limited to, representations or warranties of 
non-infringement, merchantability or fitness for a particular purpose. In no 
event shall RICE, MPI-SWS or contributors be liable for any direct, indirect, 
incidental, special, exemplary, or consequential damages (including, but not 
limited to, procurement of substitute goods or services; loss of use, data, or 
profits; or business interruption) however caused and on any theory of 
liability, whether in contract, strict liability, or tort (including negligence
or otherwise) arising in any way out of the use of this software, even if 
advised of the possibility of such damage.

*******************************************************************************/ 
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
