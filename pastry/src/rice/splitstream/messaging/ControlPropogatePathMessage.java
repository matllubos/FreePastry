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
 * @version $Id$
 * @author briang
 *
 * @deprecated This version of SplitStream has been deprecated - please use the version
 *   located in the rice.p2p.splitstream package.
 */
public class ControlPropogatePathMessage extends Message{

   /**
    * Cumulative path to root thus far
    */
   private Vector path;
   
   /**
    * Source of this message
    */
   private NodeHandle source;

   /**
    * Id of the stripe this message pertains to
    */
   private StripeId stripe_id;

   /**
    * Id of the channel this message pertains to
    */
   private ChannelId channel_id;

   /**
    * Constructor for a ControlPropogatePath message
    * @param addr Address of the destination application
    * @param source The source of this message
    * @param topicId The stripe we build the path for
    * @param c The credentials to send under
    * @param path Initial path starting point
    * @param channel_id The channel this message pertains to
    */
   public ControlPropogatePathMessage( Address addr, NodeHandle source, NodeId topicId, Credentials c, Vector path, ChannelId channel_id )
   {
       super( addr );
      this.path = path;
      this.source = source;
      this.stripe_id = (StripeId)topicId;
      this.channel_id = channel_id;
   }

   /**
    * @return The stripe id associated with this message
    */
   public StripeId getStripeId()
   {
      return stripe_id;
   }

   public ChannelId getChannelId()
   {
      return channel_id;
   }

   /**
    * Handles forwarding of the message.  This node's path is set
    * to the current list.  Then this node's NodeHandle is added to the
    * list and messages containing the new list are sent to all of this
    * node's children.
    * @param scribe The scribe group this message is relevant to
    * @param channel The channel this message is relevant to
    * @param stripe The specific stripe this is relevant to
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
	      System.out.println("DEBUG :: PROPOGATE_PATH :: Cycle detected at "+scribe.getNodeId()+ " with child "+((NodeHandle)children.get(i)).getNodeId()+" for stripe "+stripe.getStripeId());
	      channel.getSplitStream().routeMsgDirect((NodeHandle)children.get(i), new ControlDropMessage( channel.getSplitStream().getAddress(),
								     channel.getSplitStream().getNodeHandle(),
								     stripe.getStripeId(),
								     credentials,
								     channel.getSpareCapacityId(),
								     channel.getChannelId(),
								     channel.getTimeoutLen() ),
				      credentials, null );
	      if ( !scribe.removeChild((NodeHandle)children.get(i), stripe.getStripeId()) )
              {
                  System.out.println( "DEBUG :: Error removing child " + ((NodeHandle)children.get(i)).getNodeId()
                                      + " at " + scribe.getNodeHandle().getNodeId() );
              }
	  }
	  else {
	      Vector forward_path = (Vector)path.clone();
	      forward_path.add( scribe.getLocalHandle() );
	      channel.getSplitStream().routeMsgDirect( (NodeHandle)children.get(i), 
				      new ControlPropogatePathMessage( channel.getSplitStream().getAddress(),
								       channel.getSplitStream().getNodeHandle(),
								       stripe.getStripeId(),
								       credentials,
								       forward_path,
                                                                       channel_id ),
				      credentials, null );
	  }
      }
   }

}






