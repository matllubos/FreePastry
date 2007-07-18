package rice.pastry.direct;

import rice.environment.random.RandomSource;

public interface ProximityGenerator {
  public NodeRecord generateNodeRecord();

  public void setRandom(RandomSource random);
}
