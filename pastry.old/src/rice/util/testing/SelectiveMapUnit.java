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

package rice.util.testing;

import rice.util.*;
import java.util.*;

/**
 * Testing a selective map.
 *
 * @author Andrew Ladd
 */

public class SelectiveMapUnit {
    private Random rng;
    
    public void testSelectiveMap(int n, int m) {
	HeapUnit hu = new HeapUnit(null);

	Integer ints[] = hu.fillIntArray(n);
	hu.genRandomPerm(ints);
	
	String strings[] = new String[ints.length];

	for (int i=0; i<n; i++) strings[i] = "" + ints[i];

	SelectiveMap sm = new SelectiveMap(m, new ReverseOrder());

	for (int i=0; i<n; i++) sm.put(ints[i], strings[i]);

	boolean foundIt[] = new boolean[m];

	for (int i=0; i<m; i++) foundIt[i] = false;
	
	System.out.println("getting the iterator...");
	
	Iterator iter = sm.entrySet().iterator();

	for (int i=0; i<m; i++) {
	    Map.Entry me = (Map.Entry) iter.next();
	    Integer key = (Integer) me.getKey(); 
	    String val = (String) me.getValue();
	    
	    String keyStr = "" + key;

	    if (keyStr.equals(val) == false) System.out.println(keyStr + " <> " + val);

	    if (foundIt[key.intValue()] == true) System.out.println("found " + key + " twice ");
	    foundIt[key.intValue()] = true;
	}

	if (iter.hasNext() == true) System.out.println("didn't run out of elements!");

	System.out.println("test complete");
    }
    
    public static void main(String args[]) {	
	SelectiveMapUnit smu = new SelectiveMapUnit();
	smu.testSelectiveMap(30000, 50);
    }

}
