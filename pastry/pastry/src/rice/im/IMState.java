package rice.im;

import java.util.*;

import rice.*;
import rice.post.*;
import rice.post.log.*;
import rice.post.messaging.*;
import rice.post.storage.*;
import rice.email.log.*;
import rice.email.messaging.*;
import rice.im.log.*;
import rice.im.messaging.*;

/**
 * This class maintains a notion of state of the IMService 
 *
 *
 */


public class IMState implements java.io.Serializable {

    private int state; // the state of this service
    
    public static final int ENTER = 1;
    public static final int ONLINE = 2;
    public static final int EXIT = 3;
    public static final int ONLINE_NO_DISPLAY = 4;


    public IMState(int state) {
	this.state = state;
    }

    public int getState() {
	return state;
    }

    public void  setState(int state) {
	this.state = state;
    }

    public boolean equals(Object obj) {
	IMState imstate = (IMState) obj;
	return imstate.getState() == state;
    }

}
