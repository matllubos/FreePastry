package rice.p2p.glacier.messaging;

import rice.*;
import rice.p2p.commonapi.*;
import rice.p2p.glacier.*;

public class GlacierDataMessage extends GlacierMessage 
{
    protected FragmentKey key;
    int fragmentID;
    Fragment fragment;
    
    public GlacierDataMessage(int uid, FragmentKey key, int fragmentID, Fragment fragment, NodeHandle source, Id dest) 
    {
        super(uid, source, dest);

        this.key = key;
        this.fragmentID = fragmentID;
        this.fragment = fragment;
    }

    public String toString() 
    {
        return "[GlacierData for "+key+":"+fragmentID+"]";
    }
    
    public FragmentKey getKey() 
    {
        return key;
    }
    
    public int getFragmentID()
    {
        return fragmentID;
    }
    
    public Fragment getFragment()
    {
        return fragment;
    }
}

