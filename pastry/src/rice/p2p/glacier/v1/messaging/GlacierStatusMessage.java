package rice.p2p.glacier.v1.messaging;

import rice.*;
import rice.p2p.commonapi.*;
import rice.p2p.glacier.v1.*;

public class GlacierStatusMessage extends GlacierMessage 
{
    public int sequenceNo;
    public int ackSequenceNo;
    public int remainingLifetime;
    public boolean isFullList;
    public HistoryEvent[] events;
    
    public GlacierStatusMessage(int uid, int sequenceNo, int ackSequenceNo, int remainingLifetime, boolean isFullList, HistoryEvent[] events, NodeHandle source, Id dest) 
    {
        super(uid, source, dest);

        this.sequenceNo = sequenceNo;
        this.ackSequenceNo = ackSequenceNo;
        this.remainingLifetime = remainingLifetime;
        this.isFullList = isFullList;
        this.events = events;
    }

    public String toString() 
    {
        return "[GlacierStatus "+source.getId()+"#"+sequenceNo+">"+dest+" ack"+ackSequenceNo+" isFull="+isFullList+" "+events.length+"evt]";
    }
}

