package rice.p2p.splitstream;

import java.util.*;

import rice.p2p.commonapi.*;
import rice.p2p.scribe.*;
import rice.p2p.scribe.messaging.*;


//import rice.pastry.NodeId;
import rice.pastry.PastrySeed;
import rice.pastry.routing.RoutingTable;
/**
 * This class represents SplitStream's policy for Scribe, which only allows children
 * according to the bandwidth manager and makes anycasts first traverse all nodes
 * who have the stripe in question as their primary stripe, and then the nodes
 * who do not.
 *
 * @version $Id$
 * @author Alan Mislove
 */
public class SplitStreamScribePolicy implements ScribePolicy {

  /**
   * The default maximum number of children per channel
   */
  public static int DEFAULT_MAXIMUM_CHILDREN = 16;

  /**
   * A reference to this policy's splitstream object
   */
  protected SplitStream splitStream;
  
  /**
   * A reference to this policy's scribe object
   */
  protected Scribe scribe;

  /**
   * A mapping from channelId -> maximum children
   */
  protected Hashtable policy;


  /**
   * Constructor which takes a splitStream object
   *
   * @param splitStream The local splitstream
   */
  public SplitStreamScribePolicy(Scribe scribe, SplitStream splitStream) {
    this.scribe = scribe;
    this.splitStream = splitStream;
    this.policy = new Hashtable();
  }


  /**
   * Gets the max bandwidth for a channel.
   *
   * @param id The id to get the max bandwidth of
   * @return The amount of bandwidth used
   */
  public int getMaxChildren(ChannelId id) {
    Integer max = (Integer) policy.get(id);

    if (max == null) {
      return DEFAULT_MAXIMUM_CHILDREN;
    } else {
      return max.intValue();
    }
  }

  /**
   * Adjust the max bandwidth for this channel.
   *
   * @param id The id to get the max bandwidth of
   * @param children The new maximum bandwidth for this channel
   */
  public void setMaxChildren(ChannelId id, int children) {
    policy.put(id, new Integer(children));
  }

  /**
   * This method only allows subscriptions if we are already subscribed to this topic -
   * if this would cause us to become implicitly subscribed, then it is not allowed.  Additionally,
   * this method asks the bandwidth manager for that topic if the child should be allowed.
   *
   * @param message The subscribe message in question
   * @param children The list of children who are currently subscribed to this topic
   * @param clients The list of clients are are currently subscribed to this topic
   * @return Whether or not this child should be allowed add.
   */
  public boolean allowSubscribe(SubscribeMessage message, ScribeClient[] clients, NodeHandle[] children) {
      Channel channel = getChannel(message.getTopic());
      NodeHandle newChild = (NodeHandle)message.getSubscriber();
      


      if(canTakeChild(channel)){
	  //System.out.println("Accepting request from "+newChild.getId()+" at "+channel.getLocalId()+" for "+message.getTopic().getId());
	  return true;
      }
      else{
	  if(!message.getTopic().getId().equals(channel.getPrimaryStripe().getStripeId().getId()))
	      return false;
	  else{
	      NodeHandle victimChild = freeBandwidth(channel, newChild, message.getTopic().getId());
	      //System.out.println("Received subscription from "+newChild.getId()+" at "+channel.getLocalId()+" victim child "+victimChild.getId()+" for stripe "+message.getTopic().getId()+" getTotalChildren "+getTotalChildren(channel));
	      if(victimChild.getId().equals(newChild.getId()))
		  return false;
	      else{
		  scribe.removeChild(new Topic(message.getTopic().getId()), victimChild);
		  return true;
	      }
	  }
      }
  }

