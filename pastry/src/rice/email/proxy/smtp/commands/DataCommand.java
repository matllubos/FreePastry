package rice.email.proxy.smtp.commands;

import rice.email.proxy.mail.MovingMessage;

import rice.email.proxy.smtp.*;
import rice.email.proxy.util.*;

import rice.email.proxy.smtp.manager.SmtpManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;


/**
 * DATA command.
 * 
 * <p>
 * The spec is at <a
 * href="http://asg.web.cmu.edu/rfc/rfc2821.html#sec-4.1.1.4">
 * http://asg.web.cmu.edu/rfc/rfc2821.html#sec-4.1.1.4 </a>.
 * </p>
 */
public class DataCommand
    extends SmtpCommand
{
    public void execute(SmtpConnection conn, SmtpState state, 
                        SmtpManager manager, String commandLine)
                 throws IOException
    {
        MovingMessage msg = state.getMessage();

        if (msg.getReturnPath() == null)
        {
            conn.println("503 MAIL command required");

            return;
        }

        if (!msg.getRecipientIterator().hasNext())
        {
            conn.println("503 RCPT command(s) required");

            return;
        }

        conn.println("354 Start mail input; end with <CRLF>.<CRLF>");

        String value = "Return-Path: <" + msg.getReturnPath() + 
                       ">\r\n" + "Received: from " + 
                       conn.getClientAddress() + " (HELO " + 
                       conn.getHeloName() + "); " + 
                       new java.util.Date() + "\r\n";

        msg.readDotTerminatedContent(conn);

        String err = manager.checkData(state);
        if (err != null)
        {
            conn.println(err);

            return;
        }

        try
        {
            manager.send(state, conn.isLocal());
            conn.println("250 Message accepted for delivery");
        }
        catch (Exception je)
        {
          conn.println("451 Requested action aborted: local error in processing");
          je.printStackTrace();
          rice.pastry.dist.DistPastryNode.addError("SEVERE: Exception " + je + " occurred while attempting to send message to " + msg.getRecipientIterator().next());
        }

        state.clearMessage();
    }
}