package rice.splitstream.messaging;

import rice.pastry.messaging.*;
import rice.pastry.security.*;
import rice.pastry.*;
import rice.scribe.*;
import rice.splitstream.*;
import java.util.Vector;

/**
 * This message is sent from any node whose parent has changed, to each
 * of its children and descendents.  It contains a list with the nodeId
 * of each node it encounters along its path.
 *
 * @(#) ControlPropogatePathMessage.java
 * @version $Id:
 * @author briang
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

   /**
    * @return The stripe id associated with this message
    */
   public StripeId getStripeId()
   {
      return stripe_id;
   }

   /**
    * Handles forwarding of the message.  This node's path is set
    * to the current list.  Then this node's NodeHandle is added to the
    * list and messages containing the new list are sent to all of this
    * node's children.
    * @param scribe The scribe group this message is relevant to
    * @param s The specific stripe this is relevant to
    */
   public void handleMessage( Scribe scribe, Channel channel, Stripe stripe )
   {
       //System.out.println( "Received PROPOGATE_PATH : Setting path at node "+scribe.getNodeId() );

      stripe.setRootPath( path );

      Vector children = scribe.getChildren( stripe.getStripeId() );
      Credentials credentials = new PermissiveCredentials();
      for ( int i=0; i<children.size(); i++ )
      {
	  if(path.contains((NodeHandle)children.get(i))){
	      // Cycle dude..
	      //System.out.println("PROPOGATE_PATH :: Cycle detected at "+scribe.getNodeId()+ " with child "+((NodeHandle)children.get(i)).getNodeId()+" for stripe "+stripe.getStripeId());
	      channel.routeMsgDirect((NodeHandle)children.get(i), new ControlDropMessage( channel.getAddress(),
								     channel.getNodeHandle(),
								     stripe.getStripeId(),
								     credentials,
								     channel.getSpareCapacityId(),
								     channel.getChannelId(),
								     channel.getTimeoutLen() ),
				      credentials, null );
	      scribe.removeChild((NodeHandle)children.get(i), stripe.getStripeId());
	  }
	  else {
	      Vector forward_path = (Vector)path.clone();
	      forward_path.add( scribe.getLocalHandle() );
	      channel.routeMsgDirect( (NodeHandle)children.get(i), 
				      new ControlPropogatePathMessage( channel.getAddress(),
								       channel.getNodeHandle(),
								       stripe.getStripeId(),
								       credentials,
								       forward_path ),
				      credentials, null );
	  }
      }
   }

}






