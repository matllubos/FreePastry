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

import java.lang.*;

/**
 * A generic implementation of a binary heap.
 *
 * @author Andrew Ladd
 */

public class Heap 
{
    private class HeapElement implements Comparable, HeapElementInterface
    {
	private Comparable theElement;
	private int heapPosition;
	
	public HeapElement(Comparable elt)
	{
	    theElement = elt;
	    heapPosition = -1;
	}
	
	public boolean inHeap() { return heapPosition == -1; }

	public Comparable getElement() { return theElement; }

	public void setHeapPosition(int hp) { heapPosition = hp; }

	public void removeFromHeap()
	{  
	    if (heapPosition != -1) {
		removeElement(heapPosition); 
		heapPosition = -1;
	    }
	}
	
	public void updateElement(Comparable heapElt) 
	{ 
	    theElement = heapElt;
	    
	    if (heapPosition != -1) {
		removeElement(heapPosition);
		HeapElement he = (HeapElement) insertElement(theElement);
		heapPosition = he.heapPosition;
	    }
	}

	public int compareTo(Object obj) 
	{
	    HeapElement he = (HeapElement) obj;

	    return theElement.compareTo(he.theElement);
	}
    }
    
    /**
     * A handle and interface to an element inserted into a heap.
     *
     * @author Andrew Ladd
     */

    public interface HeapElementInterface 
    {
	/**
	 * Asks if the element is still a member of the heap.
	 *
	 * @return true if the element is in the heap, false otherwise.
	 */
	
	public boolean inHeap();

	/**
	 * Accessor for the element.
	 *
	 * @return the element.
	 */
	
	public Comparable getElement();

	/**
	 * Removes the element from the heap.
	 */
	
	public void removeFromHeap();

	/**
	 * Updates element which is already in the heap.
	 *
	 * @param heapElt the new element.
	 */
	
	public void updateElement(Comparable heapElt);
    }
    
    
    private HeapElement elements[];
    private int size;
    private boolean isMaximizing;

    private final static int initialSize = 2;

    /**
     * Constructor.
     *
     * Constructs an empty maximizing heap.
     */

    public Heap() 
    {
	elements = new HeapElement[initialSize];
	size = 0;
	isMaximizing = true;
    }

    /**
     * Constructor.
     *
     * Constructs an empty heap.
     *
     * @param isMaximizing true for a maximizing heap, false for a minimizing heap.
     */

    public Heap(boolean isMaximizing) 
    {
	elements = new HeapElement[initialSize];
	size = 0;
	this.isMaximizing = isMaximizing;
    }
    
    private void resize() 
    {
	if (size == elements.length) {  // double the memory usage.
	    HeapElement temp[] = new HeapElement[elements.length * 2];
	    
	    for (int i=0; i<size; i++) temp[i] = elements[i];

	    elements = temp;
	}
	else if (elements.length > initialSize && size < elements.length / 4) { // halve the memory usage
	    HeapElement temp[] = new HeapElement[elements.length / 2];

	    for (int i=0; i<size; i++) temp[i] = elements[i];

	    elements = temp;
	}
    }

    private int parent(int i) { return i / 2; }
    private int left(int i) { return 2 * i + 1; }
    private int right(int i) { return 2 * i + 2; }
    
    private boolean isBetter(int i, int j) 
    {
	Comparable a = elements[i].getElement();
	Comparable b = elements[i].getElement();

	if (isMaximizing == true)
	    if (a.compareTo(b) > 0) return true;
	    else return false;
	else
	    if (a.compareTo(b) < 0) return true;
	    else return false;	
    }


    private int selectBest(int i) 
    { 
	int l = left(i);
	int r = right(i);

	int m = i;
	
	if (l < size && isBetter(l, m) == true) m = l;
	if (r < size && isBetter(r, m) == true) m = r;
	
	return m;
    }

    private boolean promote(int i) 
    {
	if (i == 0) return false;
	
	int p = parent(i);
	
	if (isBetter(i, p) == true) return true;
	else return false;
    }

    private void swap(int i, int j)
    {
	HeapElement a = elements[i];
	HeapElement b = elements[j];
	
	a.setHeapPosition(j);
	b.setHeapPosition(i);
	
	elements[i] = b;
	elements[j] = a;
    }

    /**
     * Insert an element into the heap.
     *
     * @param elt the element to add to the heap.
     *
     * @return an interface to the element in the heap.
     */

    public HeapElementInterface insertElement(Comparable elt)
    {
	HeapElement he = new HeapElement(elt);

	resize();
	
	elements[size] = he;
	he.setHeapPosition(size);
      
	int i = size;
	size ++;
	
	while (promote(i) == true) {
	    swap(i, parent(i));
	    i = parent(i);
	}

	return (HeapElementInterface) he;
    }
    
    /**
     * Remove an element from the heap.
     *
     * @param index the index of element to remove.
     */
    
    protected void removeElement(int index) 
    {
	if (index >= 0 && index < size) {
	    HeapElement he = elements[index];

	    swap(index, size - 1);
	    size--;

	    if (size == 0) return;

	    int i = index;

	    while (true) {
		int best = selectBest(i);

		if (best == i) return;
		
		swap(i, best);

		i = best;
	    }
	}
	else throw new Error("tried to remove an element from a heap with a bad address");
    }

    /**
     * Returns the number of elements inserted into the heap.
     *
     * @return the size.
     */

    public int getSize() { return size; }

    /**
     * Checks the top element.
     *
     * @return the top element of the heap.
     */
    
    public HeapElementInterface top() 
    {
	HeapElement he = elements[0];

	return (HeapElementInterface) he;
    }
    
    /**
     * Extracts the top element and removes it.
     */
    
    public void extract() 
    {
	HeapElement he = elements[0];
	
	removeElement(0);
    }

    /**
     * Asks the heap if its maximizing.
     *
     * @return true if maximizing, false otherwise.
     */
    
    boolean isHeapMaximizing() { return isMaximizing; }
}



