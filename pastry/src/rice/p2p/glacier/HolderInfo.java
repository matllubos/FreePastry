package rice.p2p.glacier;

import rice.p2p.commonapi.Id;
import rice.p2p.glacier.*;
import java.util.Date;
import java.io.Serializable;

public class HolderInfo implements Serializable {
    public Id nodeID;
    public Date lastHeardOf;
    public int lastReceivedSequenceNo;
    public int lastAckedSequenceNo;
    public int numReferences;
    public int numLiveReferences;
    
    public HolderInfo(Id nodeID, Date lastHeardOf, int lastReceivedSequenceNo, int lastAckedSequenceNo)
    {
        this.nodeID = nodeID;
        this.lastHeardOf = lastHeardOf;
        this.lastReceivedSequenceNo = lastReceivedSequenceNo;
        this.lastAckedSequenceNo = lastAckedSequenceNo;
        this.numReferences = 0;
        this.numLiveReferences = 0;
    }
};
    
