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

    Random rng = new Random(PastrySeed.getSeed());
    

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
      
      // first see if we are in the 3rd stage of algorithm for locating parent.
      ScribeContent content = message.getContent();
      if(content != null && (content instanceof SplitStreamContent)){
	  int stage = ((SplitStreamContent)content).getStage();
	  if(stage == SplitStreamContent.STAGE_FINAL){
	      //System.out.println("Stage "+stage);
	      List list = Arrays.asList(children);
	      if(!list.contains(message.getSource())){
		  //System.out.println("CHAOS :: sending drop message to "+message.getSource()+" a node which is not our child, at "+channel.getLocalId());
		  return false;
	      }
	      else{
		  this.scribe.removeChild(message.getTopic(), message.getSource());
		  return true;
	      }
	  }
      }
      

      if(canTakeChild(channel)){
	  return true;
      }
      else{
	  if(!message.getTopic().getId().equals(channel.getPrimaryStripe().getStripeId().getId()))
	      return false;
	  else{
	      if(children.length > 0){
		  NodeHandle victimChild = freeBandwidth(channel, newChild, message.getTopic().getId());
		  if(victimChild.getId().equals(newChild.getId()))
		      return false;
		  else{
		      //System.out.println("Removing child "+victimChild+" at "+channel.getLocalId()+" for topic "
		      //+message.getTopic().getId()+" for new child "+newChild);
		      scribe.removeChild(new Topic(message.getTopic().getId()), victimChild);
		      return true;
		  }
	      }
	      else {
		  Vector res = freeBandwidthUltimate(message.getTopic().getId());
		  if(res != null){
		      scribe.removeChild(new Topic((Id)res.elementAt(1)), (NodeHandle)res.elementAt(0));
		      //scribe.addChild(message.getTopic(),((SubscribeMessage)message).getSubscriber());
		      //System.out.println("Adding child "+((SubscribeMessage)message).getSubscriber() + " for topic "
		      //+message.getTopic()+" at "+getChannel(message.getTopic()).getLocalId()
		      //+" removing child "+(NodeHandle)res.elementAt(0));
		      return true;
		  }
		  else
		      return false;
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
      if(parent != null){
	  if(SplitStreamScribePolicy.getPrefixMatch(message.getTopic().getId(), parent.getId()) > 0)
	      message.addFirst(parent);
	  else
	      message.addLast(parent);
      }
    
    // NEED TO ADD CHILDREN/PARENTS SELECTIVELY HERE

    // we do not want to anycast through our own children
    if(message instanceof SubscribeMessage && !((SubscribeMessage)message).getSubscriber().getId().equals(getChannel(message.getTopic()).getLocalId())){
	Vector good = new Vector();
	Vector bad = new Vector();
	for (int i=0; i<children.length; i++) {
	    if(SplitStreamScribePolicy.getPrefixMatch(message.getTopic().getId(), children[i].getId()) > 0)
		good.add(children[i]);
	    else
		bad.add(children[i]);
	}
	int index;
	while(good.size() > 0){
	    index = rng.nextInt(good.size());
	    message.addFirst((NodeHandle)(good.elementAt(index)));
	    good.remove((NodeHandle)(good.elementAt(index)));
	}
	while(bad.size() > 0){
	    index = rng.nextInt(bad.size());
	    message.addLast((NodeHandle)(bad.elementAt(index)));
	    bad.remove((NodeHandle)(bad.elementAt(index)));
	}
    
    
	
	//if(message instanceof SubscribeMessage){
	
	NodeHandle nextHop = message.getNext();
	// make sure that the next node is alive
	while ((nextHop != null) && (!nextHop.isAlive())) {
	nextHop = message.getNext();
	}
	
	if(nextHop == null){
	    
	    // two cases, either
	    // a) local node is a leaf
	    // b) local node is root for non-prefix match topic,
	    
	    if(this.scribe.isRoot(message.getTopic())){
		//System.out.println(" Local node "+getChannel(message.getTopic()).getLocalId()+" is root for topic "+message.getTopic().getId()+", next hop is null, for subscriber "+((SubscribeMessage)message).getSubscriber());
		Vector res = freeBandwidthUltimate(message.getTopic().getId());
		if(res != null){
		    scribe.removeChild(new Topic((Id)res.elementAt(1)), (NodeHandle)res.elementAt(0));
		    scribe.addChild(message.getTopic(),((SubscribeMessage)message).getSubscriber());
		    //System.out.println("Adding child "+((SubscribeMessage)message).getSubscriber() + " for topic "
		    //+message.getTopic()+" at "+getChannel(message.getTopic()).getLocalId()
		    //+" removing child "+(NodeHandle)res.elementAt(0));
		    return;
		}
		
	    }
	    else{
		
		// If yes, then we are in 3rd stage of our algorithm
		// need to remove myself from the parent
		//System.out.println(" **** 3rd Stage : source "+((SubscribeMessage)message).getSubscriber().getId()+" for stripe "+message.getTopic().getId()+" at "+getChannel(message.getTopic()).getLocalId()+" parent "+parent);
		//System.exit(1);
		//SplitStreamContent ssc = new SplitStreamContent(getChannel(message.getTopic()).getLocalId(), ((SubscribeMessage)message).getSubscriber());
		SplitStreamContent ssc = new SplitStreamContent(SplitStreamContent.STAGE_FINAL);
		message.remove(parent);
		message.addFirst(parent);
		message.setContent(ssc);
	    }
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
  public int getTotalChildren(Channel channel) {
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
    public Vector freeBandwidthUltimate(Id stripeId){
	Channel channel = getChannel(new Topic(stripeId));
	Stripe[] stripes = channel.getStripes();

	// find those stripes which are
	// a) non-primary
	// b) i am not root for them
	// c) have at least one child

	Vector candidateStripes = new Vector();
	Id victimStripeId = null;
	Topic tp;
	for(int i = 0; i < stripes.length; i++){
	    tp = new Topic(stripes[i].getStripeId().getId());
	    /*
	    if(channel.getPrimaryStripe().getStripeId().getId().equals(stripes[i].getStripeId().getId()))
		System.out.println(stripes[i].getStripeId().getId()+" is same as primary stripe "+channel.getPrimaryStripe().getStripeId().getId()+" for "+i);
	    if(this.scribe.isRoot(tp))
		System.out.println("local node is root for "+tp.getId());
	    System.out.println(scribe.getChildren(tp).length+" children for stripe "+tp.getId());
	    */    
	    if(!channel.getPrimaryStripe().getStripeId().getId().equals(stripes[i].getStripeId().getId()) && !this.scribe.isRoot(tp) && scribe.getChildren(tp).length > 0)
		candidateStripes.add(stripes[i].getStripeId().getId());
	}

	if(candidateStripes.size() == 0){
	    for(int i = 0; i < stripes.length; i++){
		tp = new Topic(stripes[i].getStripeId().getId());
		if(!channel.getPrimaryStripe().getStripeId().getId().equals(stripes[i].getStripeId().getId()) && scribe.getChildren(tp).length > 0 && !stripes[i].getStripeId().getId().equals(stripeId))
		candidateStripes.add(stripes[i].getStripeId().getId());
	    }
	}

	if(candidateStripes.size() > 0){
	    victimStripeId = (Id)candidateStripes.elementAt(rng.nextInt(candidateStripes.size()));

	    NodeHandle[] children;
	    children = this.scribe.getChildren(new Topic(victimStripeId));
	    if(children.length == 0){
		System.out.println("Should not happen");
		System.exit(1);
	    }
	    NodeHandle child = children[rng.nextInt(children.length)];
	    Vector result = new Vector();
	    //System.out.println("Vicitm stripe "+victimStripeId+" , at "+channel.getLocalId()+" victim child "+child.getId());
	    result.addElement(child);
	    result.addElement(victimStripeId);
	    return result;
	}
	return null;
    }

    public NodeHandle freeBandwidth(Channel channel, NodeHandle newChild, Id stripeId){
         
         // This should be implemented depending upon the policies you want
         // to use 
         //
	Stripe primaryStripe = channel.getPrimaryStripe();
	Id localId = channel.getLocalId();

	if(!stripeId.equals(primaryStripe.getStripeId().getId()))
	    System.exit(1);
	
	// We have to drop one of child of the primary stripe.
	NodeHandle[] children = scribe.getChildren(new Topic(primaryStripe.getStripeId().getId()));
	
	// Now, select that child which doesnt share least prefix with local
	// node. 
	
	int minPrefixMatch;
	minPrefixMatch = getPrefixMatch(stripeId, newChild.getId());
	
	Vector victims = new Vector();
	for(int j = 0; j < children.length; j++){
	    NodeHandle c = (NodeHandle)children[j];
	    int match = getPrefixMatch(stripeId, c.getId());
	    if(match < minPrefixMatch){
		victims.addElement(c);
	    }
	}

	if(victims.size() == 0)
	    return newChild;
	else
	    return (NodeHandle)victims.elementAt(rng.nextInt(victims.size()));
    }
    

   

     public static int getPrefixMatch(Id target, Id sample){
	int digitLength = RoutingTable.baseBitLength();
	int numDigits = rice.pastry.Id.IdBitLength / digitLength - 1;

	return (numDigits - ((rice.pastry.Id)target).indexOfMSDD((rice.pastry.Id)sample, digitLength));
    }

}





