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




public class Buddy implements java.io.Serializable {

    String _name;
    String _indicator;

    public static final String FRIEND = "friend";
    public static final String FAMILY = "family";
    public static final String WORKER = "worker";

    
    public Buddy(String name, String arena) {
	_name = name;
	_indicator = arena;
    }

    public Buddy (Buddy bud) {
	_name = bud.getName();
	_indicator = bud.getIndicator();
    }

    public boolean equals(Object obj) {
	if (obj instanceof Buddy) {
	    Buddy b = (Buddy) obj;
	    return b.getName().equals(getName()) && b.getIndicator().equals(getIndicator());
	}
	return false;
    }

    public Buddy getBuddy() {
	return this;
    }

    public String getName() {
	return _name;
    }

    public String getIndicator() {
	return _indicator;
    }
    
}
