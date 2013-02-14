package rice.pastry.secureconnection.test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.logging.Level;
import java.util.logging.Logger;
import rice.environment.Environment;
import rice.pastry.NodeIdFactory;
import rice.pastry.PastryNode;
import rice.pastry.PastryNodeFactory;
import rice.pastry.secureconnection.SecurePastrySocketNodeFactory;
import rice.pastry.secureconnection.SecureRandomNodeIdFactory;

/**
 *
 * @author Luboš Mátl
 */
public class SecureIdConnector {

    private static final Logger logger = Logger.getLogger(SecureIdConnector.class.getName());
    private int numberOfCreationAttempts = 3;
    private int numberOfbootAttempts = 2;

    protected NodeIdFactory createNodeIdFactory(InetAddress localIP, int port, Environment env) {
        return new SecureRandomNodeIdFactory(env);
    }
    
    protected PastryNode createNode(InetSocketAddress localSocketAddr) throws IOException {
        Environment env = new Environment();
        NodeIdFactory nidFactory = createNodeIdFactory(localSocketAddr.getAddress(), localSocketAddr.getPort(), env);
        PastryNodeFactory pnFactory = new SecurePastrySocketNodeFactory(nidFactory, localSocketAddr.getAddress(), localSocketAddr.getPort(), env);
        return pnFactory.newNode();
    }

    private void bootNode(PastryNode node, InetSocketAddress bootSocketAddr) throws IOException {
        node.boot(bootSocketAddr);
        synchronized (node) {
            int numberOfbootAttempts = this.numberOfbootAttempts;
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

}
