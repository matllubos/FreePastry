package rice.pastry.secureconnection.test;

import java.net.InetAddress;
import rice.environment.Environment;
import rice.pastry.NodeIdFactory;
import rice.pastry.secureconnection.SecureIPNodeIdFactory;

/**
 *
 * @author Luboš Mátl
 */
public class SecureIdIPNodeIdFactoryConnector extends SecureIdConnector{
    
    @Override
    protected NodeIdFactory createNodeIdFactory(InetAddress localIP, int port, Environment env) {
        return new SecureIPNodeIdFactory(localIP, port, env);
    }
}
