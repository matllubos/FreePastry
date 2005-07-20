/*
 * Created on May 26, 2005
 */
package rice.environment.random.simple;

import java.net.InetAddress;
import java.util.Random;

import rice.environment.random.RandomSource;

/**
 * @author Jeff Hoye
 */
public class SimpleRandomSource implements RandomSource {
  Random rnd;
  
  public SimpleRandomSource() {
    this(0);
  }
  
  public SimpleRandomSource(long seed) {
    if (seed != 0) {
      rnd = new Random(seed);
    } else {
      // NOTE: Since we are often starting up a bunch of nodes on planetlab
      // at the same time, we need this randomsource to be seeded by more
      // than just the clock, we will include the IP address
      // as amazing as this sounds, it happened in a network of 20 on 7/19/2005
      // also, if you think about it, I was starting all of the nodes at the same 
      // instant, and they had synchronized clocks, if they all started within 1/10th of
      // a second, then there is only 100 different numbers to seed the generator with
      // -Jeff
      long time = System.currentTimeMillis();
      try {
        byte[] foo = InetAddress.getLocalHost().getAddress();
        for (int ctr = 0; ctr < foo.length; ctr++) {
          int i = (int)foo[ctr];
          i <<= (ctr*8);
          time ^= i; 
        }
      } catch (Exception e) {
        // if there is no NIC, screw it, this is really unlikely anyway  
      }          
      rnd = new Random(time); 
    }
  }
  
  public boolean nextBoolean() {
    return rnd.nextBoolean();
  }
  
  public void nextBytes(byte[] bytes) {
    rnd.nextBytes(bytes);
  }
  
  public double nextDouble() {
    return rnd.nextDouble();
  }
  
  public float nextFloat() {
    return rnd.nextFloat();
  }
  
  public double nextGaussian() {
    return rnd.nextGaussian();
  }
  
  public int nextInt() {
    return rnd.nextInt();
  }
  
  public int nextInt(int max) {
    return rnd.nextInt(max);
  }
  
  public long nextLong() {
    return rnd.nextLong();
  }
}
