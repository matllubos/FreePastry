/*************************************************************************

"Free Pastry" Peer-to-Peer Application Development Substrate 

Copyright 2002, Rice University. All rights reserved. 

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

- Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

- Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

- Neither  the name  of Rice  University (RICE) nor  the names  of its
contributors may be  used to endorse or promote  products derived from
this software without specific prior written permission.

This software is provided by RICE and the contributors on an "as is"
basis, without any representations or warranties of any kind, express
or implied including, but not limited to, representations or
warranties of non-infringement, merchantability or fitness for a
particular purpose. In no event shall RICE or contributors be liable
for any direct, indirect, incidental, special, exemplary, or
consequential damages (including, but not limited to, procurement of
substitute goods or services; loss of use, data, or profits; or
business interruption) however caused and on any theory of liability,
whether in contract, strict liability, or tort (including negligence
or otherwise) arising in any way out of the use of this software, even
if advised of the possibility of such damage.

********************************************************************************/


package rice.rm;

import rice.pastry.*;
import rice.pastry.messaging.*;
import rice.pastry.security.*;
import rice.pastry.routing.*;

/**
 * @(#) RM.java
 *
 * This interface is exported by Replica Manager for any applications which need to 
 * replicate objects across k closest nodes in the NodeId space.
 *
 * @version $Id$
 * @author Animesh Nandi
 */
public interface RM {


    public void registerKey(Id key);

    /**
     * Called by client to enable optimizations to route to the nearest 
     * replica. Should be called by client in the context of the forward 
     * method with a lookup message. Should only be called if the local client
     * does not have the desired object. This call may change the nextHop 
     * field in the RouteMessage. 
     */
    public void lookupForward(RouteMessage msg);



}








