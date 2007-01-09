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
package rice.post.delivery;

import java.io.*;

import rice.*;
import rice.post.*;
import rice.post.messaging.*;
import rice.post.security.*;
import rice.p2p.past.*;
import rice.p2p.past.gc.*;
import rice.p2p.commonapi.*;
import rice.p2p.multiring.*;

/**
 * The delivery metadata stored in Past
 *
 * @version $Id$
 * @author Alan Mislove
 * @author Ansley Post
 * @author Peter Druschel
 */
public class DeliveryMetadata extends GCPastMetadata {
  
  private static final long serialVersionUID = -8357987542721320878L;
  
  protected PostEntityAddress destination;
  
  public DeliveryMetadata(long expiration, PostEntityAddress address) {
    super(expiration);
    this.destination = address;
  }
  
  public PostEntityAddress getDestination() {
    return destination;
  }
  
  public GCPastMetadata setExpiration(long expiration) {
    return new DeliveryMetadata(expiration, destination);
  }
  
  public boolean equals(Object o) {
    return super.equals(o) && ((DeliveryMetadata) o).destination.equals(destination);
  }
  
  public int hashCode() {
    return super.hashCode() ^ destination.hashCode();
  }
}
