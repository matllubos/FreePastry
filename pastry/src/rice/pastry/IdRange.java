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

import java.util.*;

/**
 * Represents a contiguous range of Pastry ids.
 * *
 * @version $Id$
 *
 * @author Peter Druschel
 */

public class IdRange {

    private Id left;
    private Id right;

    /**
     * Constructor.
     *
     * @param left the id at the counterclockwise edge of the range (inclusive)
     * @param right the id at the clockwise edge of the range (exclusive)
     */
    public IdRange(Id left, Id right) {
	this.left = left;
	this.right = right;
    }

    /**
     * Copy constructor.
     */
    public IdRange(IdRange o) {
	this.left  = o.left;
	this.right = o.right;
    }

    /**
     * return the size of the range
     * @return the numerical distance of the range
     */ 
    public Id.Distance size() {
	return left.distance(right);
    }


    /**
     * get left edge of range
     * @return the id at the counterclockwise edge of the range (inclusive)
     */ 
    public Id getLeft() {
	return left;
    }

    /**
     * get right edge of range
     * @return the id at the clockwise edge of the range (exclusive)
     */ 
    public Id getRight() {
	return right;
    }

    /**
     * set left edge of range
     * @param left the new id at the counterclockwise edge of the range (inclusive)
     */ 
    public void setLeft(Id left) {
	this.left = left;
    }

    /**
     * set right edge of range
     * @param right the new id at the clockwise edge of the range (exclusive)
     */ 
    public void setRight(Id right) {
	this.right = right;
    }

    /**
     * merge two ranges
     * 
     * @param other the other range
     * @return the result range or null if other doesn't overlap or butt with this range
     */
    public IdRange merge(IdRange other) {
	// XXX -implement
	return null;
    }

    /**
     * intersect two ranges
     * 
     * @param other the other range
     * @return the result range or null if other doesn't overlap with this range
     */
    public IdRange intersect(IdRange other) {
	// XXX -implement
	return null;
    }

}




