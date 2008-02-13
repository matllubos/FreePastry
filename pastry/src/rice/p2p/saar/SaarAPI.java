package rice.p2p.saar;

import rice.*;
import java.util.Vector;
import rice.environment.Environment;
import rice.p2p.commonapi.*;
import rice.p2p.scribe.*;


public interface SaarAPI {

    // SAARPolicy uses this information to demultiplex to the appropriate DataplaneClient based on topic/basetopic. The dataplaneClients for all topics in SAAR should be registered. 
    public void register(SaarTopic basetopic, DataplaneClient dataplaneClient);


    // this operation is reflected on all Scribe trees corresponding to the SAAR anycast group of 'basetopic'
    public void subscribe(SaarTopic basetopic);

    // this operation is reflected on all Scribe trees corresponding to the SAAR anycast group of 'basetopic'
    public void unsubscribe(SaarTopic basetopic);

    // this operation is reflected on all Scribe trees corresponding to the SAAR anycast group of 'basetopic'. If forceUpdate=false, then this method only updates the medadata on teh local node and the periodic upward propagation send an update to Scribe when scheduled. If forceUpdate=true then the update will be sent immediately. 
    public void update(SaarTopic basetopic, SaarContent saarContent, boolean forceUpdate);


    /*
     * Parameters used in the 'anycast' method
     * reqContent - encompasses the state of the requestor which might be required to evalaute the predicate/optimization functions
     * hint - the anycast request is bumped through the hint node. This can be helpful in starting the anycast traversal at different points in the spanning tree by giving different hints
     * numTreesToUse - sends redundnt anycast via multiple Scribe trees the dataplaneclient can choose to specify the number of trees to use upto the maximum number of trees supported by SAAR
     * satisfyThreshold - the minimum of metadata that should be satisfied before stopping the exploration of the anycast traversal
     * traversalThreshold - the maximum number of intermediate nodes traversed by the anycast traversal, it controls the overhead
     */
    public void anycast(SaarTopic basetopic, SaarContent reqContent, NodeHandle hint, int numTreesToUse, int satisfyThreshold, int traversalThreshold) ;


    public void issuePing(NodeHandle remote, int pingId, float expectdelay, int payloadinbytes);
    

}




