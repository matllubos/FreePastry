package rice.email.proxy.smtp.client;

import rice.email.proxy.util.StreamUtils;
import rice.environment.Environment;
import rice.environment.logging.Logger;

import java.io.*;
import java.net.*;
import rice.p2p.util.*;

public class SmtpClient {

  String _host;
  BufferedReader _in;
  Writer _out;
  private static final int SMTP_PORT = 25;
  private static final int TIMEOUT   = 1000 * 5;

  private Environment environment;
  protected Logger logger;
  
  public SmtpClient(String host, Environment env) {
    if (host == null)
      throw new NullPointerException();

    _host = host;
    this.environment = env;
    this.logger = environment.getLogManager().getLogger(SmtpClient.class, null);
  }

  public void connect() throws IOException, SmtpProtocolException {
    Socket sock = new Socket(_host, SMTP_PORT);
    sock.setSoTimeout(TIMEOUT);
    _in  = new BufferedReader(new InputStreamReader(sock.getInputStream()));
    _out = new OutputStreamWriter(sock.getOutputStream());

    String greetings = _in.readLine();
    if (greetings == null || !greetings.startsWith("220"))
      throw new SmtpProtocolException();

    String response = sendCommand("HELO myname");
    if (!response.startsWith("250"))
      throw new SmtpProtocolException("Unsucessful HELO: " +
                                      response);
  }

  private String sendCommand(String cmd) throws IOException, SmtpProtocolException {
    if (logger.level <= Logger.FINEST) logger.log( 
        "C: " + cmd);
    _out.write(cmd);
    _out.write("\r\n");
    _out.flush();

    String response = _in.readLine();
    if (logger.level <= Logger.FINEST) logger.log( 
        "S: " + response);
    if (response == null)
      throw new SmtpProtocolException("Connection unexpectedly closed");

    return response;
  }

  public void send(String returnPath, String rcptTo, Reader message) throws IOException, SmtpProtocolException {
    String response = sendCommand("MAIL FROM: <" + returnPath + ">");
    if (!response.startsWith("250"))
      throw new SmtpProtocolException("Unsucessful MAIL FROM: " + response);

    response = sendCommand("RCPT TO: <" + rcptTo + ">");
    if (!response.startsWith("250"))
      throw new SmtpProtocolException("Unsucessful RCPT TO: " + response);

    response = sendCommand("DATA");
    if (!response.startsWith("354"))
      throw new SmtpProtocolException("Unsucessful DATA: " + response);

    StreamUtils.copy(message, _out);
    response = sendCommand("\r\n.");
    if (!response.startsWith("250"))
      throw new SmtpProtocolException("Unsucessful DATA content: " + response);
  }
  
  public void send(String returnPath, String rcptTo, Reader message, String username, String password) throws IOException, SmtpProtocolException {
		String response = sendCommand("AUTH CRAM-MD5");
    
    if (response.startsWith("334")) {
      String text = new String(Base64.decode(response.substring(response.indexOf(" ")+1)));
      
      String response2 = sendCommand(Base64.encodeBytes((username + " " + SecurityUtils.hmac(password.getBytes(), text.getBytes())).getBytes()));
      
      if (! response2.startsWith("235")) throw new SmtpProtocolException("Authentication failed: " + response2);      
    } else {
      String response2 = sendCommand("AUTH LOGIN");
      
      if (!response2.startsWith("334")) throw new SmtpProtocolException("Unsuccessful AUTH command (CRAM-MD5: " + response + ", LOGIN: " + response2 + ")");  

      String command = new String(Base64.decode(response2.substring(response2.indexOf(" ")+1))).trim().toLowerCase();
      if (!command.equals("username")) throw new SmtpProtocolException("Unknown AUTH LOGIN username response: " + command);
          
      String response3 = sendCommand(Base64.encodeBytes(username.getBytes()));
      
      command = new String(Base64.decode(response3.substring(response3.indexOf(" ")+1))).trim().toLowerCase();
      if (!command.equals("password")) throw new SmtpProtocolException("Unknown AUTH LOGIN password response: " + command);
      
      String response4 = sendCommand(Base64.encodeBytes(password.getBytes()));

      if (! response4.startsWith("235")) throw new SmtpProtocolException("Authentication failed: " + response4);
    }
		
    send(returnPath, rcptTo, message);
  }
  

  public void close() throws IOException {
    try {
      sendCommand("QUIT");
    } catch (SmtpProtocolException ignore) {
    }
  }
}
