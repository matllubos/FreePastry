/*************************************************************************

"FreePastry" Peer-to-Peer Application Development Substrate

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

package rice.past;

import java.io.Serializable;

import rice.*;
import rice.p2p.commonapi.*;

/**
 * A handle class for content-hash objects stored in PAST.
 *
 * @version $Id$
 * @author Peter Druschel
 */

public class ContentHashPASTContentHandle implements PASTContentHandle {

    // the node on which the content object resides
    private NodeHandle storageNode;

    // the object's id
    private Id myId;


    /**
     * Constructor
     *
     * @param id key identifying the object to be inserted
     * @param obj the object to be inserted
     * @param command Command to be performed when the result is received
     */
 
    public ContentHashPASTContentHandle(NodeHandle nh, Id id) {
	storageNode = nh;
	myId = id;
    }


    /*
     * PASTContentHandle methods
     */

    /**
     * get the id of the PASTContent object associated with this handle
     * @return the id
     */

    public Id getId() {return myId;}

    /**
     * get the NodeHandle of the PAST node on which the object associated with this handle is stored
     * @return the id
     */

    public NodeHandle getNode() {return storageNode;}

}










