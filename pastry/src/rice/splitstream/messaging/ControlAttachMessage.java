package rice.splitstream.messaging;

import rice.pastry.*;
import rice.pastry.messaging.*;
import rice.pastry.security.*;
import rice.splitstream.*;
import rice.scribe.*;
import java.io.Serializable;

/**
 * This message is anycast to the scribe group for a channel when a node
 * attaches to that channel.  The purpose is to learn which stripes are
 * included in this channel.  This information is returned via a
 * ControlAttachResponseMessage.
 *
 * @(#) ControlAttachMessage.java
 * @version $Id:
 * @author briang
 */
public class ControlAttachMessage extends Message implements Serializable {

/**
 * NodeHandle of the source of this message
 */
private NodeHandle m_source;
private ChannelId channel_id;

/**
 * Constructor
 * @param addr The receiving address
 * @param m_source The originating node's handle
 * @param channel_id The id of the channel this pertains to
 */
public ControlAttachMessage( Address addr, NodeHandle m_source, ChannelId channel_id )
{
    super( addr );
    this.m_source = m_source;
    this.channel_id = channel_id;
}

/**
 * This method is called by the application (here, the channel) upon
 * receipt.  It retrieves the list of stripeIds and generates a
 * response message to the originator of the request.
 *
 * @param channel The channel receiving the message
 * @param scribe The channel's Scribe object (unused)
 * @param source The originating node's NodeHandle
 * @return boolean Returns false if the receiver can handle this message
 */
public boolean handleMessage( Channel channel, IScribe scribe, NodeHandle source )
{
      SpareCapacityId spcapid = channel.getSpareCapacityId();
      ChannelId chanid = channel.getChannelId();
      StripeId[] stripeid_array = channel.getStripes();
      
      NodeId[] return_array = new NodeId[stripeid_array.length+2];
      return_array[0] = (NodeId)chanid;
      for ( int i=0; i<stripeid_array.length; i++ )
      {
          return_array[i+1] = stripeid_array[i];
      }
      return_array[return_array.length-1] = (NodeId)spcapid;

      ControlAttachResponseMessage response = new ControlAttachResponseMessage( channel.getSplitStream().getAddress(), channel_id );
      Credentials credentials = new PermissiveCredentials();
      response.setContent( return_array );
      if(return_array == null){
	System.out.println("I'm not returning any data");
      }else if(return_array.length <= 1){
	System.out.println("Returning too little data");
      }
      //((Scribe)scribe).routeMsgDirect( source, response, null, null );
      channel.getSplitStream().routeMsgDirect( source, response, credentials, null );
      return false;
}

public NodeHandle getSource()
{
    return m_source;
}

public ChannelId getChannelId()
{
    return channel_id;
}

}

