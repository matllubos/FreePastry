package rice.email.proxy.test.imap;

import rice.email.proxy.imap.*;

import java.io.*;
import java.net.*;

import junit.framework.TestCase;


public class MockImapConnection
    extends ImapConnection
{
    PrintWriter pusher;
    InetAddress servAddress;
    BufferedReader responseIn;

    PipedWriter serverWriter;
    PipedReader serverIn;

    StringBuffer buffer = new StringBuffer();
      
    public MockImapConnection()
                       throws IOException
    {
        super(null, null, false);

        // simulate client input
        PipedReader clientIn     = new PipedReader();
        PipedWriter clientWriter = new PipedWriter(clientIn);
        _in                      = new BufferedReader(clientIn);
        pusher                   = new PrintWriter(clientWriter);

        // intercept server response
        serverIn     = new PipedReader();
        serverWriter             = new PipedWriter(serverIn);
        responseIn               = new BufferedReader(serverIn);
        _out                     = new PrintWriter(serverWriter);

    }

   /* public static class MyPipedWriter extends PipedWriter {

      public MyPipedWriter(PipedReader reader) throws IOException {
        super(reader);
      }
      
      public Object getLock() {
        return lock;
      }

    }

    public static class MyBufferedReader {

      private byte[] buffer;
      private Reader reader;
      private int mark = 0;

      public MyBufferedReader(Reader in) {
        this.reader = in;
        buffer = new byte[65536];
      }

      public String readLine() {
        

    } */

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

    public void print(String string) {
      System.out.print(string);
       synchronized (buffer) {
         buffer.append(string);
         buffer.notify();
       }
    }

    public void println(String string) {
      System.out.println("S: " + string);
      
      synchronized (buffer) {
        buffer.append(string + "\n");
        buffer.notify();
      }
    }

    public String getResponse() {
      try {
        String current = null;

        synchronized (buffer) {
          current = buffer.toString();

          while (current.indexOf("\n") == -1) {
            buffer.wait();
            current = buffer.toString();
          }

          current = current.substring(0, current.indexOf("\n"));
          buffer.delete(0, current.length()+1);
        }

        current = current.replaceAll("\r", "");
        
       // System.out.println("CLIENT - READ: '" + current + "' BUFFER: '" + buffer.toString() + "'");

        return current;
      } catch (Exception ioe) {
        TestCase.fail("Unexpected exception: " + ioe);
        return null;
      }
    }
      
      
      
      
    /*    try
        {
          String response = null;
          
          System.out.println("CLIENT - ABOUT TO READ");

          synchronized (this) { //(serverWriter.getLock()) {
            System.out.println("CLIENT - NOW READING");
       //     if (! serverIn.ready()) {
         //     System.out.println("CLIENT - GOING TO SLEEP");
     //         serverWriter.getLock().wait();
      //      }
            
           // System.out.println("CLIENT - WOKE UP");
            
            response = responseIn.readLine();
          }

          System.out.println("CLIENT - DONE READING");

          if (response == null) {
            System.out.println("CLIENT - FOUND NULL RESPONSE - SLEEP FOR A WHILE");
            
            synchronized (responseIn.getLock()) {
              responseIn.getLock().wait(500);
            }
            
            return getResponse();
          }

            return response;
        }
        catch (Exception ioe)
        {
            TestCase.fail("Unexpected exception: " + ioe);
        }

        // we never actually get this far...
        return null; */

    boolean haveClosed;

    public void close()
               throws IOException
    {
    	pusher.close();
    	_out.close();
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

    boolean haveQuit;

    public void quit()
    {
        haveQuit = true;
    }

    public boolean haveQuit()
    {

        return haveQuit;
    }
}