
package rice.persistence.testing;

/*
 * @(#) PersistentStorageTest.java
 *
 * @author Ansley Post
 * @author Alan Mislove
 * 
 * @version $Id$
 */
import java.io.*;
import java.util.*;
import java.util.zip.*;

import rice.*;
import rice.p2p.commonapi.*;
import rice.p2p.glacier.*;
import rice.p2p.glacier.v2.*;
import rice.p2p.util.*;
import rice.pastry.commonapi.*;
import rice.persistence.*;

/**
 * This class is a class which tests the PersistentStorage class
 * in the rice.persistence package.
 */
public class GlacierPersistentStorageTest {
    
  File root;
  
  /**
   * Builds a MemoryStorageTest
   */
  public GlacierPersistentStorageTest(String root) throws IOException {
    this.root = new File("FreePastry-Storage-Root/" + root);
  }
  
  public void start() throws Exception {
    process(root);
  }
  
  protected void process(File file) throws Exception {
    File[] files = file.listFiles();
    
    for (int i=0; i<files.length; i++) {
      /* check each file and recurse into subdirectories */
      if (files[i].isFile() && (files[i].getName().length() > 20)) {
        ObjectInputStream objin = new XMLObjectInputStream(new BufferedInputStream(new GZIPInputStream(new FileInputStream(files[i]))));
        objin.readObject(); 
        
        Object o = objin.readObject();
        if (o instanceof FragmentAndManifest) {
          FragmentAndManifest fm = (FragmentAndManifest) o;
          
          int total = fm.fragment.payload.length + 24;
          
          total += fm.manifest.getObjectHash().length + fm.manifest.getSignature().length;
          total += fm.manifest.getFragmentHashes().length * fm.manifest.getFragmentHashes()[0].length;
          System.out.println(files[i].getName() + "\t" + total + "\t" + files[i].length());
        } else {
          System.out.println("ERROR: Found class " + o.getClass().getName());
        }
        objin.close();
        
      } else if (files[i].isDirectory()) {
        process(files[i]);
      }
    }
  }
  
  public static void main(String[] args) throws Exception {
    GlacierPersistentStorageTest test = new GlacierPersistentStorageTest("sys08.cs.rice.edu-10001-glacier-immutable");
    
    test.start();
  }
}
