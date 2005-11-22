package rice.email.proxy.web;

import java.io.*;
import java.net.*;
import java.text.*;
import java.util.*;

import rice.environment.Environment;
import rice.environment.logging.Logger;

public class WebConnection {
  
  // the statuses
  public static final String STATUS_OK = "200 OK";
  public static final String STATUS_REDIRECT = "303 Moved";
  public static final String STATUS_AUTH_REQUIRED = "403 Authentication Required";
  public static final String STATUS_NOT_FOUND = "404 Not Found";
  public static final String STATUS_ERROR = "500 Internal Error";
  public static final String STATUS_INVALID_REQUEST = "501 Invalid Request";
  
  public static final int TYPE_GET = 1;
  public static final int TYPE_POST = 2;
  
  public static final String LINE_FEED = "\r\n";
  
  // TODO: clean up getting localhost name
  private static final int TIMEOUT_MILLIS = 1000 * 30;
  
  public static final SimpleDateFormat DATE = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss z");

  
  // networking/io stuff
  protected Socket socket;
  public Writer out;
  public StreamTokenizer in;
  
  // the handler for this connection
  protected WebHandler handler;
  
  // the propeties of this request
  protected int type;
  protected HashMap options;
  protected HashMap parameters;
  protected String http;
  protected String request;
  
  // the buffered response
  protected String status;
  protected String redirect;
  protected StringBuffer response;
  
  protected Environment environment;
  protected Logger logger;
  
  public WebConnection(WebHandler handler, Socket socket, Environment env) throws IOException {
    this.environment = env;
    this.logger = environment.getLogManager().getLogger(WebConnection.class, null);
    this.socket = socket;
    this.socket.setSoTimeout(TIMEOUT_MILLIS);
    this.out = new OutputStreamWriter(socket.getOutputStream());
    this.in = new StreamTokenizer(new InputStreamReader(socket.getInputStream()));
    
    this.in.resetSyntax();
    this.in.eolIsSignificant(false);
    this.in.wordChars(1, Integer.MAX_VALUE);
    this.in.whitespaceChars(10, 10);
    
    this.handler = handler;
    this.options = new HashMap();
    this.parameters = new HashMap();
    this.response = new StringBuffer();
    this.status = STATUS_OK;
  }
  
  public String getParameter(String name) {
    return (String) parameters.get(name);
  }
  
  protected void parseParameters(String parameters) {
    String[] pairs = parameters.split("&");
    
    for (int i=0; i<pairs.length; i++) {
      String[] pair = pairs[i].split("=");
      
      if (pair.length == 2) {
        this.parameters.put(cleanse(pair[0]), cleanse(pair[1]));
        if (logger.level <= Logger.FINE) logger.log(
            "READ IN " + cleanse(pair[0]) + "->" + cleanse(pair[1]));
      }
    }
  }
  
  protected String cleanse(String string) {
    /*StringBuffer result = new StringBuffer();
    
    int index = 0;
    
    while (index < string.length()) {
      int offset = string.indexOf("%", index);
      
      if (offset >= 0) {
        result.append(string.substring(index, index+offset));
        char c = (char) Integer.parseInt(string.substring(index+1, index+3), 16);
        result.append(c);
        
        index = offset+3;
      } else {
        result.append(string.substring(index));
        index = string.length();
      }
    }
    
    return result.toString();*/
    return string;
  }
  
  public String readRequest() throws WebException, IOException {
    String s = readLine();
    
    if (s.startsWith("GET ")) 
      type = TYPE_GET;
    else if (s.startsWith("POST ")) 
      type = TYPE_POST;
    else
      throw new WebException(STATUS_INVALID_REQUEST, "The request '" + s + "' was invalid.");
    
    s = s.substring(4);
    request = s.substring(0, s.indexOf(" "));
    http = s.substring(s.indexOf(" ") + 1).trim();
    
    String[] split = request.split("\\?");
    
    if (split.length > 1) {
      request = split[0];
      parseParameters(split[1]);
    }
    
    while (((s = readLine()) != null) && (! s.trim().equals(""))) {
      String[] pair = s.split(": ");
      options.put(pair[0].trim(), pair[1].trim());
    }
    
    if (type == TYPE_POST) {
      parseParameters(readLine());
    }
    
    return request;
  }
  
  public void redirect(String location) throws IOException {
    this.redirect = location;
    error(STATUS_REDIRECT, "This content hash moved to '" + location + "'.");
  }
  
  public void error(String status, String message) throws IOException {
    setStatus(status);
    response = new StringBuffer();
    print("<HTML><HEAD><TITLE>");
    print(status);
    print("</TITLE></HEAD><BODY><H1>");
    print(status);
    print("</H1>");
    print(message);
    print("<P><HR><ADDRESS>ePOST Webmail Server</ADDRESS></BODY></HTML>");
    send();
  }
  
  public void setStatus(String status) {
    this.status = status;
  }
  
  public void println(String line) {
    print(line + LINE_FEED);
  }
  
  public void print(String line) {
    if (logger.level <= Logger.FINE) logger.log(
        "S: " + line);

    response.append(line);
  }
  
  private String readLine() throws IOException {
    in.nextToken();
    if (logger.level <= Logger.FINE) logger.log(
        "C: " + in.sval);
    return in.sval;
  }
  
  public void send() throws IOException {
    out.write(http + " " + status + LINE_FEED);
    out.write("Date: " + DATE.format(new Date(handler.getEnvironment().getTimeSource().currentTimeMillis())) + LINE_FEED);
    out.write("Server: ePOST Webmail Server" + LINE_FEED);
    out.write("Content-Length: " + response.toString().length() + LINE_FEED);
    out.write("Connection: close" + LINE_FEED);
    out.write("Content-Type: text/html" + LINE_FEED);
    
    if (redirect != null) {
      out.write("Location: " + redirect + LINE_FEED);
    }
    
    out.write("" + LINE_FEED);
    out.write(response.toString()); 
    out.flush();

    
    socket.close();
  }
  
  public void quit() {
    //handler.quit();
  }
  
  
  public Environment getEnvironment() {
    return environment; 
  }
  
}
