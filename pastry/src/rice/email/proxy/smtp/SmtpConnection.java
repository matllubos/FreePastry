package rice.email.proxy.smtp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;

import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;

public class SmtpConnection {

    // TODO: clean up getting localhost name
    private static final int TIMEOUT_MILLIS = 1000 * 30;
    private InetAddress serverAddress;


    {
        try
        {
            serverAddress = InetAddress.getLocalHost();
        }
        catch (UnknownHostException uhe)
        {
        }
    }

    // networking/io stuff
    Socket sock;
    InetAddress clientAddress;
    PrintWriter out;
    BufferedReader in;
    SmtpHandler handler;
    String heloName;

    public SmtpConnection(SmtpHandler handler, Socket sock)
                   throws IOException
    {
        this.sock = sock;
        sock.setSoTimeout(TIMEOUT_MILLIS);
        clientAddress = sock.getInetAddress();
        OutputStream o = sock.getOutputStream();
        InputStream i = sock.getInputStream();
        out = new PrintWriter(o, true);
        in = new BufferedReader(new InputStreamReader(i));

        this.handler = handler;
    }

    public void println(String line) {

        System.err.println("S: " + line);
        out.print(line + "\r\n");
        out.flush();
        
    }

    public BufferedReader getReader() {

        return in;
    }

    public String readLine() throws IOException {
        String line = in.readLine();

        System.err.println("C: " + line);
        return line;
    }

    public String getClientAddress() {

        return clientAddress.getHostName();
    }

    public InetAddress getServerAddress() {

        return serverAddress;
    }

    public String getServerGreetingsName() {
        InetAddress serverAddress = getServerAddress();

        if (serverAddress != null)
            return serverAddress.toString();
        else
            return System.getProperty("user.name"); 
    }

    public String getHeloName() {

        return heloName;
    }

    public void setHeloName(String n) {
        heloName = n;
    }

    public void quit() {
        handler.quit();
    }
}