package rice.pastry.secureconnection.test;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import rice.environment.Environment;
import rice.environment.logging.Logger;
import rice.pastry.Id;
import rice.pastry.NodeIdFactory;

/**
 *
 * @author Luboš Mátl
 */
public class ColisionNodeIdFactory implements NodeIdFactory {

    protected Logger logger;

    /**
     * Constructor.
     */
    public ColisionNodeIdFactory(Environment env) {
        this.logger = env.getLogManager().getLogger(getClass(), null);
    }

    /**
     * generate a colision nodeId
     * 
     * @return the new nodeId
     */
    @Override
    public Id generateNodeId() {

        //byte raw[] = new byte[NodeId.nodeIdBitLength >> 3];
        //rng.nextBytes(raw);

        byte raw[] = new byte[]{1, 1, 1, 1, 1, 1, 1, 1};

        MessageDigest md = null;
        try {
            md = MessageDigest.getInstance("SHA");
        } catch (NoSuchAlgorithmException e) {
            if (logger.level <= Logger.SEVERE) {
                logger.log(
                        "No SHA support!");
            }
            throw new RuntimeException("No SHA support!", e);
        }

        md.update(raw);
        byte[] digest = md.digest();

        Id nodeId = Id.build(digest, raw, (short) 1);

        return nodeId;
    }
}
