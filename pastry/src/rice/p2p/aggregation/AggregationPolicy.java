package rice.p2p.aggregation;

import rice.p2p.past.PastContent;

public interface AggregationPolicy {

  public boolean shouldBeAggregated(PastContent obj, int size);
  
}

