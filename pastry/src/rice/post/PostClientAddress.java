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

import rice.p2p.commonapi.rawserialization.*;
import rice.pastry.*;

import java.io.*;

/**
 * This class represents the notion of an address, which
 * uniquely identifies an application running on top of the
 * POST service. This class is designed using the factory
 * pattery, with the getAddress() method as the entrance
 * into the factory.
 * 
 * @version $Id$
 */
public final class PostClientAddress implements Serializable {

  private static final long serialVersionUID = 7140777125939291819L;
  
  protected String name;
  
  /**
   * Constructor
   */
  public PostClientAddress(String name) {
    this.name = name;
  }

  public boolean equals(Object o) {
    if (o instanceof PostClientAddress) {
      return ((PostClientAddress) o).name.equals(name);
    } else {
      return false;
    }
  }

  /**
   * Method by which one can generate a PostClientAddress.  This
   * method will always return the same address given the same
   * PostClient class.
   *
   * @param client The client wanting an address
   * @return A unique address for this class of client
   */
  public static PostClientAddress getAddress(PostClient client) {
    return new PostClientAddress(client.getClass().getName());
  }

  public int hashCode() {
    return name.hashCode();
  }

  public String toString() {
    return "PostClientAddress[" + name + "]";
  }
  
  public PostClientAddress(InputBuffer buf) throws IOException {
    name = buf.readUTF(); 
  }
  
  public void serialize(OutputBuffer buf) throws IOException {
    buf.writeUTF(name); 
  }
}