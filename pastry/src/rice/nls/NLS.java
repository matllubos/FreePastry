/*
 * @(#) NLS.java
 *
 * @version $Id$
 * @author: Animesh Nandi 
 */

package ObjectWeb.NLS;

import java.net.URL;
import ObjectWeb.Naming.Bindable;
import java.util.Vector;
import ObjectWeb.Security.Credentials;


/**
 * NLS - Interface for the Naming and Lookup Service based on Pastry  
 *
 */
public interface NLS {
    
    /**
     * binds a Bindable object to a name in this Naming and Lookup Service,
     * and makes it visible in the global ObjectWeb namespace.
     *
     * @param name the name of the object
     * @param obj the Bindable object to be bound
     * @param timeout time in minutes, this parameter controls the dynamic caching
     *                behaviour of this object. After 'timeout' time since the 
     *                object was bound, no 'stale' references of the object will remain
     *                in the system. A reference is 'stale' if the object was actually
     *                unbound from the system before the expiry of 'timeout'. 
     * @param credentials the security credentials of the binder
     * @return true if bound successfully, false otherwise
     */ 
    public boolean bind(URL name, Bindable obj, long timeout, Credentials credentials);


  
    /**
     * looks up a nearby Bindable object with the given name 
     * in ObjectWeb's namespace. The Bindable object returned could be 'stale'. 
     *
     * @param name the name of the object to look for
     * @return the Bindable object, or null if the lookup fails
     */ 
    public Bindable lookup(URL name);



    /**
     * looks up a subset of all Bindable objects with the given name 
     * in ObjectWeb's namespace. Note that this subset contains with high
     * probability those Bindable objects that are nearby. The Bindable objects
     * returned could be 'stale'.
     *   
     * @param name the name of the object to look for
     * @return a Vector of Bindable objects, or null if the loopkup fails
     */   
    public Vector lookupSet(URL name);



    /**
     * unbinds a Bindable object from the namespace, the unbind fails if the
     * Bindable object was not earlier bound from this local node or if the 
     * credentials do not match.
     *
     * @param name the name of the object
     * @param credentials the caller's security credentials
     * @return true if unbound successfully, false otherwise
     */
    public boolean unbind(Bindable obj, Credentials credentials);


    /**
     * looks up a the nearby Bindable object with the given name 
     * in ObjectWeb's namespace with the additional gurantee that the Bindable
     * object returned is 'fresh'.
     * 
     * @param name the name of the object to look for
     * @return the Bindable object, or null if the retry fails
     */ 
    public Bindable retry(URL name);



    /**
     * looks up a subset of all Bindable objects with the given name 
     * in ObjectWeb's namespace with the additional guaranteee that the Bindable
     * objects are 'fresh'. Note that this subset contains with high
     * probability those Bindable objects that are nearby.
     *
     * @param name the name of the object to look for
     * @return a Vector of Bindable objects, null if retry fails
     */   
    public Vector retrySet(URL name);    


}







