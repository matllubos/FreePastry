package rice.past.search;

import java.util.Vector;

/**
 * @(#) Combinator.java
 *
 * Utility class which provides combinations of IndexKeyString arrays.
 *
 * @version $Id$
 * @author Alan Mislove
 */
public class Combinator {

  private IndexKeyString[] _array;
  private int _numObjects;

  /**
   * Creates a combinator out of the elements in
   * the array array.
   *
   * @param array The array to create the Combinator from.
   */
  public Combinator(IndexKeyString[] array) {
    _array = array;
    _numObjects = array.length;
  }

  /**
   * Creates a combinator with all elements of the
   * array array that are not in the array exclude.
   *
   * @param array The array of elements to create the
   *              Combinator from.
   * @param exclude The array of elements to exclude.
   */
  public Combinator(IndexKeyString[] array, IndexKeyString[] exclude) {
    Vector temp = new Vector();

    // walk over array elements, excluding exclude elements
    for (int i=0; i<array.length; i++) {
      IndexKeyString thisKey = array[i];
      boolean excluded = false;

      for (int j=0; j<exclude.length; j++) {
        if (thisKey.equals(exclude[j]))
          excluded = true;
      }

      if (! excluded)
        temp.addElement(thisKey);
    }

    // create new array for non-excluded elements
    _array = new IndexKeyString[temp.size()];

    for (int i=0; i<_array.length; i++) {
      _array[i] = (IndexKeyString) temp.elementAt(i);
    }

    _numObjects = _array.length;
  }

  /**
   * Method which returns a Vector of IndexKey[],
   * representing all of the combinations of the original
   * elements of length size.
   *
   * @param size The size of the combinations.
   * @return Vector of IndexKeyString[], represnting the
   *         combinations.
   */
  public Vector getCombination(int size) {
    return getCombination(size, 0);
  }

  /**
   * Private method which returns a Vector of all
   * combinations of length size of elements after
   * and including element index.
   *
   * @param size The size of the combinations.
   * @param index The first array element to include.
   * @return A Vector of IndexKey[] representing combinations
   *         of size size starting at index index.
   */
  private Vector getCombination(int size, int index) {
    Vector result = new Vector();

    if (size > 0) {
      // for each element, find all sub-combinations
      // starting after this element containing size-1
      // elements
      for (int i=index; i<_numObjects-size+1; i++) {
        Vector thisGroup = getCombination(size - 1, i + 1);

        // append this element to each sub-combination
        for (int j=0; j<thisGroup.size(); j++) {
          IndexKeyString[] thisEntry = (IndexKeyString[]) thisGroup.elementAt(j);
          IndexKeyString[] newEntry = new IndexKeyString[thisEntry.length + 1];

          System.arraycopy(thisEntry, 0, newEntry, 1, thisEntry.length);
          newEntry[0] = _array[i];

          result.addElement(newEntry);
        }
      }
    } else {
      result.add(new IndexKeyString[0]);
    }

    return result;
  }

  /**
   * Method which returns the number of elements in the
   * Combinator.
   *
   * @return The number of elements in the Combinator.
   */
  public int size() {
    return _array.length;
  }

  /**
   * Method which returns the array of elements in the
   * Combinator.
   *
   * @return The number of elements in the Combinator.
   */
  public IndexKeyString[] getKeys() {
    return _array;
  }

  /**
   * Utility method for printing out a Vector of IndexKey[]s.
   *
   * @param result The Vector of IndexKey[]s to print out.
   */
  public String toString() {
    String result = "{";

    for (int j=0; j<_array.length; j++) {
      result += _array[j];

      if (j<_array.length-1)
        result += ", ";
    }

    result += "}";

    return result;
  }



}