package rice.splitstream;
import rice.pastry.*;
import java.io.*; 
import java.util.Observable;
/**
 * This class encapsulates all data about an individual stripe.
 */
public class Stripe extends Observable implements IScribe{
    private int stripeState = 0;
    private stripeId = null;
    private Channel channel =  null;
    private InputStream inputStream = null;
    private OutputStream outputStream = null;

   /**
    * gets the StripeID for this stripe
    * @return theStripeID 
    */
   public StripeId getStripeId(){return stripeId;}
   /**
    * gets the Channel that this stripe is a part of 
    * @return Channel Object 
    */
   public Channel getChannel(){return channel;}
    /**
     * Gets the OutputStream for this stripe.
     * @return StripeOutputStream for this stripe
     */
    public OutputStream getOutputStream(){return outputStream;}
    /**
     * Gets the InputStream for this Stripe.
     * @return StripeInputStream for this Stripe
     */
     public InputStream getInputStream(){return inputStream;}
     /**
      * Leaves this stripe
      */
     public void leaveStripe(){}
    /**
    * get the state of the Stripe 
    * @return int s
    */
    public int getState(){ return stripeState;}

    public static final int STRIPE_VALID = 0;
    public static final int STRIPE_INVALID = 1;
}
