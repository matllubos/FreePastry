package rice.pastry.secureconnection.test;

import rice.p2p.commonapi.Message;
import rice.p2p.scribe.ScribeContent;

/**
 *
 * @author Luboš Mátl
 */
public interface MessageReceiverI {
   
    public void receive(ScribeContent content);
   public void receive(Message content);
  
    
}
