package rice.p2p.glacier;

import java.util.*;
import java.io.Serializable;

public class GlacierState implements Serializable {
    public Hashtable fileList;
    public Hashtable holderList;
    public Date lastStatusCast;
    public LinkedList history;
    public int currentSequenceNo;
        
    public GlacierState() 
    {
        fileList = new Hashtable();
        holderList = new Hashtable();
        lastStatusCast = new Date();
        history = new LinkedList();
        currentSequenceNo = 0;
    }
};    

