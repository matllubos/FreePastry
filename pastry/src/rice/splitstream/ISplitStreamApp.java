/**
 *
 * This interface allows an application running on top of SplitStream 
 * to be notified of events.
 */
 public interface ISplitStreamApp{
   /**
    * This is a call back into the application
    * to notify it that one of the stripes was unable to
    * to find a parent, and thus unable to recieve data.
    */
   public void handleParentFailure(Stripe s);


 }
