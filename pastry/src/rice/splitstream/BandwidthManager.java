package rice.splitstream;

import rice.pastry.*;
import rice.pastry.routing.*;
import rice.scribe.*;


import java.util.*;

/**
 * This class is responsible for freeing bandwidth
 * when it is needed. Right now the notion of bandwidth
 * is slightly illdefined. It can be defined in terms of stripes
 * or bytes. This can also be per application, per channel or
 * globally. The first cut at freeing badwidth is handle by 
 * the fact the you can drop children from a stripe. After that 
 * you can start dropping non-primary stripes for each channel. Finally
 * you must come up with some method for dropping channels. You must
 * handle user requests at some higher priority than what is going on
 * in the backround.  There are many ways to wiegh each of these priorities
 * and there must be some more discussion on which is best.
 *
 * @version $Id$
 * @author Ansley Post
 */
public class BandwidthManager{

    /**
     * This is the default number of outgoing bandwidth that a channel may have
     * if no call to setDefaultBandwidth has been made. Channels may 
     * individually call configureChannel to change the number of 
     * outgoing bandwidth it may take on.
     */
    private static int DEFAULT_BANDWIDTH = 16;


    /**
     * Hashtable to keep track of all Channels registered with the bandwidth
     * managers max bandwidth.
     */
    private Hashtable maxBandwidth = null;

    /**
     * Hashtable to keep track of all Channels registered with the bandwidth
     * managers used bandwidth.
     */
    private Hashtable usedBandwidth = null;

    /**
     * The number of outgoing bandwidht a channel may have, if no value has been
     * specified. Maybe adjusted later by calling configure channel
     */ 
    private int defaultBandwidth = DEFAULT_BANDWIDTH;

    /**
     * Constructor
     */
    public BandwidthManager(){
	maxBandwidth = new Hashtable();
	usedBandwidth = new Hashtable();
    }

    /**
     * This method makes an attempt to free up bandwidth
     * when it is needed. It follows the basic outline as
     * describe above,not completely defined.
     *
     * @return boolean whether bandwidth was able to be freed
     */ 
    public boolean freeBandwidth(){
        /** 
         * This should be implemented depending upon the policies, you want
         * to use 
         */
	return false;
    }

     /**
     * This method makes an attempt to free up bandwidth
     * when it is needed. It follows the basic outline as
     * describe above,not completely defined.
     *
     * @param channel The channel whose bandwidth usage needs
     *                to be controlled.
     *
     * @return A vector containing the child to be dropped and
     *         the corresponding stripeId
     */ 
    public Vector freeBandwidth(Channel channel){
        /** 
         * This should be implemented depending upon the policies, you want
         * to use 
         */
	Stripe primaryStripe = channel.getPrimaryStripe();
	StripeId[] stripeIds = channel.getStripes();
	Vector candidateStripes = new Vector();
	Scribe scribe = channel.getScribe();
	Random rng = new Random(PastrySeed.getSeed());

	NodeHandle victimChild;
	StripeId victimStripeId;
	
	Vector returnVector;
	/** 
	 * Go through all non-primary stripes and find all stripes
	 * for which local node has children, these stripes are
	 * favorite candidates for dropping children.
	 */
	for(int i = 0; i < stripeIds.length; i++){
	    //System.out.println("Primary Stripe "+primaryStripe.getStripeId()+i+"-th stripId "+stripeIds[i]);
	    if(!stripeIds[i].equals((NodeId)primaryStripe.getStripeId())
	       && scribe.getChildren((NodeId)stripeIds[i]) != null &&
	       scribe.getChildren((NodeId)stripeIds[i]).size() > 0)
		candidateStripes.addElement(stripeIds[i]);
	}

	if(candidateStripes.size() > 0){
	    //System.out.println("FREE-BM :: Non-primary stripe children exist");
	    // There is at least one non-primary stripe for which
	    // local node has children, hence can drop one of its child
	    // Randomly select one such non-primary stripe.
	    int j = rng.nextInt(candidateStripes.size());
	    Vector children = scribe.getChildren((NodeId)candidateStripes.elementAt(j));
	    
	    // Now randomly select one of its child.
	    int k = rng.nextInt(children.size());
	    victimChild = (NodeHandle)children.elementAt(k);
	    victimStripeId = (StripeId)candidateStripes.elementAt(j);
	}
	else {
	    // We have to drop one of child of the primary stripe.
	    Vector children = scribe.getChildren((NodeId)primaryStripe.getStripeId());
	    //System.out.println("FREE-BM :: Should drop primary stripe children, number of such children "+children.size());
	    // Now, select that child which doesnt share a prefix with local
	    // node.
	    Vector candidateChildren = new Vector();
	    int numDigits = channel.getSplitStream().getRoutingTable().numRows() - 1;
	    int digitLength = RoutingTable.baseBitLength();
	    
	    //System.out.println("numDigits "+numDigits+" digitLength "+digitLength);
	    // Start comparing the children's digits with local node's
	    // digits, starting with most significant digit going all the
	    // way to least signifcant digit 
	    //System.out.println("Local node "+channel.getSplitStream().getNodeId());
	    for(int k = 0; k < numDigits; k++){
		for(int j = 0; j < children.size(); j++){
		    NodeHandle c = (NodeHandle)children.elementAt(j);
		    //System.out.println("Comparing with child "+c.getNodeId());
		    // Compare the k-th digit from the most significant digit
		    if(channel.getSplitStream().getNodeId().getDigit(numDigits - k, digitLength) != 
		       c.getNodeId().getDigit(numDigits - k, digitLength))
			candidateChildren.add(c);
		}
		// If we find some children differing at kth digit, no need to look
		// at (k-1)-th digit
		if(candidateChildren.size() > 0)
		    break;
	    }
	    
	    int m = rng.nextInt(candidateChildren.size());
	    victimChild = (NodeHandle)candidateChildren.elementAt(m);
	    victimStripeId = primaryStripe.getStripeId();
	    
	}
	returnVector = new Vector();
	returnVector.add((NodeHandle)victimChild);
	returnVector.add((StripeId)victimStripeId);
	return returnVector;
    }
    

