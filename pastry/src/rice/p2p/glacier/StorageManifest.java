package rice.p2p.glacier;

import rice.p2p.commonapi.*;
import java.io.Serializable;

public class StorageManifest implements Serializable {
    protected FragmentKey key;
    protected int fragmentHash[];
    
    public StorageManifest(FragmentKey key, int fragmentHash[])
    {
        this.key = key;
        this.fragmentHash = fragmentHash;
    }
}
