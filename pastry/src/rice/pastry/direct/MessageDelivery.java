package rice.pastry.direct;

import rice.environment.logging.Logger;
import rice.pastry.PastryNode;
import rice.pastry.messaging.Message;

/**
   * 
   * @version $Id: EuclideanNetwork.java 2561 2005-06-09 16:22:02Z jeffh $
   * @author amislove
   */
  class MessageDelivery {
    protected Message msg;
    protected DirectPastryNode node;    
    protected Logger logger;
    
    /**
     * Constructor for MessageDelivery.
     */
    public MessageDelivery(Message m, DirectPastryNode pn) {
      msg = m;
      node = pn;
      logger = pn.getEnvironment().getLogManager().getLogger(MessageDelivery.class, null);
    }

    public void deliver() {
      if (logger.level <= Logger.FINE) logger.log("MD: deliver "+msg+" to "+node);
      GeometricNetworkSimulator.currentNode = node;
      node.receiveMessage(msg);
      GeometricNetworkSimulator.currentNode = null;
      
      
//      if (isAlive(msg.getSenderId())) {
//        environment.getLogManager().getLogger(EuclideanNetwork.class, null).log(Logger.FINER, 
//            "delivering "+msg+" to " + node);
//        node.receiveMessage(msg);
//      } else {
//        environment.getLogManager().getLogger(EuclideanNetwork.class, null).log(Logger.INFO, 
//            "Cant deliver "+msg+" to " + node + "because it is not alive.");        
//      }
    } 
  }