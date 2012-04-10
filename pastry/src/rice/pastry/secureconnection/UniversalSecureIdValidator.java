package rice.pastry.secureconnection;

import java.util.HashMap;
import java.util.Map;
import rice.pastry.Id;

/**
 *
 * @author Luboš Mátl
 */
public class UniversalSecureIdValidator implements SecureIdValidator{

    private Map<Short, SecureIdValidator> controllers = new HashMap<Short, SecureIdValidator>();
    
    protected SecureIdValidator createController(short controlType) {
        switch(controlType) {
            case 1:
                return new ShaSecureIdValidator();
        }
        return null;
    }
    
    private SecureIdValidator getController(short validationAlg) {
        synchronized (controllers) {
             if (controllers.containsKey(validationAlg)) return controllers.get(validationAlg);
        }
        SecureIdValidator controller = createController(validationAlg);
        if (controller == null) return null;
        synchronized (controllers) {
             if (controllers.containsKey(validationAlg)) return controllers.get(validationAlg);
             else controllers.put(validationAlg, controller);
             return controller;
        }
    }
    
    @Override
    public boolean isValid(Id id) {
        if (id.getValidationAlg() == -1) return false;
        SecureIdValidator controller = getController(id.getValidationAlg());
        if (controller == null) return false;
        return controller.isValid(id);
    }
    
    
}
