//////////////////////////////////////////////////////////////////////////////
// Rice Open Source Pastry Implementation                  //               //
//                                                         //  R I C E      //
// Copyright (c)                                           //               //
// Romer Gil                   rgil@cs.rice.edu            //   UNIVERSITY  //
// Andrew Ladd                 aladd@cs.rice.edu           //               //
// Tsuen Wan Ngan              twngan@cs.rice.edu          ///////////////////
//                                                                          //
// This program is free software; you can redistribute it and/or            //
// modify it under the terms of the GNU General Public License              //
// as published by the Free Software Foundation; either version 2           //
// of the License, or (at your option) any later version.                   //
//                                                                          //
// This program is distributed in the hope that it will be useful,          //
// but WITHOUT ANY WARRANTY; without even the implied warranty of           //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the            //
// GNU General Public License for more details.                             //
//                                                                          //
// You should have received a copy of the GNU General Public License        //
// along with this program; if not, write to the Free Software              //
// Foundation, Inc., 59 Temple Place - Suite 330,                           //
// Boston, MA  02111-1307, USA.                                             //
//                                                                          //
// This license has been added in concordance with the developer rights     //
// for non-commercial and research distribution granted by Rice University  //
// software and patent policy 333-99.  This notice may not be removed.      //
//////////////////////////////////////////////////////////////////////////////

package rice.util;

import java.util.*;

/**
 * This object reverses a given ordering on some set of objects.
 *
 * @author Andrew Ladd
 */

public class ReverseOrder implements Comparator
{
    private Comparator theCmp;

    /**
     * Constructor.
     *
     * Reverses the natural of Comparable objects.
     */
    
    public ReverseOrder() 
    {
	theCmp = null;
    }

    /**
     * Constructor.
     *
     * Reverses the order of the given comparator.
     *
     * @param cmp the comparator to reverse order of. 
     */

    public ReverseOrder(Comparator cmp)
    {
	theCmp = cmp;
    }
    
    public int compare(Object a, Object b) 
    {
	if (theCmp == null) {
	    Comparable ac = (Comparable) a;
	    Comparable bc = (Comparable) b;
	    
	    return - ac.compareTo(bc);
	}
	else return - theCmp.compare(a, b);
    }
    
    public boolean equals(Object a, Object b)
    {
	return a.equals(b);
    }
}
