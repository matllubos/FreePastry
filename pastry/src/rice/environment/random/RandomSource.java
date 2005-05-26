/*
 * Created on May 26, 2005
 */
package rice.environment.random;

/**
 * @author Jeff Hoye
 */
public interface RandomSource {
  public boolean nextBoolean();
  public void nextBytes(byte[] bytes);
  public double nextDouble();
  public float nextFloat();
  public double nextGaussian();
  public int nextInt();
  public int nextInt(int max);
  public long nextLong();
}
