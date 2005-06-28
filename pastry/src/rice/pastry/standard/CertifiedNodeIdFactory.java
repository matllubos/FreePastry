
package rice.pastry.standard;

import java.io.*;
import java.security.*;
import java.security.cert.*;
import java.security.spec.*;
import java.util.*;
import java.util.zip.*;
import javax.crypto.*;
import javax.crypto.spec.*;

import rice.environment.Environment;
import rice.environment.logging.Logger;
import rice.environment.random.simple.SimpleRandomSource;
import rice.pastry.*;
import rice.p2p.util.*;

/**
 * Builds nodeIds in a certified manner, guaranteeing that a given node will always
 * have the same nodeId.  NOTE:  Actual certification is not yet implemented, rather, 
 * using this factory simply guarantees that the node's nodeId will never change.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */
public class CertifiedNodeIdFactory implements NodeIdFactory {
  
  public static String NODE_ID_FILENAME = "nodeId";

  protected int port;
  protected IPNodeIdFactory realFactory;

  protected Environment environment;
  /**
   * Constructor.
   */
  public CertifiedNodeIdFactory(int port, Environment env) {
    this.environment = env;
    this.port = port;
    this.realFactory = new IPNodeIdFactory(port, env);
  }
  
  /**
   * generate a nodeId
   *
   * @return the new nodeId
   */
  public NodeId generateNodeId() {
    XMLObjectInputStream xois = null;
    try {
      File f = new File(NODE_ID_FILENAME);
      
      if (! f.exists()) {
        File g = new File("." + NODE_ID_FILENAME + "-" + port);
        
        if (g.exists())
          g.renameTo(f);
      }
      
      if (f.exists()) {
        xois = new XMLObjectInputStream(new FileInputStream(f));
        return (NodeId) xois.readObject();
      } else {
        environment.getLogManager().getLogger(CertifiedNodeIdFactory.class,null).log(Logger.WARNING,
          "Unable to find NodeID certificate - exiting.");
        throw new RuntimeException("Unable to find NodeID certificate - make sure that the NodeID certificate file '" + NODE_ID_FILENAME + "' exists in your ePOST directory.");
      }
    } catch (IOException e) {
      environment.getLogManager().getLogger(CertifiedNodeIdFactory.class,null).logException(Logger.WARNING,"",e);
      throw new RuntimeException(e);
    } catch (ClassNotFoundException e) {
      environment.getLogManager().getLogger(CertifiedNodeIdFactory.class,null).logException(Logger.WARNING,"",e);
      throw new RuntimeException(e);
    } finally {
      try {
        if (xois != null)
          xois.close();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  /**
   * Method which generates a certificate given the nodeid, location, and private key
   *
   * @param id The id of the certificate to generate
   * @param file The location to write the certificate to
   * @param key The private key to use to sign the result
   */
  public static void generateCertificate(NodeId id, File file, PrivateKey key) {
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      XMLObjectOutputStream xoos = new XMLObjectOutputStream(baos);
      xoos.writeObject(id);
      xoos.close();
      
      XMLObjectOutputStream xoos2 = new XMLObjectOutputStream(new FileOutputStream(file));
      xoos2.writeObject(id);
      xoos2.write(SecurityUtils.sign(baos.toByteArray(), key));
      xoos2.close();
    } catch (IOException e) {
      throw new RuntimeException(e);
    } 
  }
  
  /**
   * Main method which, for convenience, allows certificate creation.  The parameters allowed are
   * -ca [file] -out [dir]
   */
  public static void main(String[] args) throws Exception {
    String caDirectory = getArg(args, "-ca");
    String out = getArg(args, "-out");
    
    File f = new File(caDirectory,"ca.keypair.enc");      
    FileInputStream fis = new FileInputStream(f);
    ObjectInputStream ois = new XMLObjectInputStream(new BufferedInputStream(new GZIPInputStream(fis)));
    
    byte[] cipher = (byte[]) ois.readObject();
      
    File pwFile = new File(caDirectory,"pw"); 
    StreamTokenizer st = new StreamTokenizer(new BufferedReader(new InputStreamReader(new FileInputStream(pwFile))));
    st.nextToken();
    
    KeyPair caPair = (KeyPair) SecurityUtils.deserialize(SecurityUtils.decryptSymmetric(cipher, SecurityUtils.hash(st.sval.getBytes())));
          
    generateCertificate(new RandomNodeIdFactory(new Environment()).generateNodeId(), new File("/tmp/epost/" + out + "/" + NODE_ID_FILENAME), caPair.getPrivate());
  }
  
  public static String getArg(String[] args, String argType) {
    for (int i = 0; i < args.length; i++) {
      if (args[i].startsWith(argType)) {
        if (args.length > i+1) {
          String ret = args[i+1];
          if (!ret.startsWith("-"))
            return ret;
        } 
      } 
    } 
    return null;
  }  
}

