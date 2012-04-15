package rice.pastry.secureconnection;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import org.mpisws.p2p.transport.multiaddress.MultiInetSocketAddress;
import rice.environment.Environment;
import rice.pastry.Id;
import rice.pastry.NodeHandle;
import rice.pastry.NodeHandleFactory;
import rice.pastry.NodeIdFactory;
import rice.pastry.PastryNode;
import rice.pastry.ReadyStrategy;
import rice.pastry.join.JoinProtocol;
import rice.pastry.leafset.LeafSet;
import rice.pastry.messaging.MessageDispatch;
import rice.pastry.routing.RoutingTable;
import rice.pastry.socket.SocketNodeHandle;
import rice.pastry.socket.SocketNodeHandleFactory;
import rice.pastry.socket.SocketPastryNodeFactory;
import rice.pastry.standard.ConsistentJoinProtocol;
import rice.pastry.standard.RapidRerouter;
import rice.pastry.standard.StandardRouter;
import rice.pastry.transport.NodeHandleAdapter;
import rice.pastry.transport.TLDeserializer;

/**
 *
 * @author Luboš Mátl
 */
public class SecurePastrySocketNodeFactory extends SocketPastryNodeFactory {

    private final SecureIdValidator controller = new UniversalSecureIdValidator();

    public SecurePastrySocketNodeFactory(NodeIdFactory nf, int startPort, Environment env) throws IOException {
        super(nf, startPort, env);
        Id.setSecureId();
    }

    public SecurePastrySocketNodeFactory(NodeIdFactory nf, InetAddress bindAddress, int startPort, Environment env) throws IOException {
        super(nf, bindAddress, startPort, env);
        Id.setSecureId();
    }

    @Override
    protected JoinProtocol getJoinProtocol(PastryNode pn, LeafSet leafSet, RoutingTable routeTable, ReadyStrategy lsProtocol) {
        ConsistentJoinProtocol jProtocol = new SecureConsistentJoinProtocol(pn,
                pn.getLocalHandle(), routeTable, leafSet, lsProtocol, getIdController());
        jProtocol.register();
        return jProtocol;
    }

    public PastryNode nodeHandleHelper(PastryNode pn) throws IOException {
        NodeHandleFactory handleFactory = getNodeHandleFactory(pn);
        NodeHandle localhandle = getLocalHandle(pn, handleFactory);

        TLDeserializer deserializer = getTLDeserializer(handleFactory, pn);

        MessageDispatch msgDisp = new MessageDispatch(pn, deserializer);
        RoutingTable routeTable = new SecureRoutingTable(localhandle, rtMax, rtBase, pn, getIdController());
        LeafSet leafSet = new SecureLeafSet(localhandle, lSetSize, routeTable, getIdController());
        StandardRouter router = new RapidRerouter(pn, msgDisp, getRouterStrategy(pn));
        pn.setElements(localhandle, msgDisp, leafSet, routeTable, router);


        NodeHandleAdapter nha = getNodeHandleAdapter(pn, handleFactory, deserializer);

        pn.setSocketElements(leafSetMaintFreq, routeSetMaintFreq,
                nha, nha, nha, handleFactory);

        router.register();

        registerApps(pn, leafSet, routeTable, nha, handleFactory);

        return pn;
    }

    protected SecureIdValidator getIdController() {
        return controller;
    }
    
    
}
