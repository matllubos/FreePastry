

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
 *
 * @author Animesh Nandi
 */
public interface RMClient {

    /**
     * This upcall is invoked to notify the application that is should
     * fetch the cooresponding keys in this set, since the node is now
     * responsible for these keys also.
     * @param keySet set containing the keys that needs to be fetched
     */
    public void fetch(IdSet keySet);



    /**
     * This upcall is simply to denote that the underlying replica manager
     * (rm) is ready. The 'rm' should henceforth be used by this RMClient
     * to issue the downcalls on the RM interface.
     * @param rm the instance of the Replica Manager
     */
    public void rmIsReady(RM rm);



    /**
     * This upcall is to notify the application of the range of keys for 
     * which it is responsible. The application might choose to react to 
     * call by calling a scan(complement of this range) to the persistance
     * manager and get the keys for which it is not responsible and
     * call delete on the persistance manager for those objects.
     * @param range the range of keys for which the local node is currently 
     *              responsible  
     */
    public void isResponsible(IdRange range);



    /**
     * This upcall should return the set of keys that the application
     * currently stores in this range. Should return a empty IdSet (not null),
     * in the case that no keys belong to this range.
     * @param range the requested range
     */
    public IdSet scan(IdRange range);

}















































































