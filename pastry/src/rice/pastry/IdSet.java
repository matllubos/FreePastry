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

package rice.pastry;

import java.io.Serializable;
import java.util.*;
import java.security.*;

/**
 * Represents a set of Pastry ids.
 * *
 * @version $Id$
 *
 * @author Peter Druschel
 */

public class IdSet implements Serializable {

    private SortedSet idSet;

    // a cache of the fingerprint hash
    private Id cachedHash;
    private boolean validHash;

    /**
     * Constructor.
     */
    public IdSet() {
	idSet = new TreeSet();
	validHash = false;
    }

    /**
     * Constructor.
     */
    protected IdSet(SortedSet s) {
	idSet = s;
	validHash = false;
    }

    /**
     * Copy constructor.
     */
    public IdSet(IdSet o) {
	idSet = new TreeSet(idSet);
	cachedHash = o.cachedHash;
	validHash = o.validHash;
    }

    /**
     * return the number of elements
     */ 
    public int numElements() {
	return idSet.size();
    }

    /**
     * add a member
     * @param id the id to add
     */ 
    public void addMember(Id id) {
	idSet.add(id);
	validHash = false;
    }

    /**
     * remove a member
     * @param id the id to remove
     */ 
    public void removeMember(Id id) {
	idSet.remove(id);
	validHash = false;
    }

    /**
     * test membership
     * @param id the id to test
     * @return true of id is a member, false otherwise
     */ 
    public boolean isMember(Id id) {
	return idSet.contains(id);
    }

    /**
     * return the smallest member id
     * @return the smallest id in the set
     */ 
    public Id minMember() {
	return (Id) idSet.first();
    }

    /**
     * return the largest member id
     * @return the largest id in the set
     */ 
    public Id maxMember() {
	return (Id) idSet.last();
    }

    /**
     * return a subset of this set, consisting of the member ids in a given range
     * @param from the lower end of the range (inclusive)
     * @param to the upper end of the range (exclusive)
     * @return the subset
     */ 
    public IdSet subSet(Id from, Id to) {
	IdSet res = new IdSet(idSet.subSet(from, to));
	return res;
    }

    /**
     * return an iterator over the elements of this set
     * @return the interator
     */
    public Iterator getIterator() {
	return idSet.iterator();
    }

    /**
     * compute a fingerprint of the members in this IdSet
     *
     * @return an Id containing the secure hash of this set
     */

    public Id getHash() {

	if (validHash) return cachedHash;

	// recompute the hash
	MessageDigest md = null;
	try {
	    md = MessageDigest.getInstance("SHA");
	} catch ( NoSuchAlgorithmException e ) {
	    System.err.println( "No SHA support!" );
	    return null;
	}

	Iterator it = idSet.iterator();
	byte[] raw = new byte[Id.IdBitLength / 8];
	Id id;

	while (it.hasNext()) {
	    id = (Id) it.next();
	    id.blit(raw);
	    md.update(raw);
	}

	byte[] digest = md.digest();
	cachedHash = new Id(digest);
	validHash = true;

	return cachedHash;
    }

    
    
    /**
     * Returns a string representation of the IdSet.
     */

    public String toString() 
    {
	Iterator it = getIterator();
	Id key;
	String s = "[ IdSet: ";
	while(it.hasNext()) {
	    key = (Id)it.next();
	    s = s + key + ",";

	}
	s = s + " ]";
	return s;
    }
    

}
