/*************************************************************************

"FreePastry" Peer-to-Peer Application Development Substrate 

Copyright 2002, Rice University. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

- Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

- Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

- Neither  the name  of Rice  University (RICE) nor  the names  of its
contributors may be  used to endorse or promote  products derived from
this software without specific prior written permission.

This software is provided by RICE and the contributors on an "as is"
basis, without any representations or warranties of any kind, express
or implied including, but not limited to, representations or
warranties of non-infringement, merchantability or fitness for a
particular purpose. In no event shall RICE or contributors be liable
for any direct, indirect, incidental, special, exemplary, or
consequential damages (including, but not limited to, procurement of
substitute goods or services; loss of use, data, or profits; or
business interruption) however caused and on any theory of liability,
whether in contract, strict liability, or tort (including negligence
or otherwise) arising in any way out of the use of this software, even
if advised of the possibility of such damage.

********************************************************************************/

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

    private final int STALE_LIMIT = 1;
    private final int MISSING_LIMIT = 1;
    
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











