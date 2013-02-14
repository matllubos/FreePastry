/*******************************************************************************

"FreePastry" Peer-to-Peer Application Development Substrate

Copyright 2002-2007, Rice University. Copyright 2006-2007, Max Planck Institute 
for Software Systems.  All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

- Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

- Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

- Neither the name of Rice  University (RICE), Max Planck Institute for Software 
Systems (MPI-SWS) nor the names of its contributors may be used to endorse or 
promote products derived from this software without specific prior written 
permission.

This software is provided by RICE, MPI-SWS and the contributors on an "as is" 
basis, without any representations or warranties of any kind, express or implied 
including, but not limited to, representations or warranties of 
non-infringement, merchantability or fitness for a particular purpose. In no 
event shall RICE, MPI-SWS or contributors be liable for any direct, indirect, 
incidental, special, exemplary, or consequential damages (including, but not 
limited to, procurement of substitute goods or services; loss of use, data, or 
profits; or business interruption) however caused and on any theory of 
liability, whether in contract, strict liability, or tort (including negligence
or otherwise) arising in any way out of the use of this software, even if 
advised of the possibility of such damage.

*******************************************************************************/ 
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
@SuppressWarnings("unchecked")
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