  /**
   * This method adds the parent and child in such a way that the nodes who have this stripe as
   * their primary strpe are examined first.
   *
   * @param message The anycast message in question
   * @param parent Our current parent for this message's topic
   * @param children Our current children for this message's topic
   */
  public void directAnycast(AnycastMessage message, NodeHandle parent, NodeHandle[] children) {
    message.addFirst(parent);

    // NEED TO ADD CHILDREN/PARENTS SELECTIVELY HERE
    for (int i=0; i<children.length; i++) {
	if(SplitStreamScribePolicy.getPrefixMatch(message.getTopic().getId(), children[i].getId()) > 0)
	    message.addFirst(children[i]);
	else
	    message.addLast(children[i]);
    }

    NodeHandle nextHop = message.getNext();
    if(message instanceof SubscribeMessage){
	if(nextHop == null){
	// If yes, then we are in 3rd stage of our algorithm
	    // need to remove myself from the parent
	    //System.out.println(" source "+message.getSource().getId()+" for stripe "+message.getTopic().getId()+" at "+getChannel(message.getTopic()).getLocalId());
	    System.exit(1);
	    SplitStreamContent ssc = new SplitStreamContent(getChannel(message.getTopic()).getLocalId(), ((SubscribeMessage)message).getSubscriber());
	    message.remove(parent);
	    message.addFirst(parent);
	    message.setContent(ssc);
	}
	else
	    message.addFirst(nextHop);
    }
  }

  /**
   * Returns the Channel which contains the stripe cooresponding to the
   * provided topic.
   *
   * @param topic The topic in question
   * @return The channel which contains a cooresponding stripe
   */
  private Channel getChannel(Topic topic) {
    Channel[] channels = splitStream.getChannels();

    for (int i=0; i<channels.length; i++) {
      Channel channel = channels[i];
      Stripe[] stripes = channel.getStripes();

      for (int j=0; j<stripes.length; j++) {
        Stripe stripe = stripes[j];

        if (stripe.getStripeId().getId().equals(topic.getId())) {
          return channel;
        }
      }
    }

    return null;
  }

  /**
   * Returns the total number of children for the given channel
   *
   * @param channel The channel to get the children for
   * @return The total number of children for that channel
   */
  private int getTotalChildren(Channel channel) {
    int total = 0;
    Stripe[] stripes = channel.getStripes();

    for (int j=0; j<stripes.length; j++) {
      total += scribe.getChildren(new Topic(stripes[j].getStripeId().getId())).length;
    }

    return total;
  }

