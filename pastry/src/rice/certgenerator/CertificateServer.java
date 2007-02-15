/*******************************************************************************

"FreePastry" Peer-to-Peer Application Development Substrate

Copyright 2002-2007, Rice University. Copyright 2006-2007, Max Planck Institute 
for Software Systems.  All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

- Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

- Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

- Neither the name of Rice  University (RICE), Max Planck Institute for Software 
Systems (MPI-SWS) nor the names of its contributors may be used to endorse or 
promote products derived from this software without specific prior written 
permission.

This software is provided by RICE, MPI-SWS and the contributors on an "as is" 
basis, without any representations or warranties of any kind, express or implied 
including, but not limited to, representations or warranties of 
non-infringement, merchantability or fitness for a particular purpose. In no 
event shall RICE, MPI-SWS or contributors be liable for any direct, indirect, 
incidental, special, exemplary, or consequential damages (including, but not 
limited to, procurement of substitute goods or services; loss of use, data, or 
profits; or business interruption) however caused and on any theory of 
liability, whether in contract, strict liability, or tort (including negligence
or otherwise) arising in any way out of the use of this software, even if 
advised of the possibility of such damage.

*******************************************************************************/ 
package rice.certgenerator;

import rice.environment.Environment;
import rice.p2p.commonapi.*;
import rice.p2p.multiring.*;
import rice.p2p.util.*;
import rice.pastry.commonapi.*;
import rice.pastry.standard.*;
import rice.post.*;
import rice.post.security.*;
import rice.post.security.ca.*;

import java.io.*;
import java.net.*;
import java.sql.*;
import java.util.*;
import java.util.zip.*;
import java.util.regex.*;
import java.nio.charset.Charset;

import java.security.*;

public class CertificateServer {
  public abstract static class Request {
    public String uri;

    public Map headers;

    public Map arguments;

    public final static String METHOD = "GET";

    public Request(String _uri, Map _headers) {
      uri = _uri;
      headers = _headers;
      arguments = new HashMap();

      if (uri.indexOf("?") > 0) {
        String[] args = uri.substring(uri.indexOf("?") + 1).split("&");

        for (int i = 0; i < args.length; i++)
          if (args[i].indexOf("=") > 0)
            arguments.put(args[i].substring(0, args[i].indexOf("=")), args[i]
                .substring(args[i].indexOf("=") + 1));
      }
    }

    public Request(String _uri) {
      uri = _uri;
      headers = new HashMap();
    }

    public String getArgument(String name) {
      return (String) (arguments.get(name));
    }

    public String getHeader(String name) {
      return (String) (headers.get(name));
    }

    public void setHeader(String name, String value) {
      headers.put(name, value);
    }
  }

  public static class GetRequest extends Request {
    public static final String METHOD = "GET";

    public GetRequest(String u, Map h) {
      super(u, h);
    }

    public GetRequest(String u) {
      super(u);
    }
  }

  public static class PostRequest extends Request {
    public String body;

    public static final String METHOD = "POST";

    public PostRequest(String u, Map h, StringBuffer _bodyBuf) {
      super(u, h);
      setBody(_bodyBuf);
    }

    public PostRequest(String u, Map h, String _body) {
      super(u, h);
      setBody(_body);
    }

    public void setBody(String _body) {
      body = _body;
    }

    public void setBody(StringBuffer _bodyBuf) {
      body = _bodyBuf.toString();
    }
  }

  public static class PutRequest extends PostRequest {
    public static final String METHOD = "PUT";

    public PutRequest(String u, Map h, StringBuffer _bodyBuf) {
      super(u, h, _bodyBuf);
    }

    public PutRequest(String u, Map h, String _body) {
      super(u, h, _body);
    }
  }

  public static abstract class Handler extends Thread {
    protected static class WebPrintWriter extends PrintWriter {
      public WebPrintWriter(OutputStream out) {
        super(out);
      }

      public WebPrintWriter(OutputStream out, boolean autoFlush) {
        super(out, autoFlush);
      }

      public WebPrintWriter(Writer out) {
        super(out);
      }

      public WebPrintWriter(Writer out, boolean autoFlush) {
        super(out, autoFlush);
      }

      public void println() {
        print("\r\n");
      }
    }

    protected boolean PRINT_EXCEPTIONS = true;

    protected Socket socket;

    protected BufferedReader input;

    protected OutputStream rawOutput;

    protected WebPrintWriter output;

    protected boolean headersSent;

    public Handler() {
      super("CertificateServer.Handler");
      headersSent = false;
    }

    protected void attach(Socket _socket) throws java.io.IOException {
      socket = _socket;
      input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
      rawOutput = socket.getOutputStream();
      output = new WebPrintWriter(new BufferedWriter(new OutputStreamWriter(
          rawOutput, Charset.forName("UTF-8"))));
    }

    protected void header(String s) {
      if (headersSent) {
        // throw
      } else {
        output.println(s);
      }
    }

    protected void endHeaders() {
      if (headersSent) {
        // throw
      } else {
        output.println();
        headersSent = true;
      }
    }

