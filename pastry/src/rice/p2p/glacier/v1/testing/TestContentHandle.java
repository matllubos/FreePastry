package rice.p2p.glacier.v1.testing;

import rice.p2p.past.*;
import rice.p2p.commonapi.*;

public class TestContentHandle implements PastContentHandle {
    protected Id myId;
    protected rice.p2p.commonapi.NodeHandle myNodeHandle;
    
    TestContentHandle(Id id, NodeHandle nodeHandle)
    {
        myId = id;
        myNodeHandle = nodeHandle;
    }
    
    public Id getId()
    {
        return myId;
    }
    
    public NodeHandle getNodeHandle()
    {
        return myNodeHandle;
    }
}
