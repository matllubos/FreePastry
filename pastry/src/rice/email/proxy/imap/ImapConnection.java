package rice.email.proxy.imap;

import rice.email.proxy.util.Quittable;
import rice.email.proxy.util.SpyInputStream;
import rice.email.proxy.util.StreamUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Reader;

import java.net.InetAddress;
import java.net.Socket;



/**
 * A <code>Socket</code> wrapper for use by  {@link
 * rice.email.proxy.imap.commands.AbstractImapCommand ImapCommands}.
 */
public class ImapConnection
{

    // This 5-minute timeout isn't strictly-speaking legal.
    // The specs say 30 minutes.
    private static final int TIMEOUT_MILLIS = 1000 * 60 * 5;

    // networking/io stuff
    Socket _socket;
    PrintWriter _out;
    BufferedReader _in;
    InetAddress _clientAddress;
    SpyInputStream _spy;

    // see quit()
    Quittable _handler;

    public ImapConnection(Quittable handler, Socket sock)
                   throws IOException
    {

        // networking
        _socket = sock;
        _socket.setSoTimeout(TIMEOUT_MILLIS);
        _clientAddress = _socket.getInetAddress();

        // streams
        OutputStream out = _socket.getOutputStream();
        InputStream in = _socket.getInputStream();

        /* cheap logging trick */

        // _spy = new SpyInputStream(in, System.out);
        _out = new PrintWriter(out, true);
        _in = new BufferedReader(new InputStreamReader(in));

        // protocol
        _handler = handler;
    }

    /**
     * Writes a string and newline to the output stream.  Currently
     * also writes to System.out for logging purposes.
     * 
     * <p>
     * Command implementations, in general, should not this method.
     * They should instead use one of the methods in {@link
     * rice.email.proxy.imap.commands.AbstractImapCommand}.
     * </p>
     */
    public void println(String line)
    {
        System.out.println("S: " + line);
        _out.print(line);
        _out.print("\r\n");
        _out.flush();
    }

    /**
     * Sometimes you don't need a newline.
     * 
     * <p>
     * Most command implementations should use one of the methods in
     * {@link rice.email.proxy.imap.commands.AbstractImapCommand}. Some
     * commands, like FETCH, require complex replies that are best
     * implemented by writing to an ImapConnection in small chunks.
     * </p>
     */
    public void print(String string)
    {
        System.out.print(string);
        _out.print(string);
    }

    /**
     * Sends the entire contents of a Reader to the client, and
     * closes it.
     * 
     * <p>
     * This method is useful for commands like FETCH, which need to
     * send the contents of StoredMessages.
     * </p>
     * 
     * <p>
     * The data written will not be logged
     * </p>
     */
    public void print(Reader in)
               throws IOException
    {
        StreamUtils.copy(in, _out);
    }

    /**
     * Allows reading directly from the input stream.
     * 
     * <p>
     * There is only one command at the moment which has a good
     * excuse to use this method: APPEND. If at some point it seems
     * like a good idea to allow IMAP literals in any part of any
     * command, then excuses might be given to other classes.
     * </p>
     * 
     * <p>
     * The data written will not be logged
     * </p>
     */
    public BufferedReader getReader()
    {

        return _in;
    }

    /**
     * Reads a line sent by the client.
     * 
     * <p>
     * The only classes with an excuse to use this method at the
     * moment are AppendCommand and ParserImapHandler.
     * </p>
     */
    public String readLine()
                    throws IOException
    {
        String line = _in.readLine();

        /* more crude debug logging */
        System.out.println("C: " + line);

        return line;
    }

    /**
     * Specifies that no more commands should be processed after the
     * current on finishes.
     * 
     * @see rice.email.proxy.imap.Quittable#quit()
     */
    public void quit()
    {
        _handler.quit();
    }

    /**
     * Closes the underlying socket.
     */
    void close()
        throws IOException
    {
        _socket.close();
    }

    /**
     * Returns identifying information (IP and/or hostname) of the
     * client.
     * 
     * <p>
     * For debugging/logging only, at present. The output format of
     * this method may change.
     * </p>
     */
    public String getClientAddress()
    {

        return _clientAddress.toString();
    }
}