    protected void printHttpResponse(int code) {
      if (code == 404) {
        header("HTTP/1.1 404 Not Found");
      } else if (code == 200) {
        header("HTTP/1.1 200 OK");
      } else if (code == 301) {
        header("HTTP/1.1 301 Document Moved");
      } else if (code == 302) {
        header("HTTP/1.1 302 Document Moved");
      } else {
        header("HTTP/1.1 500 Internal Server Error");
      }
    }

    protected void die(Exception exc) {
      try {
        printHttpResponse(500);
        header("Content-type: text/html");
        header("Connection: close");
        endHeaders();
        output.println("<h1>Proxy Error</h1>");
        output.print("<p>An exception (<i>" + exc + "</i>) was raised.</p>");
        if (PRINT_EXCEPTIONS)
          printException(exc);
        printFooter();
      } catch (Exception e) {
        // um, really have to bail out now
        System.out.println("Handler failed to die():");
        e.printStackTrace(System.out);
      }
    }

    protected void notFound(String uri) {
      printHttpResponse(404);
      header("Content-type: text/html");
      header("Connection: close");
      endHeaders();
      output.println("<h1>404 Not Found</h1>The URL <tt>" + uri
          + "</tt> was not found.");
      printFooter();
    }

    protected void printException(Exception exc) {
      output
          .print("<hr style=\"display: none;\"><pre style=\"font-size: small; background-color: #eee; margin-top: 1.5em; padding: 1em; border: 1px solid #999; -moz-border-radius: 4px;\">");
      exc.printStackTrace(output);
      output.println("</pre>");
    }

    protected void printFooter() {
      output
          .println("<hr style=\"display: none;\"><div style=\"font-family: sans-serif; font-size: small; background-color: #eee; margin-top: 1.5em; padding: 1em; border: 1px solid #999; -moz-border-radius: 4px;\">CertificateServer Java server (<tt>"
              + getClass() + "</tt>)</div>");
    }

    protected abstract void handle(Request request);

    static Pattern kHeaderPattern = Pattern.compile("^([^:]+): (.*)$");

    public void run() {
      try {
        String request, uri;
        request = input.readLine();
        boolean isGet = request.startsWith("GET ")
            || request.startsWith("HEAD ");
        boolean isPost = request.startsWith("POST ")
            || request.startsWith("PUT ");
        if (isGet || isPost) {
          uri = (request.split(" "))[1];
          Map headers = new HashMap();
          String headerLine = input.readLine();
          while (!headerLine.equals("")) {
            Matcher m = kHeaderPattern.matcher(headerLine);
            if (m.matches()) {
              headers.put(m.group(1), m.group(2));
            }
            headerLine = input.readLine();
          }

          StringBuffer postBuf = null;

          Request requestObj;

          if (isPost) {
            postBuf = new StringBuffer();
            char buf[] = new char[4096];
            int len;
            int contentLength = -1;
            String contentLengthStr = (String) (headers
                .get((Object) "Content-Length"));
            if (contentLengthStr != null) {
              contentLength = new Integer(contentLengthStr).intValue();
            }
            System.out
                .println("[CertificateServer.run] POST/PUT content-length = "
                    + contentLength);
            if (contentLength > 0) {
              while (contentLength > 0) {
                len = input.read(buf, 0, 4096);
                if (len >= 0) {
                  postBuf.append(buf, 0, len);
                } else {
                  System.out
                      .println("[CertificateServer.run] parsing form post: Content-length was "
                          + (String) (headers.get("Content-Length"))
                          + " but read() returned "
                          + len
                          + " before all content had been read.");
                  break;
                }
                contentLength -= len;
              }
            } else {
              while ((len = input.read(buf, 0, 4096)) >= 0)
                postBuf.append(buf, 0, len);
            }
            if (request.startsWith("PUT "))
              requestObj = new PutRequest(uri, headers, postBuf);
            else
              requestObj = new PostRequest(uri, headers, postBuf);
          } else {
            if (request.startsWith("GET "))
              requestObj = new GetRequest(uri, headers);
            else
              // XXX: fixme
              requestObj = new GetRequest(uri, headers);

          }
          handle(requestObj);
        }
      } catch (Exception e) {
        die(e);
      }
      output.flush();
      output.close();
    }
  };

  public void serve(int port) throws java.io.IOException,
      java.lang.IllegalAccessException {
    serve(port, true);
  }

  public void serve(int port, boolean isPublic) throws java.io.IOException,
      java.lang.IllegalAccessException {
    ServerSocket s;
    if (isPublic) {
      s = new ServerSocket(port, kConnectionBacklog);
    } else {
      // byte loopback[] = new byte[4];
      // loopback[0] = 127; loopback[1] = loopback[2] = 0; loopback[3] = 1;
      s = new ServerSocket(port, kConnectionBacklog, InetAddress
          .getByName("localhost"));
      // InetAddress.getByAddress(loopback));
      // InetAddress.getLocalHost()); // <-- will this deny connections from
      // other hosts? doubtful.
    }

    for (;;) {
      Handler handler = (Handler) (m_handlerFactory.newHandler());
      handler.attach(s.accept()); // blocks
      handler.start();
    }
  }

