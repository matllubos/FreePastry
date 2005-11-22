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
