package rice.p2p.util.testing;

import rice.p2p.util.*;

public class BloomFilterUnit {
  
  public static void main(String[] args) {
    int k = Integer.parseInt(args[0]);
    int l = Integer.parseInt(args[1]);
    int m = Integer.parseInt(args[2]);
    int n = Integer.parseInt(args[3]);
    
    byte[][] elements = new byte[n][];
    BloomFilter filter = new BloomFilter(k, m);
    
    for (int i=0; i<elements.length; i++) {
      elements[i] = MathUtils.randomBytes(l);
      filter.add(elements[i]);
    }
    
 //   System.out.println(filter.getBitSet());
    
    for (int i=0; i<elements.length; i++) {
      if (! filter.check(elements[i]))
        System.out.println("FAILURE: Element " + i + " did not exist!");
    }
    
    int count = 0;
    
    for (int i=0; i<elements.length; i++) {
      if (filter.check(MathUtils.randomBytes(l)))
        count++;
    }
    
    System.out.println("FALSE POSITIVE RATE: " + count + "/" + elements.length);
  }
}