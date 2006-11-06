/*
 * Created on May 26, 2005
 */
package rice.environment.random;

/**
 * Provides a virtualized random interface for FreePastry.
 * 
 * Usually acquired by calling environment.getRandomSource().
 * 
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
