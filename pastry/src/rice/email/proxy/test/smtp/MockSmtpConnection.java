package rice.email.proxy.test.smtp;

import java.io.*;
import java.net.*;

import junit.framework.TestCase;

import rice.email.proxy.smtp.*;

public class MockSmtpConnection
    extends SmtpConnection
{
    PrintWriter pusher;
    InetAddress servAddress;
    BufferedReader responseIn;

    public MockSmtpConnection()
                       throws IOException
    {
        super(null, null, null, false);

        // simulate client input
        PipedReader clientIn     = new PipedReader();
        PipedWriter clientWriter = new PipedWriter(clientIn);
        StreamTokenizer in = new StreamTokenizer(clientIn);
        in.eolIsSignificant(false);
        in.wordChars(1,255);
        in.whitespaceChars(10, 10);
        pusher                   = new PrintWriter(clientWriter);

        // intercept server response
        PipedReader serverIn     = new PipedReader();
        PipedWriter serverWriter = new PipedWriter(serverIn);
        responseIn               = new BufferedReader(serverIn);
        PrintWriter out                      = new PrintWriter(serverWriter);

        // other stuff
        servAddress = InetAddress.getLocalHost();
    }

    public void pushln(String s)
    {
        pusher.println(s);
        pusher.flush();
    }

    public void push(String s)
    {
        pusher.print(s);
        pusher.flush();
    }

    public String getResponse()
    {
        try
        {

            return responseIn.readLine();
        }
        catch (IOException ioe)
        {
            TestCase.fail("Unexpected exception: " + ioe);
        }

        // we never actually get this far...
        return null;
    }

    boolean haveClosed;

    public void close()
               throws IOException
    {
        haveClosed = true;
    }

    public boolean haveClosed()
    {

        return haveClosed;
    }

    public String getClientAddress()
    {

        return "(Client Address)";
    }

    public InetAddress getServerAddress()
    {

        return servAddress;
    }

    public String getServerGreetingsName()
    {

        return "localhost";
    }

    boolean haveQuit;

    public void quit()
    {
        haveQuit = true;
    }

    public boolean haveQuit()
    {

        return haveQuit;
    }

    public boolean isLocal() throws IOException {
      return true;
    }
}