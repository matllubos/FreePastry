package rice.splitstream.testing;


import rice.*;
import rice.splitstream.*;

import rice.past.*;
import rice.past.messaging.*;

import rice.pastry.*;
import rice.pastry.dist.*;
import rice.pastry.direct.*;
import rice.pastry.standard.*;
import rice.pastry.security.*;
import rice.pastry.routing.*;

import rice.scribe.*;
import rice.scribe.testing.*;

import java.util.*;
import java.io.*;
import java.net.*;
import java.io.Serializable;
import java.security.*;


public class DirectSplitStreamTestApp implements ISplitStreamApp, Observer{

    private ISplitStream m_splitstream = null;
    
    private Scribe m_scribe = null;
    
    public int m_appIndex;

    public int m_numRecv;
    /** 
     * HashTable of channels created/attached by this application.
     * Contains mapping from channelId -> Channel objects
     */
    private Hashtable m_channels = null;

    public int OUT_BW = 16;

    public DirectSplitStreamTestApp(ISplitStream splitstream, int index){
	m_splitstream = splitstream;
	m_appIndex = index;
	m_channels = new Hashtable();
	m_scribe = (Scribe) ((SplitStreamImpl)m_splitstream).getScribe();
	m_numRecv = 0;
    }

    /**
    * This is a call back into the application
    * to notify it that one of the stripes was unable to
    * to find a parent, and thus unable to recieve data.
    */
    public void handleParentFailure(Stripe s){
    }

    /**
     * Observer Implementation
     */
    public void update(Observable o, Object arg){
	//ChannelId cid = (ChannelId) arg;
	//StripeId id = (StripeId)arg;
	Stripe str = (Stripe)o;
	//System.out.println("Node "+getNodeId()+" app "+m_appIndex+" recieved on"+str.getStripeId());
	m_numRecv++;
    }

    /**
     * Create a channel with given channelId
     *
     */
    public ChannelId createChannel(int numStripes, String name){
	Channel channel = m_splitstream.createChannel(numStripes, name);
	m_channels.put(channel.getChannelId(), channel);
	channel.configureChannel(OUT_BW);
	return channel.getChannelId();
    }

    public void attachChannel(ChannelId channelId){
	Channel channel = m_splitstream.attachChannel(channelId);
	m_channels.put(channel.getChannelId(), channel);
	channel.configureChannel(OUT_BW);
	return;
    }

    public int showBandwidth(ChannelId channelId){
	Channel channel = (Channel)m_channels.get(channelId);
	BandwidthManager bandwidthManager = channel.getBandwidthManager();
	//System.out.println("Channel " + channelId + " has " +
	//			   bandwidthManager.getUsedBandwidth(channel) + " children "); 
	return bandwidthManager.getUsedBandwidth(channel);
    }

    /**
     * Sends data on all the stripes of channel represented by channelId.
     */
    public void sendData(ChannelId channelId, DirectSplitStreamTest test){
	Channel send = (Channel) m_channels.get(channelId);
	System.out.println("No of subscribed stripes "+send.getNumSubscribedStripes());

	for(int i = 0; i < send.getNumStripes(); i++){
	//for(int i = 0; i < 1; i++){
	    StripeId stripeId = send.getStripes()[i];
	    Stripe stripe = send.joinStripe(stripeId, this);
	    //stripe.backdoorSend(channelId);
	    
	    OutputStream out = stripe.getOutputStream();
	    //System.out.println("Sending on Stripe " + stripeId);
	    String str = stripe.toString();
	    //byte[] toSend = "Hello".getBytes() ;
	    byte[] toSend = str.getBytes() ;
	    try{
		out.write(toSend, 0, toSend.length );
	    }
	    catch(IOException e){
		e.printStackTrace();
	    }
	    
	    while(test.simulate());
	}

    }

    /**
     * Join all stripes associated with the given channeld
     */
    public void joinChannelStripes(ChannelId channelId){
	Channel channel =  (Channel) m_channels.get(channelId);
	
	// Join all the stripes associated with the channel
	while(channel.getNumSubscribedStripes() < channel.getNumStripes()){
	    Stripe stripe = channel.joinAdditionalStripe(this);
	    //System.out.println("Node "+getNodeId()+" Joining stripe "+stripe.getStripeId());
	}
    }

    public void splitstreamIsReady(){
       System.out.println("SplitStream is Ready");
    }

    public void channelIsReady(ChannelId channelId){
	// upcall from channel object saying that it is ready.
    }

    public boolean channelReady(ChannelId channelId){
	Channel channel =  (Channel) m_channels.get(channelId);
	return channel.isReady();
    }

    public boolean isRoot(NodeId topicId){
	return m_scribe.isRoot(topicId);
    }

    public Scribe getScribe(){
	return m_scribe;
    }


    public StripeId[] getStripeIds(ChannelId channelId){
	Channel channel = (Channel) m_channels.get(channelId);
	return channel.getStripes();
    }

    public SpareCapacityId getSpareCapacityId(ChannelId channelId){
	Channel channel = (Channel) m_channels.get(channelId);
	return channel.getSpareCapacityId();
    }

    public int getNumStripes(ChannelId channelId){
	Channel channel = (Channel) m_channels.get(channelId);
	return channel.getNumStripes();
    }
    
    public NodeId getNodeId(){
	return m_scribe.getNodeId();
    }

    public StripeId getPrimaryStripeId( ChannelId channelId )
    {
	Channel channel = (Channel) m_channels.get(channelId);
        Stripe primary_stripe = channel.getPrimaryStripe();
        return primary_stripe.getStripeId();
    }

    public RoutingTable getRoutingTable( ChannelId channelId )
    {
	Channel channel = (Channel) m_channels.get(channelId);
	return channel.getSplitStream().getRoutingTable();
    }

}





