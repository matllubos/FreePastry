package rice.splitstream;

import java.io.*;

import rice.splitstream.messaging.*;
import rice.pastry.security.*;
import rice.scribe.*;
/**
 *
 * @author Ansley Post
 */
public class SplitStreamOutputStream extends ByteArrayOutputStream{

   private StripeId stripeId;
   private IScribe scribe;
   private Credentials cred;
 
   public SplitStreamOutputStream(StripeId stripeId, IScribe scribe, Credentials cred){
	super();
	this.stripeId = stripeId;
	this.scribe = scribe;
	this.cred = cred;
   }
   public void write(byte[] b, int off, int len){
	super.write(b, off, len);
	/* Do splitstream stuff */
	scribe.multicast(stripeId, b, cred);
   }
   public void write(int b){
	super.write(b); 
	System.out.println("Attempting to Send Data");
	//if(scribe.multicast(stripeId, b, cred))
	System.out.println("Attempting to Send Data");
	/* Do splitstream stuff */
   } 

}
