package rice.p2p.util.testing;

import rice.environment.random.RandomSource;
import rice.environment.random.simple.SimpleRandomSource;
import rice.p2p.commonapi.*;
import rice.p2p.multiring.*;
import rice.p2p.past.gc.*;
import rice.pastry.commonapi.*;
import rice.p2p.util.*;
import java.util.*;

public class IdBloomFilterUnit {
  
  public static void main(String[] args) {
    int n = Integer.parseInt(args[0]);
    RandomSource random = new SimpleRandomSource();
    PastryIdFactory pFactory = new PastryIdFactory();
    IdFactory factory = new MultiringIdFactory(pFactory.buildRandomId(random), pFactory);
    GCIdFactory gFactory = new GCIdFactory(factory);
    
    IdSet set = gFactory.buildIdSet();
    
    for (int i=0; i<n; i++)
      set.addId(new GCId(factory.buildRandomId(random), System.currentTimeMillis()));
    
    
    System.out.println("Start: " + System.currentTimeMillis());
    IdBloomFilter filter = new IdBloomFilter(set);
    System.out.println("Done1: " + System.currentTimeMillis());
    Iterator i = set.getIterator();
    
    while (i.hasNext()) {
      if (! filter.check((Id) i.next()))
        System.out.println("FAILURE: Element did not exist!");
    }
    System.out.println("Done2: " + System.currentTimeMillis());
    
    int count = 0;
    
    for (int j=0; j<set.numElements(); j++) {
      if (filter.check(new GCId(factory.buildRandomId(random), System.currentTimeMillis())))
        count++;
    }
    System.out.println("Done3: " + System.currentTimeMillis());
    
    System.out.println("FALSE POSITIVE RATE: " + count + "/" + set.numElements());
  }
}
