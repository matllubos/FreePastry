
package rice.pastry.testing;

import rice.pastry.*;
import rice.pastry.messaging.*;

import java.util.*;

/**
 * PingMessageNew
 *
 * A performance test suite for pastry. 
 *
 * @version $Id$
 *
 * @author Rongmei Zhang
 */

public class PingMessageNew extends Message {
    private Id target;

    private int		nHops = 0;
    private double	fDistance = 0;

    public PingMessageNew(int pingAddress, NodeHandle src, Id tgt) {
	super(pingAddress);
	setSender(src);
	target = tgt;
    }

    public String toString() {
	String s="";
	s += "ping from " + getSender().getNodeId() + " to " + target;
	return s;
    }

    public void incrHops( ){ nHops++; }
    public void incrDistance( double dist ){ fDistance += dist; }

    public int getHops(){ return nHops; }
    public double getDistance(){ return fDistance; }

    public Id getSource(){ return getSender().getNodeId(); }
}

