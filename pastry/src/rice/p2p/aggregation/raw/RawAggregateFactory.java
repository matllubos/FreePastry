/*
 * Created on Apr 26, 2006
 */
package rice.p2p.aggregation.raw;

import rice.p2p.aggregation.*;
import rice.p2p.commonapi.Id;
import rice.p2p.past.gc.GCPastContent;
import rice.p2p.past.gc.rawserialization.RawGCPastContent;

public class RawAggregateFactory implements AggregateFactory {

  public Aggregate buildAggregate(GCPastContent[] components, Id[] pointers) {
    return new RawAggregate(components, pointers);
  }

  public Aggregate buildAggregate(RawGCPastContent[] components, Id[] pointers) {
    return new RawAggregate(components, pointers);
  }
}
