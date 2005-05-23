package rice.email.proxy.pop3;

import rice.email.proxy.util.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Reader;

import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;


public class Pop3Connection {
  
  // networking stuff
  private static final int TIMEOUT_MILLIS = 1000 * 60 * 30;
  Socket _socket;
  InetAddress _clientAddress;
  
  // IO stuff
  BufferedReader _in;
  PrintWriter _out;
  boolean log;
  
  Pop3Handler handler;
  
  public Pop3Connection(Pop3Handler handler, Socket socket, boolean log) throws IOException {
    this.handler = handler;
    this.log = log;
    configureSocket(socket);
    configureStreams();
  }
  
  private void configureStreams() throws IOException {
    OutputStream o = _socket.getOutputStream();
    InputStream i = _socket.getInputStream();
    _out = new PrintWriter(o, true);
    _in = new BufferedReader(new InputStreamReader(i));
  }
  
  private void configureSocket(Socket socket) throws SocketException {
    _socket = socket;
    _socket.setSoTimeout(TIMEOUT_MILLIS);
    _clientAddress = _socket.getInetAddress();
  }
  
  public void quit() {
    try {
      close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
  
  public void close() throws IOException {
    _socket.close();
  }
  
  public void println(String line) {
    if (log) System.out.println("S: " + line);
    _out.print(line);
    println();
  }
  
  public void print(String line) {
    if (log) System.out.print(line);
    _out.print(line);
  }
  
  public void println() {
    if (log) System.out.println();
    _out.print("\r\n");
    _out.flush();
  }
  
  public void print(Reader in) throws IOException {
    StreamUtils.copy(in, _out);
    _out.flush();
  }
  
  public String readLine() throws IOException {
    String line = _in.readLine();
    if (log) System.out.println("C: " + line);
    
    return line;
  }
  
  public String getClientAddress() {
    return _clientAddress.toString();
  }
}