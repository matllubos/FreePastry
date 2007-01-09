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

import java.io.IOException;

import rice.environment.Environment;
import rice.p2p.commonapi.*;
import rice.p2p.commonapi.rawserialization.*;
import rice.p2p.past.*;

/**
 * This class represents the abstract notion of the address
 * of an group of users in the Post system.
 * 
 * @version $Id$
 */
public class PostGroupAddress extends PostEntityAddress {

  public static final short TYPE = 1;
  
  private Id id;
  
  /**
   * Constructor
   */
  public PostGroupAddress(IdFactory factory, String name, Environment env) {
    id = getId(factory, name, env);
  }

  /**
    * Returns the address associated with this useraddress
   *
   * @return The corresponding address
   */
  public Id getAddress() {
    return id;
  }

  public boolean equals(Object o) {
    if (o instanceof PostGroupAddress) {
      PostGroupAddress ua = (PostGroupAddress) o;
      return ua.getAddress().equals(id);
    }

    return false;
  }

  public int hashCode() {
    return id.hashCode();
  }
  
  public PostGroupAddress(InputBuffer buf, Endpoint endpoint) throws IOException {
    id = endpoint.readId(buf, buf.readShort()); 
  }
  
  /**
   * Note that the TYPE is read in PostEntityAddress.build()
   */
  public void serialize(OutputBuffer buf) throws IOException {
    buf.writeShort(id.getType());
    id.serialize(buf); 
  }

  public short getType() {
    return TYPE;
  }
}
