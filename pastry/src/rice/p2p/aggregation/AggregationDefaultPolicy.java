package rice.p2p.aggregation;

import rice.p2p.past.PastContent;

public class AggregationDefaultPolicy implements AggregationPolicy {

  private static final int aggregateComponentThreshold = 100*1024;

  public boolean shouldBeAggregated(PastContent obj, int size) {
    return (!obj.isMutable() && (size<aggregateComponentThreshold));
  }
    
}

