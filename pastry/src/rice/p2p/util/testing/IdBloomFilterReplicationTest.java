package rice.p2p.util.testing;

import rice.environment.random.RandomSource;
import rice.environment.random.simple.SimpleRandomSource;
import rice.p2p.commonapi.*;
import rice.p2p.multiring.*;
import rice.p2p.past.gc.*;
import rice.pastry.commonapi.*;
import rice.p2p.util.*;
import java.util.*;

public class IdBloomFilterReplicationTest {
  
  public static int NUM = 10000;
  public static int NUM_RUNS = 100;
  
  public static RandomSource random = new SimpleRandomSource();
  public static IdFactory pFactory = new PastryIdFactory();
  public static IdFactory factory = new MultiringIdFactory(pFactory.buildRandomId(random), pFactory);
  public static GCIdFactory gFactory = new GCIdFactory(factory);
  
  public static Id[] SHARED = new Id[NUM];
  public static Id[] EXTRA = new Id[2 * NUM];
  
  public static IdSet remote;
  
  public static void main(String[] args) {
 //   for (int bpk=7; bpk<10; bpk++) {
 //     for (int hash=2; hash<5; hash++) {
 //       IdBloomFilter.NUM_BITS_PER_KEY = bpk;
 //       IdBloomFilter.NUM_HASH_FUNCTIONS = hash;
       doConfig();
 //     }
 //   }
  }

  public static void buildShared() {
    remote = factory.buildIdSet();
    for (int i=0; i<SHARED.length; i++) {
      SHARED[i] = factory.buildRandomId(random);
      remote.addId(SHARED[i]);
    }
    
    for (int i=0; i<EXTRA.length; i++)
      EXTRA[i] = factory.buildRandomId(random);
  }
  
  public static void doConfig() {
    System.out.println();
    System.out.println("BPK: " + IdBloomFilter.NUM_BITS_PER_KEY + "\tHASH: " + IdBloomFilter.NUM_HASH_FUNCTIONS);
    System.out.print("\t\t");
    for (int i=0; i<2 * NUM; i += NUM/10)
      System.out.print(i + "\t");
    
    System.out.println("\n");
    
    for (int i=0; i<NUM; i += NUM/10) {
      System.out.print(i + "\t\t");
      //for (int j=0; j<2*NUM; j += NUM/10) 
        System.out.print(doRun(i, 10000) + "\t");

      System.out.println();
    }
  }
  
  public static double doRun(int has, int extra) {
    int total = 0;
    
    for (int i=0; i<NUM_RUNS; i++) 
      total += run(has, extra);
    
    return ((double) total)/((double)NUM_RUNS);
  }
  
  public static int run(int has, int extra) {
    buildShared();
    IdSet local = factory.buildIdSet();
    
    for (int i=0; i<has; i++)
      local.addId(SHARED[i]);
    
    for (int i=0; i<extra; i++)
      local.addId(EXTRA[i]);
    
    int count = 0;
    int missing = NUM - has;
    
    while (missing > 0) {
      count++;
      IdBloomFilter filter = new IdBloomFilter(local);
      Iterator i = remote.getIterator();
      
      while (i.hasNext()) {
        Id next = (Id) i.next();
        
        if (! filter.check(next)) {
          local.addId(next);
          missing--;
        }
      }
    }
    
    return count;
  }
}
