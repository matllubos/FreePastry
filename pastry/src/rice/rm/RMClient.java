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

/**
 * @(#) RMClient.java
 *
 * This interface should be implemented by all applications that interact
 * with the Replica Manager.
 *
 * @version $Id$
 * @author Animesh Nandi
 */
public interface RMClient {

    /* This upcall is invoked by the Replica Manager to notify the application 
     * that it is responsible for the object with this particular objectKey. It
     * is the duty of the application to decide what action to take. For instance,
     * in PAST, the application will need to store the file in it local storage unit.
     *
     * @param objectKey the object key of the object
     * @param object the object
     */
    public void responsible(NodeId objectKey, Object object);




    /* This upcall is invoked by the Replica Manager to notify the application 
     * that it is no longer responsible for the object with this particular
     * objectKey. It is the duty of the application to decide what action to take.
     * For instance, in PAST, the application will need to delete the file from
     * its local storage unit.
     *
     * @param objectKey the object key of the object
     */
    public void notresponsible(NodeId objectKey);



    /* This upcall is invoked by the Replica Manager to notify the application
     * that it should continue to hold the object. If it was not holding the 
     * object then the RMClient should treat this upcall as an implicit notification
     * that it is responsible and so should take steps to get the object. On the 
     * other hand if the RMClient does not get this upcall for a long time(several 
     * Timeperiods after, where it is assumed that the underlying RM layer is ASKED
     * by the RMClient layer to send a  refresh in one timeperiod), then the
     * application can get rid of the object.
     */
    public void refresh(NodeId objectKey);

    

    /* This upcall is simply to denote that the underlying replica manager
     * is ready.
     */
    public void rmIsReady();


}















































































