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

package rice.pastry.routing;

import rice.pastry.*;
import rice.util.*;

import java.util.*;

/**
 * This is a Comparator which implements a bizarre kind of handle 
 * reflection from SimilarSet from node ids.
 *
 * @author Andrew Ladd
 */

class HandleReflectComparator implements Comparator {
    private SimilarSet theSet;
    private Comparator theCmp;

    /**
     * Constructor.
     *
     * @param cmp a Comparator for node handles.
     */
	
    public HandleReflectComparator(Comparator cmp) { theSet = null; theCmp = cmp; }
    
    /**
     * Sets the SimilarSet which will be used to transform node ids into 
     * node handles.
     *
     * @param ss the SimilarSet.
     */

    public void setSimSet(SimilarSet ss) { theSet = ss; }

    /**
     * Compares two node ids, reflects handles using the SimilarSet and compares
     * those with the given Comparator.
     *
     * @param a an object (a node id).
     * @param b an object (a node id).
     *
     * @return the result of the comparison.
     */

    public int compare(Object a, Object b) 
    {
	NodeId aid = (NodeId) a;
	NodeId bid = (NodeId) b;

	NodeHandle ah = theSet.get(aid);
	NodeHandle bh = theSet.get(bid);

	return theCmp.compare(ah, bh);
    }
}
