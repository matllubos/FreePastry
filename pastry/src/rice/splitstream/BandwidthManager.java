package rice.splitstream;
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
public abstract class BandwidthManager{
     /**
      * This method makes an attempt to free up bandwidth
      * when it is needed. It follows the basic outline as
      * describe above, again not completely defined so left
      * as abstract.
      */ 
     public abstract void freeBandwidth();
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
     private static int DEFAULT_CHILDREN = 20;
} 
