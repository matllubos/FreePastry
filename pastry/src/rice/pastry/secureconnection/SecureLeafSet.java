package rice.pastry.secureconnection;


import rice.pastry.Id;
import rice.pastry.NodeHandle;
import rice.pastry.leafset.LeafSet;
import rice.pastry.routing.RoutingTable;

/**
 *
 * @author Luboš Mátl
 */
public class SecureLeafSet extends LeafSet {

    private SecureIdValidator controller;
    
    public SecureLeafSet(NodeHandle localNode, int size, RoutingTable rt, SecureIdValidator controller) {
        super(localNode, size, rt);
        this.controller = controller;
    }

    public SecureLeafSet(NodeHandle localNode, int size, boolean observe) {
        super(localNode, size, observe);
    }

    public SecureLeafSet(NodeHandle localNode, int size, boolean observe, NodeHandle[] cwTable, NodeHandle[] ccwTable) {
        super(localNode, size, observe, cwTable, ccwTable);
    }

    
    public boolean put(NodeHandle handle, boolean suppressNotification) {
        Id nid = handle.getNodeId();
        //test if node is valid
        if (!controller.isValid(nid)) {
            return false;
        }

        return super.put(handle, suppressNotification);
    }
}
