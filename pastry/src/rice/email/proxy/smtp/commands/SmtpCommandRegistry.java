package rice.email.proxy.smtp.commands;

import java.io.IOException;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

public class SmtpCommandRegistry {

  private static Map commands = new HashMap();
  private static Object[][] COMMANDS = new Object[][]
  {
  {"HELO", new HeloCommand()}, {"EHLO", new HeloCommand()},
  {"NOOP", new NoopCommand()}, {"RSET", new RsetCommand()},
  {"QUIT", new QuitCommand()}, {"MAIL", new MailCommand()},
  {"RCPT", new RcptCommand()}, {"DATA", new DataCommand()},
  {"VRFY", new VrfyCommand()}
  };

  public void load() {
    for (int i = 0; i < COMMANDS.length; i++) {
      String name = COMMANDS[i][0].toString();

      if (commands.containsKey(name))
        continue;

      SmtpCommand command = (SmtpCommand) COMMANDS[i][1];
      registerCommand(name, command);
    }
  }

  private void registerCommand(String name, SmtpCommand command)  {
    commands.put(name, command);
  }

  public SmtpCommand getCommand(String name) {
    return (SmtpCommand) commands.get(name);
  }
}