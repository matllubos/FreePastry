package rice.p2p.glacier.messaging;

import rice.*;
import rice.p2p.commonapi.*;
import rice.p2p.glacier.*;

public class GlacierStickyMessage extends GlacierMessage 
{
    public Id realDestination;
    public GlacierMessage message;
    
    public GlacierStickyMessage(int uid, Id realDestination, GlacierMessage message, NodeHandle source, Id dest) 
    {
        super(uid, source, dest);

        this.realDestination = realDestination;
        this.message = message;
    }

    public String toString() 
    {
        return "[GlacierSticky "+message+" for "+realDestination+"]";
    }
}

