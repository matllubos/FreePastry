package rice.p2p.glacier.v2;

import rice.p2p.glacier.GlacierException;

public class GlacierNotEnoughFragmentsException extends GlacierException {
    public int checked;
    
    public int found;

    public GlacierNotEnoughFragmentsException(String msg, int checked, int found) {
        super(msg);
        this.checked = checked;
        this.found = found;
    }
}
