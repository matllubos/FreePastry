package rice.pastry.secureconnection;

import java.io.IOException;
import rice.environment.logging.Logger;
import rice.p2p.commonapi.rawserialization.InputBuffer;
import rice.p2p.commonapi.rawserialization.MessageDeserializer;
import rice.pastry.Id;
import rice.pastry.JoinFailedException;
import rice.pastry.NodeHandle;
import rice.pastry.PastryNode;
import rice.pastry.ReadyStrategy;
import rice.pastry.join.JoinRequest;
import rice.pastry.leafset.LeafSet;
import rice.pastry.messaging.Message;
import rice.pastry.routing.RouteMessage;
import rice.pastry.routing.RoutingTable;
import rice.pastry.standard.ConsistentJoinProtocol;

/**
 *
 * @author Luboš Mátl
 */
public class SecureConsistentJoinProtocol extends ConsistentJoinProtocol {

    private SecureIdValidator controller;
    
    public static class SecureCJPDeserializer extends CJPDeserializer {

        public SecureCJPDeserializer(PastryNode pn) {
            super(pn);
        }

        @Override
        public Message deserialize(InputBuffer buf, short type, int priority, NodeHandle sender) throws IOException {
            switch (type) {
                case FailJoinMessage.TYPE:
                    return new FailJoinMessage(buf, (NodeHandle) sender);
            }
            return super.deserialize(buf, type, priority, sender);
        }
    }

    public SecureConsistentJoinProtocol(PastryNode ln, NodeHandle lh, RoutingTable rt, LeafSet ls, ReadyStrategy nextReadyStrategy, SecureIdValidator controller) {
        this(ln, lh, rt, ls, nextReadyStrategy, null, controller);
    }

    public SecureConsistentJoinProtocol(PastryNode ln, NodeHandle lh, RoutingTable rt, LeafSet ls, ReadyStrategy nextReadyStrategy, MessageDeserializer md, SecureIdValidator controller) {
        super(ln, lh, rt, ls, nextReadyStrategy, md != null ? md : new SecureCJPDeserializer(ln));
        this.controller = controller;
    }

    @Override
    public void receiveMessage(Message msg) {
        if (msg instanceof JoinRequest) {
            JoinRequest cjm = (JoinRequest) msg;
            if (!controller.isValid(cjm.getHandle())) {
                FailJoinMessage fjm = new FailJoinMessage();
                thePastryNode.send(cjm.getHandle(), fjm, null, null);
                return;
            }
        } if (msg instanceof RouteMessage){
            try {
                RouteMessage rm = (RouteMessage) msg;
                JoinRequest jr = (JoinRequest) rm.unwrap(deserializer);
                NodeHandle jh = jr.getHandle();
                if (! controller.isValid(jh)) {
                    FailJoinMessage fjm = new FailJoinMessage();
                    thePastryNode.send(jh, fjm, null, null);
                    return;
                }
            } catch (IOException ex) {
                if (logger.level <= Logger.SEVERE) logger.logException("SecureConsistentJoinProtocol.receiveMessage()",ex);
            }
        }
      
        if (msg instanceof FailJoinMessage) {
            thePastryNode.joinFailed(new JoinFailedException("Cannot join ring. Bootstraps do not want receive me."));
            return;
        }
        super.receiveMessage(msg);
    }
}
