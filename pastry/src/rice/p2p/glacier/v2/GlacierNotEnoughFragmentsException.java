package rice.p2p.glacier;

public class GlacierNotEnoughFragmentsException extends GlacierException {
    public int checked;
    
    public int found;

    public GlacierNotEnoughException(String msg, int checked, int found) {
        super(msg);
        this.checked = checked;
        this.found = found;
    }
}