    /**
     * Define the Default Bandwidth for a newly created Channel 
     * @param the limit to the number of children a channel may have by default
     *
     */
    public void setDefaultBandwidth(int out){
	this.defaultBandwidth = out;
    }

    /**
     * Gets the value of the default bandwidth for a newly created channel
     * @return int the value of defaultBandwidth
     */
    public int getDefaultBandwidth(){
        return defaultBandwidth;
    }
     
    /**
     * Determines based upon capacity information whether the 
     * system can take on another child.
     * @return whether we can take on another child 
     */
    public boolean canTakeChild(Channel channel){
        return(getUsedBandwidth(channel) < getMaxBandwidth(channel)); 
    }

    /**
     * Registers a channel within the system with the bandwidth manager
     * @param the channel to be added
     */
    public void registerChannel(Channel channel){
        if(usedBandwidth.get(channel) != null){
	    System.out.println("Resetting BW in error");
        }
	maxBandwidth.put(channel, new Integer(DEFAULT_BANDWIDTH)); 
	usedBandwidth.put(channel, new Integer(0)); 

    }

    /**
     * Adjust the max bandwidth for this channel.
     * @param Channel the channel to adjust
     * @param int the new max bandwidth
     */  
    public void adjustBandwidth(Channel channel, int outbandwidth){
        maxBandwidth.put(channel, new Integer(outbandwidth));
    }

    /**
     * Change the amount of bandwidth a channel is considered to be
     * using. 
     * @param Channel the channel whose bandwidth changed.
     */
    public void additionalBandwidthUsed(Channel channel){

	int oldBandwidth = ((Integer)usedBandwidth.get(channel)).intValue();
	int newBandwidth = oldBandwidth+1;
	usedBandwidth.put(channel,new Integer(newBandwidth));
    }


    /**
     * Change the amount of bandwidth a channel is considered to be
     * using. 
     * @param Channel the channel whose bandwidth changed.
     */
    public void additionalBandwidthFreed(Channel channel){
	int oldBandwidth = ((Integer)usedBandwidth.get(channel)).intValue();
	int newBandwidth = oldBandwidth - 1;
	if(newBandwidth < 0 ){
	    //System.out.println("XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX");
	    newBandwidth = 0;
	}
	usedBandwidth.put(channel,new Integer(newBandwidth));
    }

    /**
     * Gets the bandwidth a channel is currently using.
     * @param Channel the channel whose bandwidth we want
     * @return int the bandwidth used
     */
    public int getUsedBandwidth(Channel channel){
	int bandwidth = ((Integer)usedBandwidth.get(channel)).intValue();
	return bandwidth;
    }

    /**
     * Gets the max bandwidth for a channel.
     * @param Channel the channel whose bandwidth we want
     * @return int the bandwidth used
     */
    public int getMaxBandwidth(Channel channel){
	int bandwidth = ((Integer)maxBandwidth.get(channel)).intValue();
	return bandwidth;
    }
      
} 


