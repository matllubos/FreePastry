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
 * An abstract class for content-hash objects stored in PAST.
 *
 * content-hash keyed objects are immutable and thus cacheable
 */

public abstract class ContentHashPASTContent implements CacheablePASTContent {

    // the local PAST instance used with this class; to be set by derived classes
    protected transient PAST myPAST;


    /**
     * Inserts this object into its associated PAST instance.
     * Asynchronously returns a boolean as the result to the provided
     * Continuation, indicating whether the insert was successful.
     * 
     * @param id key identifying the object to be inserted
     * @param obj the object to be inserted
     * @param command Command to be performed when the result is received
     */
 
    public void insert(Continuation command) {
	Id myId = getContentHash();
	myPAST.insert(myId, this, command);
    }


    /*
     * PASTContent methods
     */


    /**
     * Checks if a insert operation should be allowed.  Invoked when a
     * PAST node receives an insert request and it is a replica root for
     * the id; invoked on the object to be inserted.  This method
     * determines the effect of an insert operation on an object that
     * already exists: it computes the new value of the stored object,
     * as a function of the new and the existing object.
     * 
     * @param id the key identifying the object
     * @param newObj the new object to be stored
     * @param existingObj the existing object stored on this node (null if no object associated with id is stored on this node)
     * @return null, if the operation is not allowed; else, the new
     * object to be stored on the local node.  
     */

    public PASTContent checkInsert(Id id, PASTContent existingContent) {
	// can't overwrite content hash objects
	if (existingContent != null) return null;

	// only allow correct content hash key
	if (!id.equals(getContentHash())) return null;

	return this;
    }


    /**
     * Checks if a lookup operation should be allowed.  Invoked when a
     * PAST node receives a lookup request and it is a replica root for
     * the id; invoked on the object that is locally stored.
     * 
     * @param id the key identifying the object
     * @param clCred credential object provided by the principal who initiated the operation
     * @return null, if the operation is not allowed; else, the object to be returned to the client 
     */

    public PASTContent checkLookup(Id id, Credential clCred) {
	// always allowed
	return this;
    }


    /**
     * Checks if an exists operation should be allowed.  Invoked when a
     * PAST node receives an insert request and it is a replica root for
     * the id; invoked on the object that is locally stored.
     * 
     * @param id Pastry key identifying the object
     * @param clCred credential object provided by the principal who initiated the operation
     * @return the result returned to the client 
     */

    public boolean checkExists(Id id, Credential clCred) {
	// always allowed
	return this;
    }


    /*
     * CacheablePASTContent methods
     */

    /**
     * Checks if an object should be cached on the local node object.
     * Invoked when a PAST node receives an object and it is NOT a
     * replica root for the id; invoked on the object to be cached.
     * This method determines the effect of an insert operation on a
     * cached object that already exists: it computes the new value of
     * the cached object, as a function of the new and the existing
     * object.
     * 
     * @param id the key identifying the object
     * @param newObj the new object to be cached
     * @param existingObj the existing object cached on this node (null if no object associated with id is cached on this node)
     * @return null, if the operation is not allowed; else, the new
     * object to be cached on the local node.  
     */

    public CacheablePASTContent checkCacheInsert(Id id, CacheablePASTContent existingContent) {
	// can't overwrite content hash objects
	if (existingContent != null) return null;

	// only allow correct content hash key
	if (!id.equals(getContentHash())) return null;

	return this;
    }

    /**
     * Checks if a lookup operation should be allowed of a cached
     * object.  Invoked when a PAST node receives a lookup request, it
     * has the object cached, and it is NOT replica root for the id;
     * invoked on the object that is locally cached.
     * 
     * @param id the key identifying the object
     * @param clCred credential object provided by the principal who initiated the operation
     * @return if null, the request is forwarded towards a replica root; else, the object to be returned to the client 
     */

    public PASTContent checkCacheLookup(Id id, Credential clCred) {
	// always allowed
	return this;
    }

    /**
     * Checks if an exists operation should be allowed of a cache
     * object.  Invoked when a PAST node receives an insert request, it
     * has the object and it is NOT a replica root for the id; invoked
     * on the object that is cached stored.
     * 
     * @param id Pastry key identifying the object
     * @param clCred credential object provided by the principal who initiated the operation
     * @return if true, the result returned to the client; if false, the request is forwarded towards a replica root for the key
     */

    public boolean checkCacheExists(Id id, Credential clCred) {
	// always allowed
	return this;
    }

    /**
     * Invoked by PAST to notify the object that is it about to be evicted from the local cache
     */

    public void notifyEvict() {
	//noop
    }


    /*
     * abstract methods, to be provided by derived classes
     */

    
    /**
     * compute the content hash of this object
     *
     * @return an Id with the content hash of this object
     */
      
    abstract Id getContentHash();

}










