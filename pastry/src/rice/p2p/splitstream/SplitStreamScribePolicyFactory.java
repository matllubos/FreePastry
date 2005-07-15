/*
 * Created on Jul 15, 2005
 */
package rice.p2p.splitstream;

import rice.p2p.scribe.*;

/**
 * @author Jeff Hoye
 */
public interface SplitStreamScribePolicyFactory {
  public ScribePolicy getSplitStreamScribePolicy(Scribe scribe, SplitStream splitstream);
}
