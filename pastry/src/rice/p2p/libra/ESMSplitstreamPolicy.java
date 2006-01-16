package rice.p2p.libra;

import java.util.*;

import rice.p2p.commonapi.*;
import rice.p2p.scribe.*;
import rice.p2p.scribe.messaging.*;
import rice.p2p.splitstream.*;

import rice.pastry.routing.RoutingTable;

/**
 * This class represents SplitStream's policy for Scribe, which only allows children
 * according to the bandwidth manager and makes anycasts first traverse all nodes
 * who have the stripe in question as their primary stripe, and then the nodes
 * who do not.
 *
 * @version $Id: SplitStreamScribePolicy.java,v 1.17 2005/06/23 18:16:52 jeffh Exp $
 * @author Alan Mislove
 * @author Atul Singh
 */
public class ESMSplitstreamPolicy extends SplitStreamScribePolicy {

  protected MySplitstreamClient mySplitstreamClient;

  /**
   * Constructor which takes a splitStream object
   *
   * @param splitStream The local splitstream
   */
  public ESMSplitstreamPolicy(Scribe scribe, SplitStream splitStream, MySplitstreamClient mySplitstreamClient ) {
      super(scribe,splitStream);
    this.mySplitstreamClient = mySplitstreamClient;
  }

    public boolean allowSubscribe(SubscribeMessage message, ScribeClient[] clients, NodeHandle[] children) {
	if(!mySplitstreamClient.topicsInitialized) {
	    return false;
	}
	Channel channel = getChannel(message.getTopic());
	String channelName = (String) mySplitstreamClient.topic2TopicName.get(channel);
	int cNumber = mySplitstreamClient.topicName2Number(channelName);

	/*
	if((!mySplitstreamClient.allTopics[cNumber].isSubscribed) && (!scribe.isRoot(message.getTopic())))  {
	    return false;
	    // The subscribe message by default proceeds towards the root
	}
	*/

	//System.out.println("channel= " + channel);
	NodeHandle newChild = (NodeHandle)message.getSubscriber();
	
	/* do not accept self - wierd case, should not happen */
	if(message.getSubscriber().getId().equals(channel.getLocalId()))
	    return false;
	
	/* first see if we are in the 3rd stage of algorithm for locating parent. */
	ScribeContent content = message.getContent();
	
	/* this occurs if we are in the third stage of locating a parent */
	if (content != null && (content instanceof SplitStreamSubscribeContent)) {
	    int stage = ((SplitStreamSubscribeContent) content).getStage();
	    if (stage == SplitStreamSubscribeContent.STAGE_FINAL) {
		List list = Arrays.asList(children);
		
		if (!list.contains(message.getSource())) {
		    return false;
		} else {
		    this.scribe.removeChild(message.getTopic(), message.getSource());
		    return true;
		}
	    }
	}

	/* see if we can accept */
	if (getTotalChildren(channel) < getMaxChildren(channel.getChannelId())) {
	    return true;
	} else {
	    /* check if non-primary stripe */
	    if ((! message.getTopic().getId().equals(channel.getPrimaryStripe().getStripeId().getId())) &&
		(! scribe.isRoot(message.getTopic()))) {
		return false;
	    } else {
		if (children.length > 0) {
		    NodeHandle victimChild = freeBandwidth(channel, newChild, message.getTopic().getId());
		    
		    /* make sure victim is not subscriber */
		    if (victimChild.getId().equals(newChild.getId())) {
			return false;
		    } else {
			scribe.removeChild(new Topic(message.getTopic().getId()), victimChild);
			return true;
		    }
		} else {
		    /* we must accept, because this is primary stripe */
		    Vector res = freeBandwidthUltimate(message.getTopic().getId());
		    if (res != null) {
			scribe.removeChild(new Topic((Id)res.elementAt(1)), (NodeHandle)res.elementAt(0));
			return true;
		    } else {
			return false;
		    }
		}
	    }
	}
    }


