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
 * A generic implementation of a minimizing binary heap.
 *
 * @author Andrew Ladd
 */

public class Heap 
{
    private class HeapElement extends HeapElementInterface implements Comparable
    {
	private Object theElement;
	private int heapPosition;
	
	public HeapElement(Object elt)
	{
	    theElement = elt;
	    heapPosition = -1;
	}
	
	public boolean inHeap() { return heapPosition != -1; }

	public Object get() { return theElement; }

	public void setHeapPosition(int hp) 
	{ 
	    heapPosition = hp; 
	    if (hp == -1) {
		setChanged();
		notifyObservers(theElement);
	    }
	}

	public void removeFromHeap()
	{  
	    if (heapPosition != -1) {
		setChanged();
		remove(heapPosition); 
		heapPosition = -1;
		notifyObservers(theElement);
	    }
	}
	
	public void update(Object heapElt) 
	{ 
	    theElement = heapElt;
	    
	    if (heapPosition != -1) {
		remove(heapPosition);
		HeapElement he = (HeapElement) put(theElement);
		heapPosition = he.heapPosition;
	    }
	    else {
		setChanged();
		notifyObservers(theElement);
	    }
	}

	public int compareTo(Object obj) 
	{
	    HeapElement he = (HeapElement) obj;

	    if (theCmp == null) {
		Comparable ac = (Comparable) theElement;
		Comparable bc = (Comparable) he.theElement;

		return ac.compareTo(bc);
	    }
	    else
		return theCmp.compare(theElement, he.theElement);
	}

	public int hashCode() { return theElement.hashCode(); }
	
	public String toString() { return theElement.toString(); }
    }
    
    /**
     * A handle and interface to an element inserted into a heap.  It will
     * notify its observers when the element is removed from the heap or when
     * the element is updated.  The argument passed will be the element.
     *
     * @author Andrew Ladd
     */

    public abstract class HeapElementInterface extends Observable
    {
	/**
	 * Asks if the element is still a member of the heap.
	 *
	 * @return true if the element is in the heap, false otherwise.
	 */
	
	public abstract boolean inHeap();

	/**
	 * Accessor for the element.
	 *
	 * @return the element.
	 */
	
	public abstract Object get();

	/**
	 * Removes the element from the heap.
	 */
	
	public abstract void removeFromHeap();

	/**
	 * Updates element which is already in the heap.
	 *
	 * @param heapElt the new element.
	 */
	
	public abstract void update(Object heapElt);
    }
    
    
    private HeapElement elements[];
    private int mySize;
    private Comparator theCmp;

    private final static int initialSize = 2;

    /**
     * Constructor.
     *
     * Constructs an empty maximizing heap.
     */

    public Heap() 
    {
	elements = new HeapElement[initialSize];
	mySize = 0;
	theCmp = null;
    }

    /**
     * Constructor.
     *
     * Constructs an empty heap.
     *
     * @param isMaximizing true for a maximizing heap, false for a minimizing heap.
     */

    public Heap(Comparator cmp) 
    {
	elements = new HeapElement[initialSize];
	mySize = 0;
	theCmp = cmp;
    }
    
    private void resize() 
    {
	if (mySize == elements.length) {  // double the memory usage.
	    HeapElement temp[] = new HeapElement[elements.length * 2];
	    
	    for (int i=0; i<mySize; i++) temp[i] = elements[i];

	    elements = temp;
	}
	else if (elements.length > initialSize && mySize < elements.length / 4) { // halve the memory usage
	    HeapElement temp[] = new HeapElement[elements.length / 2];

	    for (int i=0; i<mySize; i++) temp[i] = elements[i];

	    elements = temp;
	}
    }

    private int parent(int i) { return (i - 1) / 2; }
    private int left(int i) { return 2 * i + 1; }
    private int right(int i) { return 2 * i + 2; }
    
    private boolean isBetter(int i, int j) 
    {
	Object a = elements[i].get();
	Object b = elements[j].get();

	if (theCmp == null) {
	    Comparable ac = (Comparable) a;
	    Comparable bc = (Comparable) b;

	    //System.out.println(ac + " cmp " + bc + " " + ac.compareTo(bc));
	    
	    if (ac.compareTo(bc) < 0) return true;
	    else return false;
	}
	else
	    if (theCmp.compare(a, b) < 0) return true;
	    else return false;
    }


    private int selectBest(int i) 
    { 
	int l = left(i);
	int r = right(i);

	int m = i;
	
	if (l < mySize && isBetter(l, m) == true) m = l;
	if (r < mySize && isBetter(r, m) == true) m = r;
	
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

    public HeapElementInterface put(Object elt)
    {
	HeapElement he = new HeapElement(elt);

	resize();
	
	elements[mySize] = he;
	he.setHeapPosition(mySize);
      
	int i = mySize;
	mySize ++;
	
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
    
    protected void remove(int index) 
    {
	if (index >= 0 && index < mySize) {
	    HeapElement he = elements[index];

	    swap(index, mySize - 1);
	    mySize--;

	    he.setHeapPosition(-1);

	    if (mySize == 0) return;

	    int i = index;

	    while (true) {
		int best = selectBest(i);

		if (best == i) return;
		
		swap(i, best);

		i = best;
	    }
	}
	else throw new NoSuchElementException("tried to remove an element from a heap with a bad address");
    }

    /**
     * Returns the number of elements inserted into the heap.
     *
     * @return the size.
     */

    public int size() { return mySize; }

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
    
    public Object extract() 
    {
	HeapElement he = elements[0];
	
	remove(0);

	return he.get();
    }

    private class MyIterator implements Iterator {
	private int index;
	
	public MyIterator() 
	{ 
	    index = 0; 
	}

	public boolean hasNext() 
	{
	    if (index < mySize) return true;
	    else return false;
	}

	public Object next() 
	{
	    if (index == mySize) return null;

	    return elements[index++];
	}

	public void remove() 
	{
	    if (index == 0) throw new NoSuchElementException();
	    
	    HeapElement he = elements[index - 1];

	    he.removeFromHeap();
	}
    }
    
    /**
     * Gets an iterator for the heap.
     *
     * @return the iterator.
     */

    public Iterator iterator() 
    {
	return new MyIterator();
    }

    private String whitespace(int n) {
	String s = "";

	for (int i=0; i<n; i++) s+=" ";

	return s;
    }
    
    public String toString() {
	String s = "";

	int width = 128;
	
	Iterator iter = iterator();

	int depth = 1;
	int count = 0;
	String space = whitespace(width / 2);
	
	while (iter.hasNext() == true) {
	    String it = iter.next().toString();

	    space = whitespace(width / (depth + 1) - it.length());

	    s += space + it;
	    
	    count ++;

	    if (count == depth) {
		s += "\n";
		
		if (iter.hasNext()) {
		    s+=space + "|";
		    for (int i=1; i<depth; i++) s += whitespace(width/(depth + 1) - 1) + "|";
		    s+="\n";
		    
		    depth *= 2;
		    count = 0;

		    space = whitespace(width / (depth + 1));
		    
		    int n = width / (depth + 1);

		    for (int j=0; j<n; j++) s += " ";

		    for (int i=0; i<depth/2; i++) {
			for (int j=0; j<n; j++) s += "-";
			s+="-";
			for (int j=0; j<n - 1; j++) s += " ";
		    }
		    
		    s+="\n";
		    
		    s+=space + "|";
		    for (int i=1; i<depth; i++) s += whitespace(width/(depth + 1) - 1) + "|";
		    s+="\n";
		}		
	    }
	}

	return s;
    }
}



