package rice.splitstream.messaging;

import rice.pastry.messaging.*;
import rice.scribe.*;
import java.util.Vector;

/**
 * This message is sent from any node whose parent has changed, to each
 * of its children and descendents.  It contains a stack with the nodeId
 * of each node it encounters along its path.
 */
public class ControlPropagatePathMessage extends ControlMessage{

   private Vector path;

   public ControlPropogatePathMessage( Address addr, NodeHandle source, StripeId topicId, Credentials c )
   {
      super( addr, source, topicId, c );
      path = null;
   }

   /**
    * Handles forwarding of the message: adding this node's Id to the stack
    * and sending to all children
    * @param scribe The scribe group this message is relevant to
    * @param s The specific stripe this is relevant to
    */
   public void handleForwardMessage( IScribe scribe, Stripe s)
   {
      Scribe _scribe = (Scribe)scribe;

      if ( path == null )
      {
         if ( _scribe.isRoot( s.getStripeId() ) )
         {
            this.handleDeliverMessage( scribe, s );
         }
         else
         {
            _scribe.routeMsgDirect( _scribe.getParent( s.getStripeId() ), this );
         }
      else
      {
         Vector v = _scribe.getChildren( s.getStripeId() );

         s.setRootPath( path );
         path.add( _scribe.getLocalHandle() );
         for ( int i=0; i<v.size(); i++ )
         {
            _scribe.routeMsgDirect( (NodeHandle)v.get(i), this.clone() );
         }
      }
   }

   /**
    * Handles delivery of the message: called at root of scribe tree
    * @param scribe The scribe group this message is relevant to
    * @param s The specific stripe this is relevant to
    */
   public void handleDeliverMessage( IScribe scribe, Stripe s)
   {
      Scribe _scribe = (Scribe)scribe;
      Vector v = _scribe.getChildren( s.getStripeId() );

      path = new Vector();
      s.setRootPath( path );
      path.add( _scribe.getLocalHandle() );

      for ( int i=0; i<v.size(); i++ )
      {
         _scribe.routeMsgDirect( (NodeHandle)v.get(i), this.clone() );
      }            
   }
}






