package rice.p2p.glacier;

import java.io.Serializable;

public class Fragment implements Serializable {
    public int fragmentID;
    public int payload[];
    
    public Fragment(int _fragmentID, int _size)
    {
        fragmentID = _fragmentID;
        payload = new int[_size];
    }
};

