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
  
  Pop3Handler handler;
  
  public Pop3Connection(Pop3Handler handler, Socket socket) throws IOException {
    this.handler = handler;
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
    System.out.println("S: " + line);
    _out.print(line);
    println();
  }
  
  public void print(String line) {
    _out.print(line);
  }
  
  public void println() {
    _out.print("\r\n");
    _out.flush();
  }
  
  public void print(Reader in) throws IOException {
    StreamUtils.copy(in, _out);
    _out.flush();
  }
  
  public String readLine() throws IOException {
    String line = _in.readLine();
    System.out.println("C: " + line);
    
    return line;
  }
  
  public String getClientAddress() {
    return _clientAddress.toString();
  }
}