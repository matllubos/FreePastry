package rice.email.proxy.smtp.client;

import rice.email.proxy.util.StreamUtils;

import java.io.*;
import java.net.*;

public class SmtpClient {

  String _host;
  BufferedReader _in;
  Writer _out;
  private static final int SMTP_PORT = 25;
  private static final int TIMEOUT   = 1000 * 5;

  public SmtpClient(String host) {
    if (host == null)
      throw new NullPointerException();

    _host = host;
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
    System.out.println("C: " + cmd);
    _out.write(cmd);
    _out.write("\r\n");
    _out.flush();

    String response = _in.readLine();
    System.out.println("S: " + response);
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

  public void close() throws IOException {
    try {
      sendCommand("QUIT");
    } catch (SmtpProtocolException ignore) {
    }
  }
}