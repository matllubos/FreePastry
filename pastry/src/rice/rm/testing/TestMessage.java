

package rice.rm.testing;

import rice.pastry.*;
import rice.pastry.messaging.*;
import rice.pastry.security.*;

import rice.rm.*;

import java.util.Random;
import java.io.*;

/**
 *
 * @version $Id$
 * @author Atul Singh
 * @author Animesh Nandi
 */
public abstract class TestMessage extends Message implements Serializable{


    /**
     * The credentials of the author for the object contained in this object
     */
    private Credentials _authorCred;
     
   
    /**
     * The ID of the source of this message.
     * Should be serializable.
     */
    protected NodeHandle _source;




    public TestMessage(NodeHandle source, Address address, Credentials authorCred) {
	super(address);
	this._source = source; 
	this._authorCred = authorCred;
    }
    


    public abstract void 
	handleDeliverMessage( RMRegrTestApp testApp);
    


    public NodeHandle getSource() {
	return _source;
    }
    
    
    public Credentials getCredentials(){
	return _authorCred;
    }

    
}





