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
 * A map which keeps a bounded number of the largest keys inserted 
 * to date.
 *
 * @author Andrew Ladd
 */

public class SelectiveMap extends AbstractMap {
    private BoundedHeap theHeap;
    private HashMap keyToHeapElt;
    private HashMap keyToValue;
    private ElementObserver theObs;

    /**
     * Constructor.
     *
     * Largest is in the sense of the natural order of the keys.
     * 
     * @param max the maximum number of elements that go into the set.
     */

    public SelectiveMap(int max)
    {
	theHeap = new BoundedHeap(max);
	keyToHeapElt = new HashMap();
	keyToValue = new HashMap();
	theObs = new ElementObserver();
    }
    
    /**
     * Constructor.
     *
     * @param max the maximum number of elements that go into the set.
     * @param cmp the rule for comparing keys.
     */
    
    public SelectiveMap(int max, Comparator cmp)
    {
	theHeap = new BoundedHeap(max, cmp);
	keyToHeapElt = new HashMap();
	keyToValue = new HashMap();
	theObs = new ElementObserver();
    }

    private class MyEntrySet extends AbstractSet {
	private Set keyToValueEntries;

	public MyEntrySet() {
	    keyToValueEntries = keyToValue.entrySet();
	}
	
	public int size() { return keyToValueEntries.size(); }
	
	private class MyIterator implements Iterator {
	    private Iterator khIter;
	    private Map.Entry last;

	    public MyIterator() 
	    {
		khIter = keyToValueEntries.iterator();
		
		last = null;
	    }
	    
	    public boolean hasNext() { return khIter.hasNext(); }
	    
	    public Object next() { 
		last = (Map.Entry) khIter.next();
		
		return last; 
	    }
	    
	    public void remove() 
	    {
		if (last == null) throw new NoSuchElementException();

		khIter.remove();
		
		Object key = last.getKey();

		if (keyToValue.containsKey(key) == true) return;
		
		Heap.HeapElementInterface hei = (Heap.HeapElementInterface) keyToHeapElt.get(key);
		
		if (hei.inHeap() == true) hei.removeFromHeap();
		
		keyToHeapElt.remove(key);	
		
		khIter.remove();
	    }
	}

	public Iterator iterator() { return new MyIterator(); }
    }

    public void setBound(int max) { theHeap.modifyMaximumElements(max); }

    private class ElementObserver implements Observer {
	public void update(Observable o, Object arg) {
	    Heap.HeapElementInterface hei = (Heap.HeapElementInterface) o;

	    Object key = hei.get();
	    
	    keyToHeapElt.remove(key);
	    while (keyToValue.remove(key) != null);
	}
    }

    public Set entrySet() { return new MyEntrySet(); }

    /**
     * Puts a mapping into the selective map.
     *
     * If a key gets removed then the observer passed in will be notified with
     * an argument containing the key that was removed.  The observer passed in
     * will be notified _before_ the key / value binding is erased in the SelectiveSet.
     *
     * @param key the key in the mapping.
     * @param value the value the key maps to
     * @param obs an observer which will be notified on element removal
     *
     * @return an old binding, if it existed.
     */

    public Object put(Object key, Object value, Observer obs) 
    {
	Object oldValue = keyToValue.put(key, value);
	
	if (keyToHeapElt.containsKey(key) == false) {	    
	    Heap.HeapElementInterface hei = (Heap.HeapElementInterface) theHeap.put(key);
	    
	    if (obs != null) hei.addObserver(obs);
	    
	    if (hei.inHeap() == true) {
		hei.addObserver(theObs);
		keyToHeapElt.put(key, hei);
	    }
	    else {
		while (keyToValue.remove(key) != null);
		
		if (obs != null) obs.update(hei, key);
	    }
	}

	return oldValue;
    }

    public Object put(Object key, Object value) { return put(key, value, null); }

    // other map operations
    
    public boolean containsKey(Object key) {
	return keyToHeapElt.containsKey(key);
    }

    public boolean containsValue(Object value) {
	return keyToValue.containsValue(value);
    }

    public boolean isEmpty() { return keyToValue.isEmpty(); }
    
    public int size() { return keyToValue.size(); }

    public Object get(Object key) 
    {
	return keyToValue.get(key); 
    }
    
    public Object remove(Object key) {
	Object old = keyToValue.remove(key);	
	
	if (keyToValue.containsKey(key) == true) return old;
	
	keyToValue.put(key, old);
	
	Heap.HeapElementInterface hei = (Heap.HeapElementInterface) keyToHeapElt.get(key);
	
	if (hei.inHeap() == true) hei.removeFromHeap();
	
	keyToHeapElt.remove(key);	
	keyToValue.remove(key);
	
	return old;
    }
}



