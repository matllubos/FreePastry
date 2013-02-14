package rice.pastry.secureconnection;

import java.io.IOException;
import rice.p2p.commonapi.rawserialization.InputBuffer;
import rice.p2p.commonapi.rawserialization.OutputBuffer;
import rice.pastry.NodeHandle;
import rice.pastry.join.JoinAddress;
import rice.pastry.messaging.PRawMessage;

/**
 *
 * @author Luboš Mátl
 */
public class FailJoinMessage extends PRawMessage {

    public static final short TYPE = -3325;

    public FailJoinMessage() {
        super(JoinAddress.getCode());
    }

    @Override
    public short getType() {
        return TYPE;
    }

    @Override
    public void serialize(OutputBuffer buf) throws IOException {
        buf.writeByte((byte) 0);
    }

    public FailJoinMessage(InputBuffer buf, NodeHandle sender) throws IOException {
        super(JoinAddress.getCode());
        byte version = buf.readByte();
        switch (version) {
            case 0:
                setSender(sender);
                break;
            default:
                throw new IOException("Unknown Version: " + version);
        }
    }
}
