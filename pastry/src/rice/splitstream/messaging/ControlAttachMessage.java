package rice.splitstream.messaging;

import rice.pastry.*;
import rice.pastry.messaging.*;
import rice.splitstream.*;
import rice.scribe.*;
import java.io.Serializable;

/**
 * This message is anycast to the scribe group for a channel when a node
 * attaches to that channel.  The purpose is to learn which stripes are
 * included in this channel.
 */
public class ControlAttachMessage implements Serializable {

/**
 * This method is called by the application (here, the channel) upon
 * receipt.  It retrieves the list of stripeIds and generates a
 * response message to the originator of the request.
 */
public void handleMessage( Channel channel, IScribe scribe, NodeHandle source )
{
      StripeId[] return_array = channel.getStripes();
      ControlAttachResponseMessage response = new ControlAttachResponseMessage( (channel.getAddress() );
      response.setContent( return_array );
      ((Scribe)scribe).routeMsgDirect( source, response, null, null );
}

}

