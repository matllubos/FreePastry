/*
 * Created on May 26, 2005
 */
package rice.environment.random.simple;

import java.net.InetAddress;
import java.util.Random;

import rice.environment.logging.*;
import rice.environment.random.RandomSource;

/**
 * @author Jeff Hoye
 */
public class SimpleRandomSource implements RandomSource {
  Random rnd;
  
  Logger logger;

  String instance;
  
  public SimpleRandomSource(long seed, LogManager manager, String instance) {
    init(seed, manager, instance); 
  }
    
  public SimpleRandomSource(long seed, LogManager manager) {
    this(seed, manager, null); 
  }
    
  public SimpleRandomSource(LogManager manager) {
    this(manager, null);
  }
  
  public SimpleRandomSource(LogManager manager, String instance) {
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
      init(time, manager, instance);
  }
  
  private void init(long seed, LogManager manager, String instance) {
    if (manager != null)
      logger = manager.getLogger(SimpleRandomSource.class, instance);
    if (logger != null) 
      if (logger.level <= Logger.INFO) logger.log("RNG seed = "+seed);
    rnd = new Random(seed);    
  }
  
  public boolean nextBoolean() {
    boolean ret = rnd.nextBoolean();
    if (logger != null) 
      if (logger.level <= Logger.FINER) logger.log("nextBoolean = "+ret);
    return ret;
  }
  
  public void nextBytes(byte[] bytes) {
    rnd.nextBytes(bytes);
    if (logger != null) 
      if (logger.level <= Logger.FINER) logger.log("nextBytes = "+bytes);
  }
  
  public double nextDouble() {
    double ret = rnd.nextDouble();
    if (logger != null) 
      if (logger.level <= Logger.FINER) logger.log("nextDouble = "+ret);
    return ret;
  }
  
  public float nextFloat() {
    float ret = rnd.nextFloat();
    if (logger != null) 
      if (logger.level <= Logger.FINER) logger.log("nextFloat = "+ret);
    return ret;
  }
  
  public double nextGaussian() {
    double ret = rnd.nextGaussian();
    if (logger != null) 
      if (logger.level <= Logger.FINER) logger.log("nextGaussian = "+ret);
    return ret;
  }
  
  public int nextInt() {
    int ret = rnd.nextInt();
    if (logger != null) 
      if (logger.level <= Logger.FINER) logger.log("nextInt = "+ret);
    return ret;
  }
  
  public int nextInt(int max) {
    int ret = rnd.nextInt(max);
    if (logger != null) 
      if (logger.level <= Logger.FINER) logger.log("nextInt = "+ret);
    return ret;
  }
  
  public long nextLong() {
    long ret = rnd.nextLong();
    if (logger != null) 
      if (logger.level <= Logger.FINER) logger.log("nextLong = "+ret);
    return ret;
  }
}
