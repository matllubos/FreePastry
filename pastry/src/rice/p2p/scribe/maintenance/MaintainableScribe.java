package rice.p2p.scribe.maintenance;

import java.util.Collection;
import java.util.List;

import rice.p2p.commonapi.Endpoint;
import rice.p2p.commonapi.NodeHandle;
import rice.p2p.scribe.Scribe;
import rice.p2p.scribe.Topic;
import rice.p2p.scribe.Scribe.BaseScribe;

/**
 * This is an interface to scribe so that the MaintenacePolicy 
 * can have additional access to Scribe, that most users will not need.
 * 
 * @author Jeff Hoye
 *
 */
public interface MaintainableScribe extends BaseScribe {
  public static final int MAINTENANCE_ID = Integer.MAX_VALUE;
  
  public Collection<Topic> getTopics();
  public Endpoint getEndpoint();
  
  /**
   * This returns the topics for which the parameter 'parent' is a Scribe tree
   * parent of the local node
   * 
   * @param parent null/localHandle for topics rooted by us
   */
  public Collection<Topic> getTopicsByParent(NodeHandle parent);

  /**
   * This returns the topics for which the parameter 'child' is a Scribe tree
   * child of the local node
   */
  public Collection<Topic> getTopicsByChild(NodeHandle child);
  
  public void subscribe(Collection<Topic> failedTopics);  


}
