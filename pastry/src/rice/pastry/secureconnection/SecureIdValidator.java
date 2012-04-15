package rice.pastry.secureconnection;

import rice.pastry.Id;

/**
 *
 * @author Luboš Mátl
 */
public interface SecureIdValidator {
    
    public boolean isValid(Id id);

}
