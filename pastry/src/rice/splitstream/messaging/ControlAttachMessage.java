/**
 * This message is anycast to the scribe group for a channel when a node
 * attaches to that channel.  The purpose is to learn which stripes are
 * included in this channel.
 */
public class ControlAttachMessage extends MessageAnycast {

/**
 * This method is called by the application (here, the channel) upon
 * receipt.  It retrieves the list of stripeIds and generates a
 * response message to the originator of the request.
 */
public void handleMessage( Scribe scribe, Topic topic )
{
}

}
