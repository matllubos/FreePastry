package rice.p2p.glacier.messaging;

import rice.*;
import rice.p2p.commonapi.*;
import rice.p2p.glacier.*;

public class GlacierQueryMessage extends GlacierMessage 
{
    protected FragmentKey key;
    int fragmentID;
    
    public GlacierQueryMessage(int uid, FragmentKey key, int fragmentID, NodeHandle source, Id dest) 
    {
        super(uid, source, dest);

        this.key = key;
        this.fragmentID = fragmentID;
    }

    public String toString() 
    {
        return "[GlacierQuery for "+key+":"+fragmentID+"]";
    }
    
    public FragmentKey getKey() 
    {
        return key;
    }
    
    public int getFragmentID()
    {
        return fragmentID;
    }
}

