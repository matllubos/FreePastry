package rice.pastry.secureconnection.test;

import rice.pastry.secureconnection.test.MessageReceiverI;
import java.util.Collection;
import java.util.logging.Level;
import rice.environment.Environment;
import rice.p2p.commonapi.Application;
import rice.p2p.commonapi.Endpoint;
import rice.p2p.commonapi.Id;
import rice.p2p.commonapi.Message;
import rice.p2p.commonapi.Node;
import rice.p2p.commonapi.NodeHandle;
import rice.p2p.commonapi.RouteMessage;
import rice.p2p.scribe.Scribe;
import rice.p2p.scribe.ScribeContent;
import rice.p2p.scribe.ScribeImpl;
import rice.p2p.scribe.ScribeMultiClient;
import rice.p2p.scribe.Topic;
import rice.pastry.commonapi.PastryIdFactory;
import rice.p2p.commonapi.IdFactory;

/**
 *
 * @author Luboš Mátl
 */
public class MessagingApp implements Application, ScribeMultiClient {

    private Endpoint endpoint;
    private Scribe scribe;
    private IdFactory idFactory;

    private MessageReceiverI receiver = null;

    
    public MessagingApp(Node node, MessageReceiverI receiver) {
       
        this.endpoint = node.buildEndpoint(this, "NodeAPI");
        this.scribe = new ScribeImpl(node, "NodeAPI");
        
        this.endpoint.register();
        this.receiver = receiver;

        this.idFactory = new PastryIdFactory(new Environment());


    }

    /*----------------------Pastry-------------------------------------*/
    @Override
    public boolean forward(RouteMessage rm) {
        
        return true;
    }

    @Override
    public void deliver(Id id, Message msg) {
        receiver.receive(msg);
    }

    @Override
    public void update(NodeHandle nh, boolean bln) {
       
    }

    /*--------------------------Scribe-------------------------------------*/
    @Override
    public boolean anycast(Topic topic, ScribeContent sc) {
        receiver.receive(sc);
        return true;
    }

    @Override
    public void deliver(Topic topic, ScribeContent sc) {
        receiver.receive(sc);
    }

    @Override
    public void childAdded(Topic topic, NodeHandle nh) {
    }

    @Override
    public void childRemoved(Topic topic, NodeHandle nh) {
    }

    @Override
    public void subscribeFailed(Topic topic) {
    }

    @Override
    public void subscribeFailed(Collection<Topic> clctn) {
    }

    @Override
    public void subscribeSuccess(Collection<Topic> clctn) {
    }




    public void send(ScribeContent message, String group) {
        Topic topic = new Topic(this.idFactory, group);
        scribe.publish(topic, message);
    }

    public void send(Message message, Id id) {
        endpoint.route(id, message, null);
    }

    public void join(String group) {
        Topic topic = new Topic(this.idFactory, group);
        scribe.subscribe(topic, this);
    }
    
    public Id getLocalId() {
        return endpoint.getLocalNodeHandle().getId();
    }

}
