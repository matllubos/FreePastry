package rice.post.security;

import java.util.*;

import rice.*;
import rice.post.*;

/**
 * This class is a central repository for all of the SecurityModules which are
 * currently loaded, and can verify certificates for applications which do not
 * care about the module-certificate relationships.
 *
 * @version $Id$
 * @author amislove
 */
public class SecurityService {

  /**
   * The list of modules which are currently loaded
   */
  private Hashtable modules;

  /**
   * Constructor for SecurityService.
   */
  public SecurityService() {
    modules = new Hashtable();
  }

  /**
   * Gets the module corresponding to the given name
   *
   * @param name The name of the module to get
   * @return The Module value
   */
  public SecurityModule getModule(String name) {
    return (SecurityModule) modules.get(name);
  }

  /**
   * Loads a module under the name module.getName()
   *
   * @param module The module to load
   */
  public void loadModule(SecurityModule module) {
    modules.put(module.getName(), module);
  }

  /**
   * Unloads the module under the name module.getName()
   *
   * @param module The module to unload
   */
  public void unloadModule(SecurityModule module) {
    modules.remove(module.getName());
  }

  /**
   * Verifies the given certificate, and passes the result, either True or
   * False, to the given command. If a module cannot be found to verify the
   * certificate, command.receiveException is called with a SecurityException.
   *
   * @param certificate The certificate to verify
   * @param command The command to call with the result
   */
  public void verify(PostCertificate certificate, Continuation command) {
    Iterator i = modules.values().iterator();

    while (i.hasNext()) {
      SecurityModule module = (SecurityModule) i.next();

      if (module.canVerify(certificate)) {
        module.verify(certificate, command);
        return;
      }
    }

    command.receiveException(new SecurityException("Could not find the module to verify " + command.getClass().getName()));
  }

}
