package rice.splitstream;

import java.io.*;

import rice.splitstream.messaging.*;
import rice.pastry.security.*;
import rice.scribe.*;

/**
 * This object is a stream type that can be used to output data to
 * a stripe group in splitstream. It is meant to be compatible with
 * the java stream architecture.  It currently only works with bytes
 * or objects that can be converted to byte arays such as a string. It
 * will not work with serialization due to the headers generated when
 * serialization is used. There is also some limits due to the fact
 * that there must be some synchronization with serialized objects since
 * you must get the header information at the beginning of the stream
 * to be able to reconstruct the objects. This does not work well with
 * our system since it is not synchronized at all and stripes can start
 * reading at any time.
 * 
 * @version $Id$
 * @author Ansley Post
 */
public class SplitStreamOutputStream extends ByteArrayOutputStream{
   
   /**
    * The stripeId associated with this stream,
    * basically the stripe that this object was created by 
    */ 
   private StripeId stripeId;

   /**
    * The scribe object used by this stream to transmit data
    */
   private IScribe scribe;

   /**
    * The security credentials that must be presented to send 
    * data
    */
   private Credentials cred;

   /**
    * The constructor to create this object. 
    *
    * @param stripeId the stripeId to send on 
    * @param scribe the scribe object to use
    * @param cred the credentials to use
    */ 
   public SplitStreamOutputStream(StripeId stripeId, IScribe scribe, Credentials cred){
	super();
	this.stripeId = stripeId;
	this.scribe = scribe;
	this.cred = cred;
   }

   /**
    * Method to write an array of bytes
    * This is overridden from its parent class
    * calls super then uses scribe to send the data
    */
   public void write(byte[] b, int off, int len){
	super.write(b, off, len);
	/* Do splitstream stuff */
	scribe.multicast(stripeId, b, cred);
   }

   /**
    * Method to write a single byte
    * This is overridden from its parent class
    * calls super then uses scribe to send the data
    */
   public void write(int b){
	super.write(b); 
        /* This is used because byte[] can be serialized but byte cannot */
        byte[] temp = new byte[1];
        temp[0] = (byte) b;
	if(scribe.multicast(stripeId, temp, cred)){}
	/* Do splitstream stuff */
   } 

}
