package rice.p2p.glacier.messaging;

import rice.*;
import rice.p2p.commonapi.*;
import rice.p2p.glacier.*;

public class GlacierResponseMessage extends GlacierMessage 
{
    protected FragmentKey key;
    int fragmentID;
    boolean haveIt;
    
    public GlacierResponseMessage(int uid, FragmentKey key, int fragmentID, boolean haveIt, NodeHandle source, Id dest) 
    {
        super(uid, source, dest);

        this.key = key;
        this.fragmentID = fragmentID;
        this.haveIt = haveIt;
    }

    public String toString() 
    {
        return "[GlacierResponse for "+key+":"+fragmentID+" - "+(haveIt ? "has it" : "does not have it")+"]";
    }
    
    public FragmentKey getKey() 
    {
        return key;
    }
    
    public int getFragmentID()
    {
        return fragmentID;
    }
    
    public boolean getHaveIt()
    {
        return haveIt;
    }
}

