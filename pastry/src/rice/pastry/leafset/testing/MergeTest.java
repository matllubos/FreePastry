/*
 * Created on Dec 11, 2006
 */
package rice.pastry.leafset.testing;

import java.io.IOException;
import java.util.*;

import rice.environment.Environment;
import rice.pastry.*;
import rice.p2p.commonapi.appsocket.AppSocketReceiver;
import rice.p2p.commonapi.rawserialization.*;
import rice.pastry.client.PastryAppl;
import rice.pastry.leafset.LeafSet;
import rice.pastry.messaging.Message;
import rice.pastry.routing.RoutingTable;

public class MergeTest {

  
  /**
   * Input of the form:
   * <0xD74D4F..><0xD7B075..><0xD98A9D..><0xDAC7F0..><0xDB39A6..><0xDD5A73..><0xE050B3..><0xE0B735..><0xE33A04..><0xE48D40..><0xE678CB..><0xE73F09..> [ <0xEA5EAF..> ] <0xEBC2BB..><0xEBD2CB..><0xEF7F43..><0xF09044..><0xF10B96..><0xF33C36..><0xF64DA9..><0xF66CD9..><0xF9E251..><0xFB7F46..><0xFC1B02..><0xFC4718..>
   * @param str
   * @return
   */
  public static LeafSet getLeafSet(String str) {
    String a[] = str.split("\\[");
    assert(a.length == 2);
    String b[] = a[1].split("]");
    assert(b.length == 2);
    
    String s_ccw = a[0]; // <0xD74D4F..><0xD7B075..><0xD98A9D..><0xDAC7F0..><0xDB39A6..><0xDD5A73..><0xE050B3..><0xE0B735..><0xE33A04..><0xE48D40..><0xE678CB..><0xE73F09..> 
    String s_cw = b[1];
    String s_base = b[0]; //<0xEA5EAF..>
    
    NodeHandle[] ccw = getHandles(s_ccw);
    flip(ccw);
    NodeHandle base = getHandles(s_base)[0];
    NodeHandle[] cw = getHandles(s_cw);
    
    LeafSet ls = new LeafSet(base,24,true,cw,ccw);
    
    return ls;
  }

  public static void flip(NodeHandle[] nds) {
    for (int a = 0; a < nds.length/2; a++) {
      int b = nds.length-a-1;
      NodeHandle temp = nds[a];
      nds[a] = nds[b];
      nds[b] = temp;
    }
  }
  
  /**
   * Input of the form:
   * <0xD74D4F..><0xD7B075..><0xD98A9D..><0xDAC7F0..><0xDB39A6..>
   * @param str
   * @return
   */
  public static NodeHandle[] getHandles(String str) {
    ArrayList<NodeHandle> list = new ArrayList<NodeHandle>();
    String a[] = str.split("[< ]");
    for (int ctr = 0; ctr < a.length; ctr++) {
      if (a[ctr].length() > 3) {
        assert(a[ctr].substring(0,2).equals("0x"));
        assert(a[ctr].substring(a[ctr].length()-3,a[ctr].length()).equals("..>"));
        a[ctr] = a[ctr].substring(2,a[ctr].length()-3);
                
        list.add(new TestNodeHandle(Id.build(a[ctr])));
     
//        System.out.println(a[ctr]);
      }
    }
    return list.toArray(new NodeHandle[0]);
  }
  
