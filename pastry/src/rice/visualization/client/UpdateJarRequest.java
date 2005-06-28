/*
 * Created on Mar 11, 2004
 */
package rice.visualization.client;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;

import rice.environment.Environment;

/**
 * @author jeffh
 *
 */
public class UpdateJarRequest implements Serializable {

  public String executeCommand;
  ArrayList jars;
  public long waitTime = 10000;

  public UpdateJarRequest(File[] files, Environment env) throws IOException {    
    jars = new ArrayList();
    for (int i = 0; i < files.length; i++) {
      FileMessage fm = new FileMessage(files[i]);
      fm.readFile(env);
      jars.add(fm);
    }
  }
  
  public void writeFiles() throws IOException {
    Iterator i = jars.iterator();
    while (i.hasNext()) {
      FileMessage fm = (FileMessage)i.next();
      fm.writeFile();
    }
  }
  
  public void writeFiles(File directory) throws IOException {
    Iterator i = jars.iterator();
    while (i.hasNext()) {
      FileMessage fm = (FileMessage)i.next();
      fm.writeFile(directory);
    }    
  }

  public long getWaitTime() {
    return waitTime;
  }

}
