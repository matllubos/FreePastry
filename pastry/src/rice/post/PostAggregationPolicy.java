package rice.post;

import rice.post.storage.SignedData;
import rice.p2p.past.PastContent;
import rice.p2p.aggregation.AggregationPolicy;

public class PostAggregationPolicy implements AggregationPolicy {

  public boolean shouldBeAggregated(PastContent obj, int size) {
    return !(obj instanceof SignedData);
  }
  
}

