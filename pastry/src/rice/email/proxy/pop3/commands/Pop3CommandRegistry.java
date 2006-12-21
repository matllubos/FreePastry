/*************************************************************************

"FreePastry" Peer-to-Peer Application Development Substrate 

Copyright 2002, Rice University. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

- Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

- Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

- Neither  the name  of Rice  University (RICE) nor  the names  of its
contributors may be  used to endorse or promote  products derived from
this software without specific prior written permission.

This software is provided by RICE and the contributors on an "as is"
basis, without any representations or warranties of any kind, express
or implied including, but not limited to, representations or
warranties of non-infringement, merchantability or fitness for a
particular purpose. In no event shall RICE or contributors be liable
for any direct, indirect, incidental, special, exemplary, or
consequential damages (including, but not limited to, procurement of
substitute goods or services; loss of use, data, or profits; or
business interruption) however caused and on any theory of liability,
whether in contract, strict liability, or tort (including negligence
or otherwise) arising in any way out of the use of this software, even
if advised of the possibility of such damage.

********************************************************************************/
package rice.email.proxy.pop3.commands;

import java.util.HashMap;
import java.util.Map;

import rice.environment.Environment;
import rice.environment.logging.Logger;

public class Pop3CommandRegistry {
  
  private static Map commands        = new HashMap();
  private static Object[][] COMMANDS = new Object[][] {
  {"QUIT", QuitCommand.class}, {"STAT", StatCommand.class}, 
  {"APOP", ApopCommand.class}, {"USER", UserCommand.class}, 
  {"PASS", PassCommand.class}, {"LIST", ListCommand.class}, 
  {"UIDL", UidlCommand.class}, {"TOP", TopCommand.class}, 
  {"RETR", RetrCommand.class}, {"DELE", DeleCommand.class},
  {"NOOP", NoopCommand.class}, {"CAPA", CapaCommand.class} };
  
  public void load(Environment env) {
    for (int i = 0; i < COMMANDS.length; i++) {
      String name = COMMANDS[i][0].toString();
      
      if (commands.containsKey(name))
        continue;
      
      try {
        Class type = (Class) COMMANDS[i][1];
        Pop3Command command = (Pop3Command) type.newInstance();
        registerCommand(name, command);
      } catch (Exception e) {
        Logger logger = env.getLogManager().getLogger(Pop3CommandRegistry.class, null);
        if (logger.level <= Logger.WARNING) logger.logException("", e);
      }
    }
  }
  
  private void registerCommand(String name, Pop3Command command) {
    commands.put(name, command);
  }
  
  public Pop3Command getCommand(String name) {
    return (Pop3Command) commands.get(name);
  }
}
