package rice.email.proxy.imap;

import antlr.TokenStreamException;
import antlr.TokenStreamIOException;

import rice.email.proxy.imap.commands.AbstractImapCommand;
import rice.email.proxy.imap.commands.IllegalStateCommand;

import rice.email.proxy.imap.parser.antlr.ImapLineParser;

import rice.email.proxy.user.UserManager;

import rice.email.proxy.util.Quittable;
import rice.email.proxy.util.Workspace;

import java.io.IOException;

import java.net.Socket;
import java.net.SocketTimeoutException;


final class ParserImapHandler
    implements Quittable
{
    ImapConnection conn;
    boolean quitting;
    String currentLine;
    ImapState state;
    ImapLineParser cmdParser;

    public ParserImapHandler(UserManager manager, Workspace workspace)
    {
        state = new ImapState(manager, workspace);
    }

    public void handleConnection(final Socket socket)
                          throws IOException
    {
        conn = new ImapConnection(this, socket);

        cmdParser = new ImapLineParser();

        try
        {
            quitting = false;

            sendGreetings();

            while (!quitting)
            {
                handleCommand();
            }

            conn.close();
        }
        catch (SocketTimeoutException ste)
        {
            conn.println("* BYE Autologout; idle for too long");

        }
        catch (TokenStreamIOException tsioe)
        {
            conn.println("* BYE Autologout; idle for too long");

        }
        catch (final Exception e)
        {
        }
        finally
        {
            try
            {
                conn.close();
            }
            catch (final IOException ioe)
            {

            }
        }

    }

    protected void sendGreetings()
    {
        conn.println("* OK IMAP4rev1 server ready");
    }

    protected void handleCommand()
                          throws IOException, TokenStreamException
    {
        String line = conn.readLine();
        AbstractImapCommand cmd = cmdParser.parseCommand(line);

        if (cmd == null)
          throw new RuntimeException("Command was null!");
        
        if (!cmd.isValidForState(state))
            cmd = new IllegalStateCommand();

        cmd.setConn(conn);
        cmd.setState(state);
        try
        {
            cmd.execute();
        }
        catch (RuntimeException re)
        {
            conn.println(cmd.getTag() + " NO internal error");
          re.printStackTrace();
        }
    }

    public void quit()
    {
        quitting = true;
    }
}