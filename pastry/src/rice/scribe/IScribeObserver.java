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


package rice.scribe;

import java.util.Set;
import java.lang.Object;

/**
 * @(#) IScribeObserver.java
 *
 * This interface should be implemented by applications which want to
 * be notified when the local node becomes a "forwarder" for a topic, i.e.
 * there is no local application subscribed to this topic already.
 *
 * This interface provides the observer pattern for applications built
 * on top of Scribe Layer. 
 *
 * Whenever a topic is created (referring to Topic data structure that is created,
 * and not the Scribe's create method) implicitly inside
 * the scribe layer (when a node becomes an intermediate node for a topic)
 * the Scribe object will inform all observers which implement this interface.
 * Application then can take appropriate action depending on the functionality
 * of the application.
 *
 * @version $Id$
 * @author Atul Singh
 */

public interface IScribeObserver
{
    /**
     * Method called by underlying scribe layer whenever a topic
     * data structure is created implicitly.
     * 
     * @param obj The object associated with the topic creation event,
     *            here it is the topicId of the topic created.
     */
    public void update(Object obj);
   
}








































