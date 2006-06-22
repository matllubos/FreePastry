/*
 * Created on Jul 13, 2005
 */
package rice.p2p.splitstream.testing;

import rice.p2p.commonapi.*;
import rice.p2p.splitstream.*;
import rice.p2p.util.MathUtils;
import rice.pastry.PastryNode;
import rice.selector.TimerTask;

/**
 * @author Jeff Hoye
 */
public class MySplitStreamClient implements SplitStreamClient {

  public static int SEND_PERIOD = 15000;//1000;
  
  // 160/8 id + 4 seq
  public static int msgSize = 24; // min size is 24
  
  /**
   * The underlying common api node
   *  
   */
  private PastryNode n = null;

  /**
   * The stripes for a channel
   *  
   */
  private Stripe[] stripes;

  /**
   * The channel to be used for this test
   *  
   */
  private Channel channel;

  /**
   * The SplitStream service for this node
   *  
   */
  private SplitStream ss;

  private int numMesgsReceived = 0;

  private SplitStreamScribePolicy policy = null;

  private String instance;
  
  TimerTask publishTask;
  
  int curSeq = 0;
  
  public MySplitStreamClient(PastryNode n, String instance) {
    this.n = n;
    this.instance = instance;
    this.ss = new SplitStreamImpl(n, instance);
  }

  public void attachChannel(ChannelId cid) {
    System.out.println("Attaching to Channel " + cid + " at "+n.getEnvironment().getTimeSource().currentTimeMillis());
    if (channel == null)
      channel = ss.attachChannel(cid);
    getStripes(); // implicitly sets the stripes parameter
  }

  public void subscribeToAllChannels() {
    for (int i = 0; i < stripes.length; i++) {
      stripes[i].subscribe(this);
    } 
  }
  
  public Stripe[] getStripes() {
    stripes = channel.getStripes();
    return stripes;
  }

  public boolean shouldPublish() {
    try {
      IdRange range = n.getLeafSet().range(n.getLocalHandle(), 0);
     
      return range.containsId(rice.pastry.Id.build());
    } catch (RangeCannotBeDeterminedException rcbde) {
      return true; 
    }
  }
  
  public void publishNext() {
    if (shouldPublish()) {
      publish(n.getId(), curSeq);
      curSeq++;
    }
  }
  
  public void publish(Id id, int seq) {
    System.out.println("MSSC.publish("+id+":"+seq+"):"+n.getEnvironment().getTimeSource().currentTimeMillis());
    byte[] msg = new byte[msgSize];
    byte[] head = MathUtils.intToByteArray(seq);
    System.arraycopy(head, 0, msg, 0, 4);
    byte[] idArray = id.toByteArray();
    System.arraycopy(idArray, 0, msg, 4, 20);
    rice.pastry.Id.build(idArray);
    publishAll(msg);
  }
  
  public void publishAll(byte[] b) {
    for (int i = 0; i < stripes.length; i++) {
      publish(b, stripes[i]);
    }
  }

  public void publish(byte[] b, Stripe s) {
    s.publish(b);
  }
  
  public void joinFailed(Stripe s) {
    System.out.println("MSSC.joinFailed("+s+"):"+n.getEnvironment().getTimeSource().currentTimeMillis());
  }

  public void deliver(Stripe s, byte[] data) {
    byte[] theInt = new byte[4];
    System.arraycopy(data, 0, theInt, 0, 4);
    int seq = MathUtils.byteArrayToInt(theInt);
    
    byte[] material = new byte[20];
    System.arraycopy(data, 4, material, 0, 20);
    Id publisher = rice.pastry.Id.build(material);
        
    Id stripeId = (rice.pastry.Id) (s.getStripeId().getId());
    String stripeStr = stripeId.toString().substring(3, 4);
    System.out.println("deliver("+stripeStr+","+publisher+","+seq+"):"+n.getEnvironment().getTimeSource().currentTimeMillis()+" from "+s.getParent());
  }

  /**
   * 
   */
  public void startPublishTask() {
    publishTask = new TimerTask() {
      public void run() {        
        publishNext();
      }
    };    
    n.getEnvironment().getSelectorManager().getTimer().schedule(publishTask, SEND_PERIOD, SEND_PERIOD);    
  }
}
