package rice.p2p.glacier;

import rice.p2p.glacier.*;
import rice.p2p.commonapi.Id;
import java.io.Serializable;

public class HistoryEvent implements Serializable {
    public int type;
    FragmentKey key;
    int fragmentID;
    Id holder;
    int sequenceNo;
    
    public static final int evtAcquired = 1;
    public static final int evtHandedOff = 2;
    public static final int evtNewHolder = 3;

    public HistoryEvent(int type, FragmentKey key, int fragmentID, Id holder, int sequenceNo)
    {
        this.type = type;
        this.key = key;
        this.fragmentID = fragmentID;
        this.holder = holder;
        this.sequenceNo = sequenceNo;
    }
    
    public static String eventName(int eventType)
    {
        if (eventType == evtAcquired)
            return "Acquired";
        if (eventType == evtHandedOff)
            return "HandedOff";
        if (eventType == evtNewHolder)
            return "NewHolder";
            
        return "Unknown ("+eventType+")";
    }

    public String toString()
    {
        return "[S#"+sequenceNo+" "+eventName(type)+" "+key+":"+fragmentID+" - "+holder+"]";
    }
};
