package rice.p2p.glacier.v2;

import rice.p2p.glacier.v2.GlacierStatistics;

public interface GlacierStatisticsListener {
  public void receiveStatistics(GlacierStatistics stat);
};
