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
 * Testing a heap.
 *
 * @author Andrew Ladd
 */

public class HeapUnit {
    private Random rng;
    private Heap theHeap;

    public void genRandomPerm(Object objs[]) {
	int n = objs.length;

	for (int i=0; i<n; i++) {
	    int sw = rng.nextInt();

	    if (sw < 0) sw = -sw;

	    sw = sw % (n - i);

	    Object temp = objs[i];
	    objs[i] = objs[sw];
	    objs[sw] = temp;
	}
    }

    public Integer[] fillIntArray(int n) {
	Integer ints[] = new Integer[n];

	for (int i=0; i<n; i++) ints[i] = new Integer(i);

	return ints;
    }

    public Integer[] heapSort(Integer ints[]) {
	//for (int i=0; i<ints.length; i++)
	//    System.out.print(ints[i] + " ");
	System.out.println("starting insertion...\n");

	for (int i=0; i<ints.length; i++) {
	    theHeap.put(ints[i]);
	    //System.out.println(theHeap);
	    //System.out.println("");
	    //System.out.println("");
	}

	Integer outs[] = new Integer[theHeap.size()];

	System.out.println("\nBeginning extraction...\n");

	for (int i=0; i<outs.length; i++) {
	    outs[i] = (Integer) theHeap.extract();
	    //System.out.println(theHeap);
	    //System.out.println("");
	    //System.out.println("");
	}

	System.out.println("done!");

	return outs;
    }

    public Integer[] integersOfInts(int ints[]) {
	Integer outs[] = new Integer[ints.length];

	for (int i=0; i<ints.length; i++) outs[i] = new Integer(ints[i]);

	return outs;
    }

    public void verifyHeapSort(Integer ints[]) {
	Integer outs[] = heapSort(ints);

	for (int i=0; i<outs.length; i++)
	    if (outs[i].intValue() != i) System.out.println(outs[i].intValue() + " <> " + i);
    }

    public HeapUnit(Heap h) {
	rng = new Random();
	theHeap = h;
    }
    
    public static void main(String args[]) {
	HeapUnit hu = new HeapUnit(new Heap());

	Integer ints[] = hu.fillIntArray(1000000);
	hu.genRandomPerm(ints);

	//int rawInts[] = { 29, 7, 1, 23, 25, 2, 0, 16, 18, 9, 6, 14, 15, 3 };
	//Integer ints[] = hu.integersOfInts(rawInts);
	
	hu.verifyHeapSort(ints);
    }
}