  /**
   * NOTE: We have put a cap on the maximum length of anycast traversal to ensure that the message size does not blow up
   * This method adds the parent and child in such a way that the nodes who have this stripe as
   * their primary strpe are examined first.
   *
   * @param message The anycast message in question
   * @param parent Our current parent for this message's topic
   * @param children Our current children for this message's topic
   */
  public void directAnycast(AnycastMessage message, NodeHandle parent, NodeHandle[] children) {
      if(message.getVisitedSize() >= LibraTest.MAXSPLITSTREAMANYCASTWILLTRAVERSE) {
	  System.out.println("WARNING: The Splistream Anycast traversal reached the imposed limit of " + LibraTest.MAXSPLITSTREAMANYCASTWILLTRAVERSE);
	  return;
      }

    /* we add parent first if it shares prefix match */
    if (parent != null) {
      if (SplitStreamScribePolicy.getPrefixMatch(message.getTopic().getId(), parent.getId(), splitStream.getStripeBaseBitLength()) > 0)
        message.addFirst(parent);
      else
        message.addLast(parent);
    }

    /* if it's a subscribe */
    if (message instanceof SubscribeMessage) {

      /* First add children which match prefix with the stripe, then those which dont.
         Introduce some randomness so that load is balanced among children. */
      Vector good = new Vector();
      Vector bad = new Vector();

      for (int i=0; i<children.length; i++) {
        if (SplitStreamScribePolicy.getPrefixMatch(message.getTopic().getId(), children[i].getId(), splitStream.getStripeBaseBitLength()) > 0)
          good.add(children[i]);
        else
          bad.add(children[i]);
      }

      int index;

      /* introduce randomness to child order */
      while (good.size() > 0) {
        index = scribe.getEnvironment().getRandomSource().nextInt(good.size());
        message.addFirst((NodeHandle)(good.elementAt(index)));
        good.remove((NodeHandle)(good.elementAt(index)));
      }

      while (bad.size() > 0) {
        index = scribe.getEnvironment().getRandomSource().nextInt(bad.size());
        message.addLast((NodeHandle)(bad.elementAt(index)));
        bad.remove((NodeHandle)(bad.elementAt(index)));
      }

      // Note: At this point check the size of the (Visited + toVisit) to put a cap
      while((message.getToVisitSize() > 0) && ((message.getVisitedSize() + message.getToVisitSize()) >= LibraTest.MAXSPLITSTREAMANYCASTWILLTRAVERSE)) {
	  message.removeLastFromToVisit();
      }		    
      

      NodeHandle nextHop = message.getNext();

      /* make sure that the next node is alive */
      while ((nextHop != null) && (!nextHop.isAlive())) {
        nextHop = message.getNext();
      }

      if (nextHop == null) {
        /* if nexthop is null, then we are in 3rd stage of algorithm for locating parent.
           two cases, either
           a. local node is a leaf
              send message to our parent for dropping us and taking new subscriber
           b. local node is root for non-prefix match topic,
              drop a child from non-primary, non-root stripe and accept the new subscriber */
        if (this.scribe.isRoot(message.getTopic())) {
          Vector res = freeBandwidthUltimate(message.getTopic().getId());

          if (res != null) {
            scribe.removeChild(new Topic((Id)res.elementAt(1)), (NodeHandle)res.elementAt(0));
            scribe.addChild(message.getTopic(),((SubscribeMessage)message).getSubscriber());
            return;
          }
        } else {
          SplitStreamSubscribeContent ssc = new SplitStreamSubscribeContent(SplitStreamSubscribeContent.STAGE_FINAL);
          message.remove(parent);
          message.addFirst(parent);
          message.setContent(ssc);
        }
      } else {
        message.addFirst(nextHop);
      }
    }
  }



     /**
      * Returns the total number of children over all the channels
      *
      * @return The total number of children over all channels
      */
    public int getTotalChildren() {
	int total = 0;
	for(int i=0; i< mySplitstreamClient.NUMGROUPS; i++) {
	    Channel channel = mySplitstreamClient.allTopics[i].channel;    
	    total = total + getTotalChildren(channel);
	}
	return total;
    }

    

}

