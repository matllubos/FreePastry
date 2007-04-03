/*******************************************************************************

"FreePastry" Peer-to-Peer Application Development Substrate

Copyright 2002-2007, Rice University. Copyright 2006-2007, Max Planck Institute 
for Software Systems.  All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

- Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

- Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

- Neither the name of Rice  University (RICE), Max Planck Institute for Software 
Systems (MPI-SWS) nor the names of its contributors may be used to endorse or 
promote products derived from this software without specific prior written 
permission.

This software is provided by RICE, MPI-SWS and the contributors on an "as is" 
basis, without any representations or warranties of any kind, express or implied 
including, but not limited to, representations or warranties of 
non-infringement, merchantability or fitness for a particular purpose. In no 
event shall RICE, MPI-SWS or contributors be liable for any direct, indirect, 
incidental, special, exemplary, or consequential damages (including, but not 
limited to, procurement of substitute goods or services; loss of use, data, or 
profits; or business interruption) however caused and on any theory of 
liability, whether in contract, strict liability, or tort (including negligence
or otherwise) arising in any way out of the use of this software, even if 
advised of the possibility of such damage.

*******************************************************************************/ 

package rice.p2p.scribe.maintenance;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import rice.environment.Environment;
import rice.environment.logging.Logger;
import rice.p2p.commonapi.Endpoint;
import rice.p2p.commonapi.NodeHandle;
import rice.p2p.scribe.Scribe;
import rice.p2p.scribe.Topic;
import rice.p2p.scribe.messaging.SubscribeMessage;

public interface ScribeMaintenancePolicy {
  /**
   * Called periodically.  
   * 
   * Can be specified in millis by by the parameter: 
   *   p2p_scribe_maintenance_interval (default 180000) // 3 minutes
   */
  public void doMaintenance(MaintainableScribe scribe);
  
  /**
   * Called when membership changes "near" the local node, in overlay space.  
   * 
   * The typical use of this function would be to detect if the root has 
   * changed and subscribe to the new root, like this:
   * <pre>
   * for (Topic topic : topics) {
   *   scribe.subscribe(topic);
   * }
   * </pre>
   * 
   * Note however that this approach can cause a long tail at the head of the tree.
   * 
   * @param handle
   * @param membership
   */
  public void noLongerRoot(MaintainableScribe scribe, List<Topic> topics); 
  
  /**
   * When anyone in any Topic (child or parent) is detected faulty.
   * 
   * <pre>
   * for (Topic topic : nodeWasParent) {
   *   if (!isRoot(topic)) {
   *     scribe.subscribe(topic);
   *   }
   * }
   * </pre>
   * 
   */
  public void nodeFaulty(MaintainableScribe scribe, NodeHandle node, List<Topic> nodeWasParent, List<Topic> nodeWasChild); // when liveness changes

  /**
   * The subscription failed.  This is called if no particular client requested the Subscription.
   * 
   * TODO: Does this belong in the normal policy instead?
   * 
   * @param failedTopics
   */
  public void subscribeFailed(MaintainableScribe scribe, List<Topic> failedTopics);

  public class DefaultScribeMaintenancePolicy implements
      ScribeMaintenancePolicy {

    Logger logger;
    
    public DefaultScribeMaintenancePolicy(Environment environment) {
      logger = environment.getLogManager().getLogger(DefaultScribeMaintenancePolicy.class, null);
    }

    public void doMaintenance(MaintainableScribe scribe) {
      HashMap<NodeHandle, List<Topic>> manifest = new HashMap<NodeHandle, List<Topic>>();
      
      // for each topic, make sure our parent is still alive      
      for (Topic topic : scribe.getTopics()) {
        NodeHandle parent = scribe.getParent(topic);
        
        // also send an upward heartbeat message, which should make sure we are still subscribed
        if (parent != null) {
          List<Topic> topics = manifest.get(parent);
          if (topics == null) {
            topics = new ArrayList<Topic>();
            manifest.put(parent,topics);
          }
          topics.add(topic);
          
        } else {
          // If the parent is null, then we have either very recently sent out a
          // SubscribeMessage in which case we are fine. The way the tree
          // recovery works when my parent is null is that in the
          // sendSubscribe() method, a local mesage SubscribeLost message is
          // sceduled after message_timeout which in turn triggers the
          // subscribeFailed() method. For a node that is no longer the root,
          // the update method should send out the subscribe message
          // note, this shouldn't be necessary, because the leafset changes should fix this
          // this is in update()
//          if (!scribe.isRoot(topic)) {
//            if (logger.level <= Logger.WARNING)
//              logger.log("has null parent for " + manager.getTopic()
//                  + " inspite of NOT being root, root should be "+getRoot(manager.getTopic()));
//            scribe.subscribe(topic, null);
////            endpoint.route(manager.getTopic().getId(), new SubscribeMessage(handle, manager.getTopic(), handle.getId(), -1, null), null);
//          }        
        }
      }
      
      for (NodeHandle parent : manifest.keySet()) {
        List<Topic> topics = manifest.get(parent);
        scribe.getEndpoint().route(topics.get(0).getId(), new SubscribeMessage(scribe.getEndpoint().getLocalNodeHandle(), topics, MaintainableScribe.MAINTENANCE_ID, null), parent);
        parent.checkLiveness();
      }      
    }    
    
    public void noLongerRoot(MaintainableScribe scribe, List<Topic> topics) {
      // TODO Auto-generated method stub

    }

    public void nodeFaulty(MaintainableScribe scribe, NodeHandle node,
        List<Topic> nodeWasParent, List<Topic> nodeWasChild) {
      // TODO Auto-generated method stub

    }

    public void subscribeFailed(MaintainableScribe scribe, List<Topic> failedTopics) {
//      logger.log("subscribeFailed("+failedTopics.iterator().next()+")");          
      scribe.subscribe(failedTopics);
    }
  }

}
