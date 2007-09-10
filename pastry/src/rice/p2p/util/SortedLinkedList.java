package rice.p2p.util;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.ListIterator;

public class SortedLinkedList<E extends Comparable> extends LinkedList<E> {

  @Override
  public boolean addAll(Collection<? extends E> c) {
    for (E elt : c) {
      add(elt);
    }
    return true;
  }

  @Override
  public boolean addAll(int index, Collection<? extends E> c) {
    throw new UnsupportedOperationException("Does not guarantee sortedness.");
  }

  @Override
  public void addFirst(E o) {
    throw new UnsupportedOperationException("Does not guarantee sortedness.");
  }

  @Override
  public void addLast(E o) {
    throw new UnsupportedOperationException("Does not guarantee sortedness.");
  }

  @Override
  public ListIterator<E> listIterator(int index) {
    // TODO Auto-generated method stub
    final ListIterator<E> it = super.listIterator(index);
    
    return new ListIterator<E>(){

      public void add(E o) {
        throw new UnsupportedOperationException("Does not guarantee sortedness.");
      }

      public boolean hasNext() {
        return it.hasNext();
      }

      public boolean hasPrevious() {
        return it.hasPrevious();
      }

      public E next() {
        return it.next();
      }

      public int nextIndex() {
        return it.nextIndex();
      }

      public E previous() {
        return it.previous();
      }

      public int previousIndex() {
        return it.previousIndex();
      }

      public void remove() {
        it.remove();
      }

      public void set(E o) {
        throw new UnsupportedOperationException("Does not guarantee sortedness.");
      }    
    };
  }

  @Override
  public E set(int index, E element) {
    throw new UnsupportedOperationException("Does not guarantee sortedness.");
  }

  @Override
  public boolean add(E o) {
    // shortcuts
    if (isEmpty()) {
      super.add(o);
      return true;
    }
    
    if (getFirst().compareTo(o) >= 0) {
      super.addFirst(o);
      return true;
    }
    
    if (getLast().compareTo(o) <= 0) {
      super.addLast(o);
      return true;
    }
    
    ListIterator<E> i = super.listIterator(0);
    E elt;
    while(i.hasNext()) {
      elt = i.next(); 
      int diff = elt.compareTo(o);
      if (diff >= 0) break;
    }
    i.previous();
    i.add(o);
    return true;
  }

}
