package rice.splitstream;

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
 * @author Ansley Post
 */
public class BandwidthManager{

    /**
     * This is the default number of outgoing bandwidth that a channel may have
     * if no call to setDefaultBandwidth has been made. Channels may 
     * individually call configureChannel to change the number of outgoing bandwidth
     * it may take on.
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
     * Define the Default Bandwidth for a newly created Channel 
     * @param the limit to the number of children a channel may have by default
     *
     */
    public void setDefaultBandwidth(int out){
	this.defaultBandwidth = out;
    }


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
	usedBandwidth.put(channel,new Integer(oldBandwidth - 1));
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


