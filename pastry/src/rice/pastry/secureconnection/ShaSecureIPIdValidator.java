package rice.pastry.secureconnection;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import rice.environment.logging.Logger;
import rice.p2p.commonapi.NodeHandle;
import rice.pastry.Id;
import rice.pastry.socket.SocketNodeHandle;

/**
 *
 * @author Luboš Mátl
 */
public class ShaSecureIPIdValidator implements SecureIdValidator{

    @Override
    public boolean isValid(NodeHandle nh) {
        Id id = (Id) nh.getId();

        SocketNodeHandle snh = (SocketNodeHandle) nh;
        
        byte rawIP[] = snh.getInetSocketAddress().getAddress().getAddress();

        byte rawPort[] = new byte[2];
        int tmp = snh.getInetSocketAddress().getPort();
        for (int i = 0; i < 2; i++) {
            rawPort[i] = (byte) (tmp & 0xff);
            tmp >>= 8;
        }

        byte raw[] = id.getRaw();


        MessageDigest md = null;
        try {
            md = MessageDigest.getInstance("SHA");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("No SHA support!", e);
        }
        
        byte[] allRaw = new byte[rawIP.length + rawPort.length + raw.length];
        
        System.arraycopy(rawIP,0,allRaw,0         ,rawIP.length);
        System.arraycopy(rawPort,0,allRaw,rawIP.length,rawPort.length);
        System.arraycopy(raw,0,allRaw,rawIP.length + rawPort.length,raw.length);

        md.update(allRaw);
        byte[] digest = md.digest();
        return Id.build(digest).equals(id);
    }
    
}
