/*
 * Created on May 26, 2005
 */
package rice.environment.random.simple;

import java.util.Random;

import rice.environment.random.RandomSource;

/**
 * @author Jeff Hoye
 */
public class SimpleRandomSource implements RandomSource {
  Random rnd;
  
  public SimpleRandomSource() {
    rnd = new Random();
  }
  
  public SimpleRandomSource(long seed) {
    rnd = new Random(seed);
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
