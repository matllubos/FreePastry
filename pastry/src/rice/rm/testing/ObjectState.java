
package rice.rm.testing;

import rice.pastry.*;
import rice.pastry.client.*;
import rice.pastry.security.*;
import rice.pastry.routing.*;
import rice.pastry.messaging.*;
import rice.pastry.direct.*;
import rice.pastry.leafset.*;

import rice.rm.*;
import rice.rm.messaging.*;
import rice.rm.testing.*;

import java.util.*;
import java.security.*;
import java.io.*;

/**
 * @(#) ObjectState.java
 *
 *  used by the RegrTest suite for Replica Manager
 * @version $Id$
 * @author Animesh Nandi 
 */


public class ObjectState {
    private int staleCount;
    private int missingCount;
    private boolean present;

    private final int STALE_LIMIT = 5;
    private final int MISSING_LIMIT = 5;
    
    public ObjectState(boolean val1, int val2, int val3){
	present = val1;
	missingCount = val2;
	staleCount = val3;
    }

    public boolean isStale() {
	if(present && (staleCount > STALE_LIMIT))
	    return true;
	else
	    return false;
    }

    public boolean isMissing() {
	if(!present && (missingCount > MISSING_LIMIT))
	    return true;
	else
    return false;
    }


    public int getstaleCount(){
	return staleCount;
    }
    
    public void setstaleCount(int value) {
	staleCount = value;
    }
	
    public void setmissingCount(int value) {
	missingCount = value;
    }
    
    public int getmissingCount() {
	return missingCount;
    }
    
    public boolean isPresent() {
	return present;
    }
    
    public void setPresent(boolean val) {
	present = val;
    }
    
    public void incrMissingCount() {
	missingCount ++;
    }

    public void incrStaleCount() {
	staleCount ++;
    }
    
}