    public boolean canTakeChild(Channel channel){
	return (getTotalChildren(channel) < getMaxChildren(channel.getChannelId()));
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
    public NodeHandle freeBandwidth(Channel channel, NodeHandle newChild, Id stripeId){
        /** 
         * This should be implemented depending upon the policies you want
         * to use 
         */
	Stripe primaryStripe = channel.getPrimaryStripe();
	Stripe[] stripes = channel.getStripes();
	Vector candidateStripes = new Vector();
	Random rng = new Random(PastrySeed.getSeed());
	Id localId = channel.getLocalId();
	NodeHandle victimChild;
	Id victimStripeId;
	
	Vector returnVector;

	if(!stripeId.equals(primaryStripe.getStripeId().getId()))
	    System.exit(1);
	
	// We have to drop one of child of the primary stripe.
	NodeHandle[] children = scribe.getChildren(new Topic(primaryStripe.getStripeId().getId()));
	
	// Now, select that child which doesnt share least prefix with local
	// node. 
	
	int minPrefixMatch;
	int minPrefixMatchIndex = -1;
	
	minPrefixMatch = getPrefixMatch(stripeId, newChild.getId());
	
	Vector victims = new Vector();
	for(int j = 0; j < children.length; j++){
	    NodeHandle c = (NodeHandle)children[j];
	    int match = getPrefixMatch(stripeId, c.getId());
	    //System.out.println("min "+minPrefixMatch +", match "+match+" for child "+c.getId()+" for topic "+stripeId +" new child "+newChild.getId());
	    if(match < minPrefixMatch){
		//minPrefixMatch = match;
		//minPrefixMatchIndex = j;
		victims.addElement(c);
	    }
	}
	/*
	if(minPrefixMatchIndex == -1)
	    victimChild = newChild;
	else
	    victimChild = (NodeHandle)children[minPrefixMatchIndex];
	*/
	if(victims.size() == 0)
	    return newChild;
	else
	    return (NodeHandle)victims.elementAt(rng.nextInt(victims.size()));
	//return victimChild;
	/** 
	 * Go through all non-primary stripes and find all stripes
	 * for which local node has children, these stripes are
	 * favorite candidates for dropping children.
	 */
	//System.out.println("Primary stripe "+primaryStripe.getStripeId()+" new child "+newChild.getId()+" localId "+localNodeId);

	/*
	if(!primaryStripe.getStripeId().getId().equals(stripeId)){
	    victimChild = newChild;
	    victimStripeId = stripeId;
	}
	else {
	    for(int i = 0; i < stripes.length; i++){
		//System.out.println("\nComparing stripe "+stripes[i].getStripeId()+" with primary "+primaryStripe.getStripeId()+" children  "+scribe.getChildren(new Topic(stripes[i].getStripeId().getId())).length);
		if(!stripes[i].getStripeId().getId().equals(primaryStripe.getStripeId().getId())
		   && scribe.getChildren(new Topic(stripes[i].getStripeId().getId())).length > 0){
		    candidateStripes.addElement(stripes[i].getStripeId().getId());
		    //System.out.println("Candidate stripe "+stripes[i].getStripeId()+", children "+scribe.getChildren(new Topic(stripes[i].getStripeId().getId())).length +" new child "+newChild.getId()+" localId "+localNodeId);
		}
	    }
	    //System.exit(1);
	    
	    
	    
	    if(candidateStripes.size() > 0){
		// There is at least one non-primary stripe for which
		// local node has children, hence can drop one of its child
		// Randomly select one such non-primary stripe.
		
		if(candidateStripes.contains(stripeId)){
		    victimChild = newChild;
		    victimStripeId = stripeId;
		}
		else {
		    
		    int j = rng.nextInt(candidateStripes.size());
		    NodeHandle[] children = scribe.getChildren(new Topic((Id)candidateStripes.elementAt(j)));
		    
		    // Now randomly select one of its child.
		    //System.out.println("Children length "+children.length+" candidate stripe "+stripes[j].getStripeId());
		    int k = rng.nextInt(children.length);
		    victimChild = (NodeHandle)children[k];
		    victimStripeId = (Id)candidateStripes.elementAt(j);
		}
	    }
	    else {
		
		
		
		// We have to drop one of child of the primary stripe.
		NodeHandle[] children = scribe.getChildren(new Topic(primaryStripe.getStripeId().getId()));
		
		// Now, select that child which doesnt share least prefix with local
		// node. 
		
		int minPrefixMatch;
		int minPrefixMatchIndex = -1;
		
		minPrefixMatch = getPrefixMatch(localNodeId, newChild.getId());
		
		for(int j = 0; j < children.length; j++){
		    NodeHandle c = (NodeHandle)children[j];
		    int match = getPrefixMatch(localNodeId, c.getId());
		    if(match < minPrefixMatch){
			minPrefixMatch = match;
			minPrefixMatchIndex = j;
		    }
		}
		if(minPrefixMatchIndex == -1)
		    victimChild = newChild;
		else
		    victimChild = (NodeHandle)children[minPrefixMatchIndex];
		victimStripeId = primaryStripe.getStripeId().getId();
	    }
	}
	returnVector = new Vector();
	returnVector.add(victimChild);
	returnVector.add(victimStripeId);
	return returnVector;
	*/
    }
    

   

     public static int getPrefixMatch(Id target, Id sample){
	int digitLength = RoutingTable.baseBitLength();
	int numDigits = rice.pastry.Id.IdBitLength / digitLength - 1;

	return (numDigits - ((rice.pastry.Id)target).indexOfMSDD((rice.pastry.Id)sample, digitLength));
    }

}





