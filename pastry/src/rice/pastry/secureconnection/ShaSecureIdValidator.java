package rice.pastry.secureconnection;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import rice.pastry.Id;

/**
 *
 * @author Luboš Mátl
 */
public class ShaSecureIdValidator implements SecureIdValidator {

    @Override
    public boolean isValid(Id id) {
        MessageDigest md = null;
        try {
            md = MessageDigest.getInstance("SHA");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("No SHA support!", e);
        }
        md.update(id.getRaw());
        byte[] digest = md.digest();
        return Id.build(digest).equals(id);
    }
    
}
