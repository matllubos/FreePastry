package rice.p2p.splitstream;

import java.io.*;
import java.math.*;
import java.util.*;

import rice.p2p.commonapi.*;
import rice.p2p.scribe.*;

/**
 * The channel controls all the meta data associated with a group of stripes. It contains the
 * stripes themselves plus any sparecapcity groups associated with the group of stripes. It also
 * manages the amount of bandwidth that is used by this collection of stripes. A Channel is created
 * by giving it a name which is then hashed to come up with a channelId which uniquely identifies
 * this channel. If other nodes want to join the channel they attach to it. ( Join the scribe group
 * ) This is the channel object that represents a group of stripes in SplitStream.
 *
 * @version $Id$
 * @author Ansley Post
 * @author Alan Mislove
 */
public class Channel {

  /**
   * The default number of stripes to create
   */
  public static int STRIPE_BASE = 4;
  
  /**
   * ChannelId for this channel
   */
  protected ChannelId channelId;

  /**
   * The list of stripeIds for this channel
   */
  protected Stripe[] stripes;

    protected Id localId;

  /**
   * Constructor to create a new channel from scratch
   *
   * @param splitStream the splitStream instance for this node
   * @param channelId DESCRIBE THE PARAMETER
   * @param scribe DESCRIBE THE PARAMETER
   */
  public Channel(ChannelId channelId, Scribe scribe, IdFactory factory, Id localId) {

    /*
     *  Initialize Member variables
     */
    this.channelId = channelId;
    this.localId = localId;

    /*
     *  Create the stripe id and stripe arrays
     */
    StripeId[] stripeIds = generateStripeIds(channelId, factory);
    stripes = new Stripe[stripeIds.length];

    /*
     *  Create the stripes
     */
    for (int i = 0; i < stripeIds.length; i++) {
      stripes[i] = new Stripe(stripeIds[i], scribe, this);
    }

    
  }

    /**
     * Gets the local node id.
     */
    public Id getLocalId(){
	return localId;
    }

  /**
   * Gets the channelId for this channel
   *
   * @return ChannelId the channelId for this channel
   */
  public ChannelId getChannelId() {
    return channelId;
  }

  /**
   * At any moment a node is subscribed to at least 1 but possibly more stripes. They will always be
   * subscribed to their primary Stripe.
   *
   * @return Vector the Stripes this node is subscribed to.
   */
  public Stripe[] getStripes() {
    return stripes;
  }

  /**
   * The primary stripe is the stripe that the user must have.
   *
   * @return Stripe The Stripe object that is the primary stripe.
   */
  protected Stripe getPrimaryStripe() {
      for(int i = 0; i < stripes.length; i++){
	  if(SplitStreamScribePolicy.getPrefixMatch(this.localId, stripes[i].getStripeId().getId()) > 0)
	      return stripes[i];
      }
    return null;
    //INSERT LOGIC HERE
  }

  /**
   * Creates and returns the Ids associated with the provided channelId
   *
   * @param channelId The id of the channel
   * @return The array of stripeIds based on this channelId
   */
  protected static StripeId[] generateStripeIds(ChannelId id, IdFactory factory) {
    int num = (int) Math.pow(2, STRIPE_BASE);
    StripeId[] stripeIds = new StripeId[num];

    for (int i=0; i<num; i++) {
      byte[] array = id.getId().toByteArray();
      stripeIds[i] = new StripeId(factory.buildId(process(array, STRIPE_BASE, i)));
    }

    return stripeIds; 
  }

  /**
   * Returns a new array, of the same length as array, with the first
   * base bits set to represent num.
   *
   * @param array The input array
   * @param base The number of bits to replace
   * @param num The number to replace the first base bits with
   * @return A new array
   */
  private static byte[] process(byte[] array, int base, int num) {
    int length = array.length * 8;
    BigInteger bNum = new BigInteger(num + "");
    bNum = bNum.shiftLeft(length-base);
    BigInteger bArray = new BigInteger(1, switchEndian(array));
    
    for (int i=length-1; i>length-base-1; i--) {
      if (bNum.testBit(i)) {
        bArray = bArray.setBit(i);
      } else {
        bArray = bArray.clearBit(i);
      }
    }
    int x = 8;
    BigInteger bNum2 = new BigInteger(x + "");
    bNum2 = bNum2.shiftLeft(length-base);
    for(int i = length-1; i>length-base; i--){
	if(bNum2.testBit(i)){
	    bArray = bArray.setBit(i - base);
	}else{
	    bArray = bArray.clearBit(i - base);
	}
    }

    byte[] newArray = bArray.toByteArray();
    byte[] result = new byte[array.length];
    
    if (newArray.length <= array.length) {
      System.arraycopy(newArray, 0, result, result.length-newArray.length, newArray.length);
    } else {
      System.arraycopy(newArray, newArray.length-array.length, result, 0, array.length);
    }
    
    return switchEndian(result);
  }

  private static byte[] switchEndian(byte[] array) {
    byte[] result = new byte[array.length];

    for (int i=0; i<result.length; i++) {
      result[i] = array[result.length-1-i];
    }

    return result;
  }

  
}
