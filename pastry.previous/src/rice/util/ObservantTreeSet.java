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
 * A TreeSet that has a observer which will remove the element when notified.
 *
 * @author Andrew Ladd
 */

class ObservantTreeSet extends TreeSet {
    private class TreeObserver implements Observer {
	public void update(Observable o, Object arg) {
	    remove(arg);
	}
    }
    
    private TreeObserver tObs;
    
    /**
     * Returns the observer.
     *
     * The observer takes an argument on a notify which is the
     * element to remove from the tree.
     *
     * @return the observer for this tree set.
     */

    public Observer getObserver() { return tObs; }
    
    /**
     * Constructor.
     */

    public ObservantTreeSet() {
	super();
	tObs = new TreeObserver();
    }
    
    /**
     * Constructor.
     *
     * Creates a TreeSet from a Collection.
     *
     * @param c a Collection.
     */

    public ObservantTreeSet(Collection c) {
	super(c);
	tObs = new TreeObserver();
    }
    
    /**
     * Constructor.
     *
     * Creates a TreeSet from a Comparator.
     *
     * @param c a Comparator.
     */

    public ObservantTreeSet(Comparator c) {
	super(c);
	tObs = new TreeObserver();
    }
    
    /**
     * Constructor.
     *
     * Creates a TreeSet from a SortedSet.
     *
     * @param s a SortedSet.
     */

    public ObservantTreeSet(SortedSet s) {
	super(s);
	tObs = new TreeObserver();
    }
}
