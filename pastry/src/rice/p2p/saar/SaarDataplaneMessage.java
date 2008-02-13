package rice.p2p.saar;

//import rice.replay.*;
import rice.pastry.PastryNode;
import rice.*;
import rice.p2p.commonapi.*;
import rice.p2p.scribe.*;
import rice.p2p.scribe.messaging.*;
import java.util.*;

/**
 * @(#) All dataplane specific messages will extend this. This helps us track the overhead separately for the control plane and the data plane
 *
 */
public abstract class SaarDataplaneMessage implements Message {

//    public int dummyfield;

//    public void dump(rice.replay.ReplayBuffer buffer, PastryNode pn) {
//    }
//    
//    public SaarDataplaneMessage(ReplayBuffer buffer, PastryNode pn) {
//	super(buffer,pn);
//    }
  
  // the source of this message
  protected NodeHandle source;

  // the topic of this message
  protected Topic topic;


    protected SaarDataplaneMessage(NodeHandle source, Topic topic) {
      this.source = source;
      this.topic = topic;
    }

    public int getPriority() {
      return MEDIUM_HIGH_PRIORITY;
    }
    
  public NodeHandle getSource() {
    return source;
  }
  
  public Topic getTopic() {
    return topic;
  }

}