package rice.splitstream.messaging;

import rice.pastry.*;
import rice.pastry.messaging.*;
import rice.pastry.security.*;
import rice.splitstream.*;
import rice.scribe.*;
import rice.scribe.messaging.*;
import java.io.Serializable;

/**
 * This message is anycast to the scribe group for a channel when a node
 * attaches to that channel.  The purpose is to learn which stripes are
 * included in this channel.  This information is returned via a
 * ControlAttachResponseMessage.
 *
 * @(#) ControlAttachMessage.java
 * @version $Id$
 * @author briang
 * @author Atul Singh
 */
public class ControlAttachMessage extends MessageAnycast{

    /**
     * NodeHandle of the source of this message
     */
    private NodeHandle m_source;

    /**
     * Id of the channel this message pertains to
     */
    private ChannelId channel_id;
    
    /** 
     * Credentials.
     */
    Credentials credentials;

    /**
     * Constructor
     * @param addr The receiving address
     * @param source The originating node's handle
     * @param channel_id The id of the channel this pertains to
     * @param cred The credentials for the message
     */
    public ControlAttachMessage( Address addr, NodeHandle source, ChannelId channel_id, Credentials cred )
    {
	super( addr, source, (NodeId)channel_id, cred );
	this.m_source = source;
	this.channel_id = channel_id;
	this.credentials = cred;
    }

    /**
     * This method is called by the application (here, the channel) upon
     * receipt.  It retrieves the list of stripeIds and generates a
     * response message to the originator of the request.
     *
     * @param channel The channel receiving the message
     * @param scribe The channel's Scribe object (unused)
     * @return boolean Returns false if the receiver can handle this message
     */
    public boolean handleMessage( Channel channel, IScribe scribe )
    {
	if(!channel.isReady()){
	    System.out.println("DEBUG :: ControlAttachMessage -- Channel is not ready at "+((Scribe)scribe).getNodeId());
	    return true;
	}
	else {
	    NodeHandle source = this.getSource();
	    SpareCapacityId spcapid = channel.getSpareCapacityId();
	    ChannelId chanid = channel.getChannelId();
	    StripeId[] stripeid_array = channel.getStripes();
	    
	    NodeId[] return_array = new NodeId[stripeid_array.length+2];
	    System.out.println("DEBUG :: ControlAttachMessage  -- Node "+((Scribe)scribe).getNodeId()+" satisfying request from node "+source.getNodeId());

	    return_array[0] = (NodeId)chanid;
	    for ( int i=0; i<stripeid_array.length; i++ )
		{
		    return_array[i+1] = stripeid_array[i];
		}
	    return_array[return_array.length-1] = (NodeId)spcapid;
	    
	    ControlAttachResponseMessage response = new ControlAttachResponseMessage( channel.getSplitStream().getAddress(), channel_id );
	    
	    response.setContent( return_array );
	    if(return_array == null){
		System.out.println("DEBUG :: I'm not returning any data");
	    }else if(return_array.length <= 1){
		System.out.println("DEBUG :: Returning too little data");
	    }
	    
	    channel.getSplitStream().routeMsgDirect( source, response, credentials, null );
	    return false;
	}
    }
    
    public void faultHandler(Scribe scribe){
	System.out.println("DEBUG :: ControlAttachMessage Failed ");
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




