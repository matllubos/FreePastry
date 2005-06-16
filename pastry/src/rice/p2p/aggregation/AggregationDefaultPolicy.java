package rice.p2p.aggregation;

import rice.p2p.past.PastContent;

public class AggregationDefaultPolicy implements AggregationPolicy {

  public boolean shouldBeAggregated(PastContent obj, int size) {
    return true;
  }

}