  /**
   * @param args
   */
  public static void main(String[] args) {
    Environment env = new Environment();
    
//  leafset:  [ <0xEA1020..> ]  complete:false size:0 s2:false.merge(leafset: <0xD74D4F..><0xD7B075..><0xD98A9D..><0xDAC7F0..><0xDB39A6..><0xDD5A73..><0xE050B3..><0xE0B735..><0xE33A04..><0xE48D40..><0xE678CB..><0xE73F09..> [ <0xEA5EAF..> ] <0xEBC2BB..><0xEBD2CB..><0xEF7F43..><0xF09044..><0xF10B96..><0xF33C36..><0xF64DA9..><0xF66CD9..><0xF9E251..><0xFB7F46..><0xFC1B02..><0xFC4718..> complete:true size:24 s1:false s2:false,[SNH: <0xEA5EAF..>//128.59.20.228:21854 [-7262332366877176307]],...,false,null)
    String s_ls1 = "<0xD74D4F..><0xD7B075..><0xD98A9D..><0xDAC7F0..><0xDB39A6..><0xDD5A73..><0xE050B3..><0xE0B735..><0xE33A04..><0xE48D40..><0xE678CB..><0xE73F09..> [ <0xEA5EAF..> ] <0xEBC2BB..><0xEBD2CB..><0xEF7F43..><0xF09044..><0xF10B96..><0xF33C36..><0xF64DA9..><0xF66CD9..><0xF9E251..><0xFB7F46..><0xFC1B02..><0xFC4718..>";
    
    LeafSet ls1 = getLeafSet(s_ls1);
    
    String s_ls2 = " [ <0xEA1020..> ] ";
    
    LeafSet ls2 = getLeafSet(s_ls2);
    
    PastryNode pn = new PastryNode((rice.pastry.Id)ls2.get(0).getId(),env){
    
//      public PastryNode(Id id, Environment env) {
//        super(id, env); 
//      }
//      
      public NodeHandle readNodeHandle(InputBuffer buf) throws IOException {
        // TODO Auto-generated method stub
        return null;
      }
    
      @Override
      public void send(NodeHandle handle, Message message) {
        // TODO Auto-generated method stub
        
      }
    
      @Override
      public ScheduledMessage scheduleMsgAtFixedRate(Message msg, long delay, long period) {
        // TODO Auto-generated method stub
        return null;
      }
    
      @Override
      public ScheduledMessage scheduleMsg(Message msg, long delay) {
        // TODO Auto-generated method stub
        return null;
      }
    
      @Override
      public ScheduledMessage scheduleMsg(Message msg, long delay, long period) {
        // TODO Auto-generated method stub
        return null;
      }
    
      @Override
      public int proximity(NodeHandle nh) {
        // TODO Auto-generated method stub
        return 0;
      }
    
      @Override
      public void nodeIsReady() {
        // TODO Auto-generated method stub
        
      }
    
      @Override
      public void initiateJoin(NodeHandle[] bootstrap) {
        // TODO Auto-generated method stub
        
      }
    
      @Override
      public void connect(NodeHandle handle, AppSocketReceiver receiver, PastryAppl appl, int timeout) {
        // TODO Auto-generated method stub
        
      }
    
      @Override
      public NodeHandle coalesce(NodeHandle newHandle) {
        // TODO Auto-generated method stub
        return null;
      }
    
    };
    
    RoutingTable rt = new RoutingTable(ls2.get(0),1,(byte)4,pn);
    
    ls2.addNodeSetListener(new NodeSetListener() {
    
      public void nodeSetUpdate(NodeSetEventSource nodeSetEventSource,
          NodeHandle handle, boolean added) {
        System.out.println("nodeSetUpdate("+handle+","+added+")");
    
      }    
    });
    
    ls2.merge(ls1,ls1.get(0),rt,false, null);
    
    
    env.destroy();
    
  }
  
  public static class TestNodeHandle extends NodeHandle {
    private Id id;

    public TestNodeHandle(Id id) {
      this.id = id;
    }

    public Id getNodeId() {
      return id;
    }

    public int getLiveness() {
      return NodeHandle.LIVENESS_ALIVE;
    }

    public int proximity() {
      return 1;
    }

    public boolean ping() {
      return true;
    }

    public boolean equals(Object obj) {
      if (obj instanceof TestNodeHandle) {
        return ((TestNodeHandle) obj).id.equals(id);
      }

      return false;
    }

    public int hashCode() {
      return id.hashCode();
    }

    public void receiveMessage(Message m) {
    };

    public String toString() {
      return id.toString();
    }

    public void serialize(OutputBuffer buf) throws IOException {
      throw new RuntimeException("not implemented.");        
    }
  }


}
