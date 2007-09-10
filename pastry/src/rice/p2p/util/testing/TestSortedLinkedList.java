package rice.p2p.util.testing;

import java.util.Random;

import rice.p2p.util.SortedLinkedList;


public class TestSortedLinkedList {

  
  public static void main(String[] args) {
    SortedLinkedList<Integer> foo = new SortedLinkedList<Integer>();
    
    Random r = new Random();
    for (int ctr = 0; ctr < 10; ctr++) {
      int i = r.nextInt(100);
      System.out.println("Adding "+i);
      foo.add(i);
    }
    
    System.out.println(foo);
    int last = foo.getFirst();
    for (int i : foo) {
      if (i < last) throw new RuntimeException(i+"<"+last);
    }
  }
}
