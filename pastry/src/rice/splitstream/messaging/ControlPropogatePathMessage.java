package rice.splitstream.messaging;

import rice.pastry.messaging.*;
import rice.pastry.security.*;
import rice.pastry.*;
import rice.scribe.*;
import rice.splitstream.*;
import java.util.Vector;

/**
 * This message is sent from any node whose parent has changed, to each
 * of its children and descendents.  It contains a stack with the nodeId
 * of each node it encounters along its path.
 */
public class ControlPropogatePathMessage extends Message{

   private Vector path;
   private NodeHandle source;
   private StripeId stripe_id;

   public ControlPropogatePathMessage( Address addr, NodeHandle source, NodeId topicId, Credentials c, Vector path )
   {
      super( addr );
      this.path = path;
      this.source = source;
      this.stripe_id = (StripeId)topicId;
   }

   public StripeId getStripeId()
   {
      return stripe_id;
   }

   /**
    * Handles forwarding of the message: adding this node's Id to the stack
    * and sending to all children
    * @param scribe The scribe group this message is relevant to
    * @param s The specific stripe this is relevant to
    */
   public void handleMessage( Scribe scribe, Channel channel, Stripe stripe )
   {
      //System.out.println( "Setting path at node "+scribe.getNodeId() );
      stripe.setRootPath( path );
      Vector forward_path = path;
      forward_path.add( scribe.getLocalHandle() );
      Vector children = scribe.getChildren( stripe.getStripeId() );
      Credentials credentials = new PermissiveCredentials();
      for ( int i=0; i<children.size(); i++ )
      {
          channel.routeMsgDirect( (NodeHandle)children.get(i), 
                                  new ControlPropogatePathMessage( channel.getAddress(),
                                                                   channel.getNodeHandle(),
                                                                   stripe.getStripeId(),                                                                                        credentials,
                                                                   forward_path ),
                                  credentials, null );
      }
   }

}






