package rice.pastry.secureconnection.test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.logging.Level;
import java.util.logging.Logger;
import rice.environment.Environment;
import rice.pastry.NodeIdFactory;
import rice.pastry.PastryNode;
import rice.pastry.PastryNodeFactory;
import rice.pastry.secureconnection.SecurePastrySocketNodeFactory;
import rice.pastry.secureconnection.SecureRandomNodeIdFactory;
import rice.pastry.standard.RandomNodeIdFactory;

/**
 *
 * @author Luboš Mátl
 */
public class Connector {

    private static final Logger logger = Logger.getLogger(Connector.class.getName());
    private int numberOfCreationAttempts = 3;
    private int numberOfbootAttempts = 2;

    private PastryNode createNode(InetSocketAddress localSocketAddr) throws IOException {
        Environment env = new Environment();
        NodeIdFactory nidFactory = new SecureRandomNodeIdFactory(env);
       // NodeIdFactory nidFactory = new RandomNodeIdFactory(env);
        PastryNodeFactory pnFactory = new SecurePastrySocketNodeFactory(nidFactory, localSocketAddr.getAddress(), localSocketAddr.getPort(), env);
        return pnFactory.newNode();
    }

    private void bootNode(PastryNode node, InetSocketAddress bootSocketAddr) throws IOException {
        node.boot(bootSocketAddr);
        synchronized (node) {
            while (!node.isReady() && !node.joinFailed()) {
                if (numberOfbootAttempts-- == 0) {
                    node.destroy();
                    throw new IOException("Could not join the FreePastry ring.");
                }
                try {
                    node.wait(500);
                } catch (InterruptedException ex) {
                    throw new IOException("Could not join the FreePastry ring. Reason: " + ex);
                }
                if (node.joinFailed()) {
                    throw new IOException("Could not join the FreePastry ring. Reason: " + node.joinFailedReason());
                }
            }
        }
    }

    protected MessagingApp getMessagingApp(PastryNode node, MessageReceiverI receiver) {
        return new MessagingApp(node, receiver);
    }

    public MessagingApp getSender(InetSocketAddress localSocketAddr, InetSocketAddress bootSocketAddr, MessageReceiverI receiver) throws IOException {
        MessagingApp messagingApp = null;
        PastryNode node = null;
        while (numberOfCreationAttempts >= 0) {
            try {
                node = createNode(localSocketAddr);
                messagingApp = getMessagingApp(node, receiver);
                bootNode(node, bootSocketAddr);
                logger.log(Level.FINE, "Finished creating new node: {0}", node);
                break;
            } catch (IOException ex) {
                if (node != null) {
                    node.destroy();
                }
                logger.log(Level.WARNING, "Could not join the FreePastry ring: {0}, remaining connection attempts " + numberOfCreationAttempts, node);
                if (numberOfCreationAttempts == 0) {
                    throw new IOException("Could not join the FreePastry ring.");
                }
                numberOfCreationAttempts--;
            }
        }


        return messagingApp;
    }

    public static void main(String args[]) throws InterruptedException {

        Connector connector = new Connector();
        try {
            connector.getSender(new InetSocketAddress("127.0.0.1", 6000), new InetSocketAddress("127.0.0.1", 6000), null);
            Thread.sleep(1000);
            connector.getSender(new InetSocketAddress("127.0.0.1", 6001), new InetSocketAddress("127.0.0.1", 6000), null);
            Thread.sleep(1000);
            connector.getSender(new InetSocketAddress("127.0.0.1", 6002), new InetSocketAddress("127.0.0.1", 6001), null);
            Thread.sleep(1000);
            connector.getSender(new InetSocketAddress("127.0.0.1", 6003), new InetSocketAddress("127.0.0.1", 6002), null);
            Thread.sleep(1000);
            connector.getSender(new InetSocketAddress("127.0.0.1", 6004), new InetSocketAddress("127.0.0.1", 6003), null);
            Thread.sleep(1000);
            connector.getSender(new InetSocketAddress("127.0.0.1", 6005), new InetSocketAddress("127.0.0.1", 6004), null);
            Thread.sleep(5000);
            System.out.println("ok");
        } catch (IOException ex) {
            System.out.println("error");
        }


        System.exit(0);
    }
}
