package rice.p2p.glacier.testing;

import rice.p2p.past.*;
import rice.p2p.commonapi.*;
import rice.p2p.glacier.testing.*;

public class TestContent implements PastContent {

    protected Id myId;

    public TestContent(Id id)
    {
        myId = id;
    }

    public PastContent checkInsert(Id id, PastContent existingContent) throws PastException
    {
        System.out.println("checkInsert("+existingContent+") -- returning this");
        return this;
    }
    
    public PastContentHandle getHandle(Past local)
    {
        return new TestContentHandle(myId, local.getLocalNodeHandle());
    }
    
    public Id getId()
    {
        return myId;
    }
    
    public boolean isMutable()
    {
        return false;
    }
}

