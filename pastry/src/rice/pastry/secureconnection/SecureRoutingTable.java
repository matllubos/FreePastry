package rice.pastry.secureconnection;

import rice.pastry.Id;
import rice.pastry.NodeHandle;
import rice.pastry.PastryNode;
import rice.pastry.routing.RoutingTable;

/**
 *
 * @author Luboš Mátl
 */
public class SecureRoutingTable extends RoutingTable {

    private SecureIdValidator controller;
    
    public SecureRoutingTable(NodeHandle me, int max, byte base, PastryNode pn, SecureIdValidator controller) {
        super(me, max, base, pn);
        this.controller = controller;
    }

    @Override
    public synchronized boolean put(NodeHandle handle) {
        
        if (!controller.isValid(handle)) {
            return false;
        }
        return super.put(handle);
    }
}
