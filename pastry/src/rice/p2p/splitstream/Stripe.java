package rice.p2p.splitstream;

import rice.splitstream2.messaging.*;

import rice.scribe.*;
import rice.scribe.messaging.*;

import rice.pastry.*;
import rice.pastry.standard.*;
import rice.pastry.security.*;

import java.io.*; 
import java.util.*;

/**
 * This class encapsulates all data about an individual stripe.
 * It is the basic unit in the system. It is rooted at the stripeId
 * for the stripe.  It is through the stripe that data is sent. It
 * can be subscribed to in which case data is recieved or it can
 * be unsubscribed from. A stripe can have some number of children
 * and controlling this is the way that bandwidth is controlled.
 * If a stripe is dropped then it searches for a new parent. 
 * A stripe recieves all the scribe messages for the topicId
 * It handles subscribtions and data received through scribe
 *
 * @version $Id$
 * @author Ansley Post
 */

public class Stripe extends Observable{

   /**
    * The stripe state, whether it is dropped, connected, etc.
    */
   private int stripeState = STRIPE_UNSUBSCRIBED;

   /**
    * The stripeId for this stripe
    */
   private StripeId stripeId = null;

   /**
    * The channel this stripe is a part of.
    */
   private Channel channel =  null;

   /**
    * The input stream used to get data from this stripe. 
    * Currently this is a byteArrayInputStream but should be
    * converted to a SplitStreamInputStream so that the
    * buffer will not grow forever. Currently the app
    * does not use this stream for input and instead
    * uses the Observer parent
    */
   private InputStream inputStream = null;

   /**
    * The buffer that holds the data, it is used by 
    * the input stream. Which is currently not used
    */
   private byte[] inputBuffer = new byte[1];  

   /**
    * The output stream used to give date to this stripe.
    */
   private OutputStream outputStream = null;

   /**
    * A flag whether or not this stripe is the primary stripe for this node
    */
   private boolean isPrimary = false;  

   /**
    * The constructor used when creating a stripe from scratch.
    *
    * @param stripeId the stripeId that this stripe will be rooted at
    * @param channel the channel this stripe belongs to
    * @param scribe the underlying scribe object used
    * @param credentials the security credentials used
    * @param create wheter this stripe has already been created or needs
    * to be created now
    */
   public Stripe(StripeId stripeId, Channel channel) {
      this.channel = channel;
      this.stripeId = stripeId;
      stripeState = STRIPE_UNSUBSCRIBED;
      /* Figure out a a way to use this output stream .... */
      //outputStream = new SplitStreamOutputStream(stripeId, scribe, credentials);
    }


   /**
    * gets the StripeID for this stripe
    * @return theStripeID 
    */
    public StripeId getStripeId(){
     return stripeId;
    }
    /**
    * gets the Channel that this stripe is a part of 
    * @return Channel Object 
    */
    public Channel getChannel(){
      return channel;
    }
    /**
     * Gets the OutputStream for this stripe.
     * @return StripeOutputStream for this stripe
     */
    public OutputStream getOutputStream(){
      return outputStream;
    }
    /**
     * Gets the InputStream for this Stripe.
     * @return StripeInputStream for this Stripe
     */
     public InputStream getInputStream(){
       return inputStream;
     }
     /**
      * Leaves this stripe
      * This causes us to stop getting data and to leave the
      * scribe topic group 
      */
     public void leaveStripe(){
     }

     /**
      * Joins this stripe 
      * It causes this stripe to become subscribed and allows
      * data to start being received
      */
     public void joinStripe(){
     }

     /**
      * Method called when this stripe is dropped
      */
     protected void dropped(){
        stripeState = STRIPE_DROPPED;
     }

   /**
    * get the state of the Stripe 
    * @return int the State the stripe is in 
    */
    public int getState(){ 
       return stripeState;
    }


    private void setState(int state){
	stripeState = state;
    }

    protected void messageForStripe(SplitStreamMessage m){}

    public static final int STRIPE_SUBSCRIBED = 0;

    /** 
     * The constant status code associated with the unsubscribed state
     */
    public static final int STRIPE_UNSUBSCRIBED = 1;

    /** 
     * The constant status code associated with the dropped state
     */
    public static final int STRIPE_DROPPED = 2;

}

