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
 * and there must be some more discussion on which is best. So this
 * class is abstract. The behavior will be decided at a later point
 */
public class BandwidthManager{
     private Hashtable channelBandwidth = null;
     public BandwidthManager(){
	channelBandwidth = new Hashtable();
     }
     /**
      * This method makes an attempt to free up bandwidth
      * when it is needed. It follows the basic outline as
      * describe above, again not completely defined so left
      * as abstract.
      */ 
     public void freeBandwidth(){}
     /**
      * Define the Default Bandwidth for a newly created Channel 
      * @param the limit to the number of children a channel may have
      *
      */
     public void setDefaultChildren(int out){}
     /**
      * Get the default number of children that a channel may have
      * @return the default number of children
      */
     public int getDefaultChildren(){return 0;}
     /**
      * gets current number of Children this child currently has.
      * @return the current number of children
      */
      public int getNumChildren(){return 0;}
     /**
      * gets current number of primary Children this child currently has.
      * @return the current number of primary children
      */
      public int getNumPrimaryChildren(){return 0;}
     /**
      * Determines based upon capacity information whether the 
      * system can take on another child.
      * @return whether we can take on another child 
      */
      public boolean canTakeChild(Channel channel, Stripe s){
         return false;
      }
    /**
     * Registers a channel within the system with the bandwidth manager
     * @param the channel to be added
     */
      public void registerChannel(Channel channel){
	channelBandwidth.put(channel, new Integer(DEFAULT_CHILDREN)); 

      }

      public void adjustBandwidth(Channel channel, int outbandwidth){
       channelBandwidth.put(channel, new Integer(outbandwidth));
      }

      private static int DEFAULT_CHILDREN = 20;
} 
