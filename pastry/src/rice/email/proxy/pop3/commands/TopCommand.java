package rice.email.proxy.pop3.commands;

import rice.*;
import rice.Continuation.*;

import rice.email.*;
import rice.email.proxy.mail.*;
import rice.email.proxy.mailbox.*;
import rice.email.proxy.pop3.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.List;

public class TopCommand extends Pop3Command {
  
  public boolean isValidForState(Pop3State state) {
    return state.isAuthenticated();
  }
  
  public void execute(Pop3Connection conn, Pop3State state, String cmd) {
    try {
      MailFolder inbox = state.getFolder();
      String[] cmdLine = cmd.split(" ");
      if (cmdLine.length < 3)
        throw new IllegalArgumentException("range and line count required");
      
      String msgNumStr = cmdLine[1];
      List msgList = inbox.getMessages(new MsgRangeFilter(msgNumStr, false));
      if (msgList.size() != 1) {
        conn.println("-ERR no such message");
        return;
      }
      
      int numLines = Integer.parseInt(cmdLine[2]);
      
      StoredMessage msg = (StoredMessage) msgList.get(0);
      ExternalContinuation c = new ExternalContinuation();
      msg.getMessage().getContent(c);
      c.sleep();
      
      if (c.exceptionThrown()) { throw new MailboxException(c.getException()); }
      
      EmailMessagePart message = (EmailMessagePart) c.getResult();
      
      conn.println("+OK");
      
      String test = RetrCommand.fetchAll(message);
      String[] lines = test.split("\n");
      int i=0;
      
      while (! lines[i].trim().equals("")) {
        conn.print(lines[i] + "\n");
        i++;
      }
      
      conn.print(lines[i] + "\n");
      
      for (int j=i; j<i+numLines && j<lines.length; j++) {
        conn.print(lines[j] + "\n");
      }
      
      conn.println(".");
    } catch (Exception e) {
      conn.println("-ERR " + e);
    }
  }
}