/*
 * Created on Jul 13, 2005
 */
package rice.p2p.splitstream.testing;

import rice.p2p.commonapi.*;
import rice.p2p.commonapi.Node;
import rice.p2p.splitstream.*;
import rice.p2p.splitstream.SplitStreamClient;
import rice.p2p.util.MathUtils;
import rice.selector.TimerTask;

/**
 * @author Jeff Hoye
 */
public class MySplitStreamClient implements SplitStreamClient {

  int PERIOD = 1000;
  
  int msgSize = 500;
  
  /**
   * The underlying common api node
   *  
   */
  private Node n = null;

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
  
  public MySplitStreamClient(Node n, String instance) {
    this.n = n;
    this.instance = instance;
    this.ss = new SplitStreamImpl(n, instance);
  }

  public void attachChannel(ChannelId cid) {
    System.out.println("Attaching to Channel " + cid + " at "+n.getEnvironment().getTimeSource().currentTimeMillis());
    if (channel == null)
      channel = ss.attachChannel(cid);
    getStripes();
  }

  public Stripe[] getStripes() {
    stripes = channel.getStripes();
    return stripes;
  }

  public void publishNext() {
    publish(curSeq);
    curSeq++;
  }
  
  public void publish(int seq) {
    System.out.println("MSSC.publish("+seq+"):"+n.getEnvironment().getTimeSource().currentTimeMillis());
    byte[] msg = new byte[msgSize];
    byte[] head = MathUtils.intToByteArray(seq);
    System.arraycopy(head, 0, msg, 0, 4);
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
    Id stripeId = (rice.pastry.Id) (s.getStripeId().getId());
    String stripeStr = stripeId.toString().substring(3, 4);
    System.out.println("deliver("+stripeStr+","+seq+")");
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
    n.getEnvironment().getSelectorManager().getTimer().schedule(publishTask, PERIOD, PERIOD);    
  }
}
