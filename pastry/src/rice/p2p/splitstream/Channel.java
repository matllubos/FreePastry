package rice.p2p.splitstream;

import java.io.Serializable;
import java.util.*;

import rice.scribe.*;
import rice.scribe.messaging.*;

import rice.pastry.*;
import rice.pastry.client.*;
import rice.pastry.messaging.*;
import rice.pastry.standard.*;
import rice.pastry.security.*;

import rice.splitstream2.messaging.*;

/** 
 * The channel controls all the meta  data associated with a group of 
 * stripes. It contains the stripes themselves plus any sparecapcity groups
 * associated with the group of stripes.  It also manages the amount of
 * bandwidth that is used by this collection of stripes.  A Channel is 
 * created by giving it a name which is then hashed to come up with a 
 * channelId which uniquely identifies this channel. If other nodes want
 * to join the channel they attach to it. ( Join the scribe group ) 
 *
 * This is the channel object that represents a group of stripes in
 * SplitStream.
 *
 * @version $Id$
 * @author Ansley Post
 * @author Atul Singh
 */
public class Channel{

    /**
     * ChannelId for this channel 
     */
    private ChannelId channelId = null;

    /**
     * The Node id the spare capacity tree is rooted at.
     */
    private SpareCapacityId spareCapacityId = null;

    /**
     * The hashtable mapping StripeId -> Stripes 
     */
    private Hashtable stripeIdTable = new Hashtable();

    /**
     * A vector containing all of the subscribed stripes for this channel
     */
    private Vector subscribedStripes = new Vector();

    /**
     * The primary stripe for this node.
     */
    private Stripe primaryStripe = null;

    /**
     * The number of stripes contained in this channel.
     */
    private int numStripes = 0;

    /**
     * The splitStreamImpl object associated with this node, this is
     * needed to have access to the pastry messages
     */
    private SplitStreamImpl splitStream = null;


    /**
     * The bandwidth manager for this channel, responsible for 
     * keeping track of the number of children, and then deciding
     * when to take on children.
     */
    private BandwidthManager bandwidthManager = null;


    /**
     * Random number
     */
    private static Random random = new Random();


    /**
     * Constructor to create a new channel from scratch
     * 
     * @param numStripes the number of stripes this channel will contain
     * @param name the Name to be associated with this channel
     * @param scribe the scribe service this Channel will utilize
     * @param cred the credentials associated with this user
     * @param bandwidthManager the object that controls bw utilization
     * @param splitStream the splitStream instance for this node 
     *
     */
    public Channel(int numStripes, String name, BandwidthManager bandwidthManager, SplitStreamImpl splitStream){
  
        /* Initialize Member variables */
        this.bandwidthManager = bandwidthManager;
        this.numStripes = numStripes;
        this.splitStream = splitStream;

        /*Create the topic for the Channel */
        channelId = (ChannelId) splitStream.createId(name);

        /* Create the Spare Capacity Group */
        spareCapacityId = (SpareCapacityId)
                              splitStream.createId(name + "SPARE_CAPACITY");


        /* Create the stripe group(s)      */
        StripeId[] ids = (StripeId []) 
                         splitStream.createIds(name + "STRIPES" , numStripes);

        /* Create the stripes */
        for(int i = 0; i < numStripes; i++){
           Stripe s = new Stripe(ids[i], this);
           stripeIdTable.put(ids[i], s);  
        }

        /* verifies that everything has been created */
        channelCreationCheck();
    }

    /**
     * Gets the bandwidth manager for this channel.
     * @return BandwidthManager the BandwidthManager for this channel
     */
    public BandwidthManager getBandwidthManager(){
	return bandwidthManager;
    }

    /**
     * Gets the channelId for this channel
     * @return ChannelId the channelId for this channel
     */
    public ChannelId getChannelId(){
	return channelId;
    } 

    /** 
     * A channel consists of a number of stripes. This number is determined
     * at the time of content creation. Note that a content receiver does not
     * necessarily need to receive all stripes in order to view the content.
     * @return An array of all StripeIds associated with this channel
     */ 
    public StripeId[] getStripes(){
	Object[] obj = stripeIdTable.keySet().toArray();	
	StripeId[] temp = new StripeId[obj.length];
	for(int i = 0; i < obj.length; i++){
	    temp[i] = (StripeId) obj[i];
	}
	return (temp);
    }
   

    /**
     * At any moment a node is subscribed to at least 1 but possibly
     * more stripes. They will always be subscribed to their primary
     * Stripe.
     * @return Vector the Stripes this node is subscribed to.
     */
    public Vector getSubscribedStripes(){
 	return(subscribedStripes); 
    }

    /**
     * The primary stripe is the stripe that the user must have.
     * @return Stripe The Stripe object that is the primary stripe.
     */ 
    public Stripe getPrimaryStripe(){
	return primaryStripe;
    }

    /**
     * Returns a random stripe from this channel that the user is not
     * currently subscribed to
     * @param observer the Object that is going to observe the stripe
     * @return Stripe A random stripe
     */
    public Stripe joinAdditionalStripe(Observer observer ){
       /* Should join any stripe, not particular */
       return null;
    } 

    /**
     * Join a specific Stripe of this channel
     * @param stripeID The stripe to subscribe to
     * @param observer the Object that is going to observe the stripe
     * @return the Stripe joined
     */ 
    public Stripe joinStripe(StripeId stripeId, Observer observer){

	Object tableEntry = stripeIdTable.get(stripeId);
	Stripe stripe = null; 
	stripe = (Stripe) tableEntry;

        /** 
         * Checks to see if we are already subscribed to this
         * stripe, if we are just return.
         */ 
	if(subscribedStripes.contains(stripe))
	    return stripe;

	stripe.joinStripe();	
	stripe.addObserver(observer);
	subscribedStripes.addElement(stripe);
	return(stripe);
    }

    /**
     * Leave a random stripe
     * @return the stripeID left 
     */
    public StripeId leaveStripe(){

        /**
         * If there are no subscriped stripes just return
         * null
         */
	if(subscribedStripes.size() == 0)
	    return null;
       Stripe stripe = (Stripe) subscribedStripes.firstElement();
       subscribedStripes.removeElement(stripe);
       stripe.leaveStripe(); 
       return stripe.getStripeId();
    }
 
    /**
     * Get the number of Stripes this channel is subscribed to.
     * @return the number of Stripes
     */
    public int getNumSubscribedStripes(){
	return subscribedStripes.size();
    }

    /**
     * Get the total number of stripes in this channel
     * @return the total number of stripes
     */
    public int getNumStripes(){
	return numStripes;
    }
  
    protected void messageForChannel(SplitStreamMessage ssm){

    } 

    private void channelCreationCheck(){
         if(getChannelId() == null)
            System.out.println("WARNING: Channel Topic not created");

         if(spareCapacityId == null)
            System.out.println("WARNING: Spare Capacity Topic not created");
        
         /* Add a check for stripe creation */
    }
 }