  public abstract static class HandlerFactory {
    public abstract Handler newHandler();
  }

  protected static int kConnectionBacklog = 50;

  protected HandlerFactory m_handlerFactory;

  public CertificateServer(HandlerFactory handlerFactory) {
    m_handlerFactory = handlerFactory;
  }

  public static void main(String[] args) throws Exception {
    int port = 8000;
    if (args.length >= 1) {
      port = new Integer(args[0]).intValue();
    }
    System.out.println("Starting demo server on port " + port);

    new Thread() {
      public void run() {
        try {
          String hostname = InetAddress.getLocalHost().getHostName();

          Class.forName("com.mysql.jdbc.Driver");
          Connection connection = DriverManager.getConnection(
              "jdbc:mysql://svn.mpi-sws.mpg.de/epost",
                  "epost", "monkey");
          PreparedStatement insert = connection
              .prepareStatement("insert into cert_servers (hostname, alive) values ('"
                  + hostname + "', NOW())");

          try {
            insert.executeUpdate();
          } catch (Exception e) {
          }

          connection.close();

          while (true) {
            try {
              Connection conn = DriverManager
                  .getConnection("jdbc:mysql://svn.mpi-sws.mpg.de/epost",
                      "epost", "monkey");
              PreparedStatement update = conn
                  .prepareStatement("update cert_servers set alive=NOW() where hostname='"
                      + hostname + "'");

              update.executeUpdate();

              conn.close();
            } catch (Exception e) {
              System.err.println("Can't insert alive record due to " + e);
            }

            try {
              sleep(3 * 60 * 1000);
            } catch (Exception e) {
            }
          }
        } catch (Exception e) {
          System.err.println("Udpate error " + e);
        }
      }
    }.start();

    new CertificateServer(new HandlerFactory() {
      public Handler newHandler() {
        return new CertificateHandler();
      }
    }).serve(port);
  }

  static class CertificateHandler extends Handler {
    protected void handle(Request request) {
      try {
        if (request.uri.startsWith("/certificate")) {
          Environment env = new Environment();
          String username = request.getArgument("username");
          String password = request.getArgument("password");
          String ring = request.getArgument("ring");

          if ((username == null) || (password == null) || (ring == null))
            throw new Exception("Not all arguments present - " + username + " "
                + password + " " + ring);

          File f = new File("ca.keypair.enc");
          ObjectInputStream ois = new XMLObjectInputStream(
              new BufferedInputStream(new GZIPInputStream(
                  new FileInputStream(f))));
          KeyPair caPair = (KeyPair) SecurityUtils.deserialize(SecurityUtils
              .decryptSymmetric((byte[]) ois.readObject(), SecurityUtils
                  .hash("monkey".getBytes())));
          ois.close();

          KeyPair pair = SecurityUtils.generateKeyAsymmetric();

          IdFactory realFactory = new PastryIdFactory(env);
          Id ringId = realFactory.buildId(ring.substring(0, 1).toUpperCase()
              + ring.substring(1).toLowerCase());
          byte[] ringData = ringId.toByteArray();

          for (int i = 0; i < ringData.length
              - env.getParameters().getInt("p2p_multiring_base"); i++)
            ringData[i] = 0;

          ringId = realFactory.buildId(ringData);

          PostUserAddress address = new PostUserAddress(new MultiringIdFactory(
              ringId, realFactory), username + "@" + ring.toLowerCase(), env);
          PostCertificate certificate = CASecurityModule.generate(address, pair
              .getPublic(), caPair.getPrivate());

          ByteArrayOutputStream baos = new ByteArrayOutputStream();
          CACertificateGenerator.write(certificate, pair, password, baos);
          byte[] cert = baos.toByteArray();

          ByteArrayOutputStream baos2 = new ByteArrayOutputStream();
          CertifiedNodeIdFactory.generateCertificate(new RandomNodeIdFactory(
              env).generateNodeId(), baos2, caPair.getPrivate());
          byte[] nodeID = baos2.toByteArray();

          Class.forName("com.mysql.jdbc.Driver");
          Connection connection = DriverManager.getConnection(
              "jdbc:mysql://svn.mpi-sws.mpg.de/epost",
                  "epost", "monkey");
          PreparedStatement stmt = connection
              .prepareStatement("update certificates set cert_data=?, nodeid_data=? where name=? and ring=?");

          stmt.setBytes(1, cert);
          stmt.setBytes(2, nodeID);
          stmt.setString(3, username);
          stmt.setString(4, ring);

          stmt.executeUpdate();

          connection.close();

          printHttpResponse(200);
          header("Content-type: text/html");
          header("Connection: close");
          endHeaders();

          output.println("Certificate: <br>" + MathUtils.toHex(cert));
          output.println("<p>");
          output.println("NodeId: <br>" + MathUtils.toHex(nodeID));
          output.close();
        } else {
          notFound(request.uri);
        }
      } catch (Exception e) {
        die(e);
      }
    }
  }
}

// inspired by http://www.mcwalter.org/technology/java/httpd/tiny/index.html
