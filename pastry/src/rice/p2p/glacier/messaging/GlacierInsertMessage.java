package rice.p2p.glacier.messaging;

import rice.*;
import rice.p2p.commonapi.*;
import rice.p2p.glacier.*;

public class GlacierInsertMessage extends GlacierMessage 
{
    protected FragmentKey key;
    StorageManifest manifest;
    public Id knownHolder[];
    public int knownHolderFragmentID[];
    public boolean knownHolderCertain[];
    Fragment fragment;
    int fragmentID;
    
    public GlacierInsertMessage(int uid, FragmentKey key, int fragmentID, StorageManifest manifest, Fragment fragment, Id[] knownHolder, int[] knownHolderFragmentID, boolean[] knownHolderCertain, NodeHandle source, Id dest) 
    {
        super(uid, source, dest);

        this.key = key;
        this.fragmentID = fragmentID;
        this.manifest = manifest;
        this.fragment = fragment;
        this.knownHolder = knownHolder;
        this.knownHolderFragmentID = knownHolderFragmentID;
        this.knownHolderCertain = knownHolderCertain;
    }

    public String toString() 
    {
        return "[GlacierInsert for "+key+":"+fragmentID+"]";
    }
    
    public FragmentKey getKey() 
    {
        return key;
    }
    
    public int getFragmentID()
    {
        return fragmentID;
    }
    
    public StorageManifest getStorageManifest()
    {
        return manifest;
    }
    
    public Fragment getFragment()
    {
        return fragment;
    }
}

