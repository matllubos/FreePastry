package rice.pastry.direct;

import rice.environment.logging.Logger;
import rice.pastry.PastryNode;
import rice.pastry.messaging.Message;

/**
   * 
   * @version $Id: EuclideanNetwork.java 2561 2005-06-09 16:22:02Z jeffh $
   * @author amislove
   */
  class MessageDelivery implements Delivery {
    protected Message msg;
    protected DirectPastryNode node;    
    protected Logger logger;
    protected int seq;
    
    /**
     * Constructor for MessageDelivery.
     */
    public MessageDelivery(Message m, DirectPastryNode pn) {
      msg = m;
      node = pn;
      this.seq = pn.seq++;
      
      // Note: this is done to reduce memory thrashing.  There are a ton of strings created
      // in getLogger(), and this is a really temporary object.
      logger = pn.getLogger();
//      logger = pn.getEnvironment().getLogManager().getLogger(MessageDelivery.class, null);
    }

    public void deliver() {
      if (logger.level <= Logger.FINE) logger.log("MD: deliver "+msg+" to "+node);
      node.receiveMessage(msg);
      
      
//      if (isAlive(msg.getSenderId())) {
//        environment.getLogManager().getLogger(EuclideanNetwork.class, null).log(Logger.FINER, 
//            "delivering "+msg+" to " + node);
//        node.receiveMessage(msg);
//      } else {
//        environment.getLogManager().getLogger(EuclideanNetwork.class, null).log(Logger.INFO, 
//            "Cant deliver "+msg+" to " + node + "because it is not alive.");        
//      }
    } 
    
    public int getSeq() {
      return seq; 
    }
  }