package rice.splitstream2;
/**
 *
 * This interface allows an application running on top of SplitStream 
 * to be notified of events.
 *
 * @version $Id$
 * @author Ansley Post
 */
 public interface ISplitStreamApp{

   /**
    * This is a call back into the application
    * to notify it that one of the stripes was unable to
    * to find a parent, and thus unable to recieve data.
    */
   public void handleParentFailure(Stripe s);


     /**
      * Notification from a channel when it is ready, so that
      * application can take appropriate action.
      * 
      * @param channelId the channel which is ready
      */
     public void channelIsReady(ChannelId channelId);

     /**
      * Notification from a splitstream when it is ready, so that
      * application can take appropriate action.
      */
     public void splitstreamIsReady();
 }








