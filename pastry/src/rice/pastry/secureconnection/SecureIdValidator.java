package rice.pastry.secureconnection;

import rice.p2p.commonapi.NodeHandle;

/**
 *
 * @author Luboš Mátl
 */
public interface SecureIdValidator {
    
    public boolean isValid(NodeHandle nh);

}
