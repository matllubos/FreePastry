package rice.email.proxy.pop3.commands;

import rice.email.proxy.mailbox.*;
import rice.email.proxy.pop3.*;
import rice.email.proxy.user.*;

public class PassCommand extends Pop3Command {
  
    public boolean isValidForState(Pop3State state) {
        return !state.isAuthenticated();
    }

    public void execute(Pop3Connection conn, Pop3State state, String cmd) {
        User user = state.getUser();
        if (user == null) {
            conn.println("-ERR USER required");
            return;
        }

        String[] args = cmd.split(" ");
        if (args.length < 2) {
            conn.println("-ERR Required syntax: PASS <username>");

            return;
        }

        try {
            String pass = args[1];
            state.authenticate(pass);
            conn.println("+OK");
        } catch (Exception e) {
            conn.println("-ERR Authentication failed: " + e);
        }
    }
}