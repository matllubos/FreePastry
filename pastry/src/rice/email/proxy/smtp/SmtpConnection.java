package rice.email.proxy.smtp;

import java.io.*;
import java.net.*;

public class SmtpConnection {

    // TODO: clean up getting localhost name
    private static final int TIMEOUT_MILLIS = 1000 * 30;
    private InetAddress serverAddress;
    private SmtpServer server;


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
    public PrintWriter out;
    //public BufferedReader in;
    public StreamTokenizer in;
    SmtpHandler handler;
    String heloName;
    boolean log;

    public SmtpConnection(SmtpHandler handler, Socket sock, SmtpServer server, boolean log) throws IOException {
        if (sock != null) {
          this.sock = sock;
          sock.setSoTimeout(TIMEOUT_MILLIS);
          clientAddress = sock.getInetAddress();
          OutputStream o = sock.getOutputStream();
          InputStream i = sock.getInputStream();
          out = new PrintWriter(o, true);
          in = new StreamTokenizer(new InputStreamReader(i));
          
          in.resetSyntax();
          in.eolIsSignificant(false);
          in.wordChars(1, Integer.MAX_VALUE);
          in.whitespaceChars(10, 10);
        }
          
      this.server = server;
        this.handler = handler;
        this.log = log;
    }
    
    public SmtpServer getServer() {
      return server;
    }
    
    public StreamTokenizer getTokenizer() {
      return in;
    }

    public void println(String line) {
        if (log) System.out.println("S: " + line);
        out.print(line + "\r\n");
        out.flush();
    }

    public String readLine() throws IOException {
      int result = in.nextToken();
      
      if (result == in.TT_WORD) {
        if (log)  System.out.println("C: " + in.sval);
        return in.sval;
      } else if (result == in.TT_NUMBER) {
        if (log) System.out.println("C*:" + in.nval);
        return "" + in.nval;
      } else {
        return null;
      }
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

    public boolean isLocal() throws IOException {
      return (sock.getInetAddress().isLoopbackAddress() ||
              sock.getInetAddress().equals(InetAddress.getLocalHost()));
    }
}