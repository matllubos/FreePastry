package rice.splitstream;
import java.io.*;
import rice.scribe.*;
import rice.scribe.messaging.*;
import rice.splitstream.messaging.*;
import rice.pastry.*;
import rice.pastry.client.*;
import rice.pastry.messaging.*;
import rice.pastry.security.*;
/*
 * @(#) SplitStream.java
 *
 */

/**
 * This is the implementing class of the SplitStream service 
 */
public class SplitStreamImpl implements ISplitStream, IScribeApp,IScribeObserver{
    private IScribe scribe = null;
    private BandwidthManager bandwidthManager = new BandwidthManager();
    private Credentials credentials = new PermissiveCredentials();
    private PastryNode node; 
    public SplitStreamImpl(PastryNode node, IScribe scribe){
 	this.scribe = scribe;  
 	this.scribe.registerScribeObserver(this);
	this.scribe.registerApp(this);
        this.node = node;
   }

   /**
    * This method is used by a peer who wishes to distribute the content
    * using SplitStream. It creates a Channel Object consisting of numStripes
    * number of Stripes, one for each strips content. A Channel object is
    * responsible for implementing SplitStream functionality, like maintaing
    * multiple multicast trees, bandwidth management and discovering parents
    * having spare capacity. One Channel object should be created for each 
    * content distribution which wishes to use SplitStream. 
    * @param numStripes - number of parts into which the content will
    *        be striped, each part is a Stripe.
    * @return an instance of a Channel class. 
    */
   public Channel createChannel(int numStripes){
	System.out.println("Channel: Creating a new channel, numStripes = " + numStripes);
	return (new Channel(numStripes, scribe, credentials ,bandwidthManager, node));
   }
   /**
    * This method is used by peers who wish to listen to content distributed 
    * by some other peer using SplitStream. It attaches the local node to the 
    * Channel which is being used by the source peer to distribute the content.
    * Essentially, this method finds out the different parameters of Channel
    * object which is created by the source, (the peer distributing the content)
    *, and then creates a local Channel object with these parameters and
    * returns it.  
    * This is a non-blocking call so the returned Channel object may not be 
    * initialized with all the parameters, so applications should wait for 
    * channelIsReady() notification made by channels when they are ready. 
    * @param channelId - Identifier of channel to which local node wishes
    *  to attach to. 
    * @return  An instance of Channel object.
    */
   public Channel attachChannel(ChannelId channelId){
     System.out.println("Attempting to attach to Channel " + channelId);
     return (new Channel(channelId, scribe, credentials, bandwidthManager, node));
   }
   /**
    * Handles the upcall generated by Scribe when a new topicID is 
    * created. SplitStream can generate a channel as appropriate.
    * @param The channelId for the created channel. 
    * This was designed per suggestion by Atuhl, he is going to 
    * add the appropriate hooks into Scribe.
    */
   public void handleSubscribe(ChannelId channelId){
	/* Does this really work on subscribe? */
        System.out.println("A create channel message has been recieved!");
   }
   public BandwidthManager getBandwidthManager(){return null;}
   public void setBandwidthManager(){}
   /** - IScribeObserver Implementation -- */
   public void update(Object topicId){
 	scribe.join((NodeId) topicId, this, credentials);	
   } 

   /** - IScribeApp Implementation -- */
   public void faultHandler(ScribeMessage msg, NodeHandle faultyParent){}
   public void forwardHandler(ScribeMessage msg){}
   public void receiveMessage(ScribeMessage msg){
     /* Check the type of message */
     /* then make call accordingly */
     System.out.println("Recieved message");
   }
   public void scribeIsReady(){
     //System.out.println("Scribe is Ready");
   }
   public void subscribeHandler(NodeId topicId, 
                               NodeHandle child, 
                               boolean wasAdded,  
                               Serializable data){
     NodeId[] nodeData = (NodeId[]) data;
     if(nodeData!=null){
   			
	/* Clean This up */
	StripeId[] stripeId = new StripeId[nodeData.length - 2];
	ChannelId channelId = new ChannelId(nodeData[0]);
	for(int i = 1; i < nodeData.length -1; i++){
		stripeId[i-1] = new StripeId(nodeData[i]);
	}


        SpareCapacityId spareCapacityId = new SpareCapacityId(nodeData[nodeData.length -1]);
	/* Clean This up */

	new Channel(channelId, stripeId, spareCapacityId, scribe, bandwidthManager, node);

     }
     else
	System.out.println("Data in Packet is null!");
 
   }
}

