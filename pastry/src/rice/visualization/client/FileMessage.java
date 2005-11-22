/*
 * Created on Mar 11, 2004
 *
 */
package rice.visualization.client;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.Properties;

import rice.environment.Environment;
import rice.environment.logging.Logger;

/**
 * @author Jeff Hoye
 *
 * Sends a file.
 * 
 */
public class FileMessage implements Serializable {

  String fileName;
  byte[] bytes;

  transient File file;
  transient File directory;

  public FileMessage(String s) {
    this(new File(s));
  }

  public FileMessage(File f) {
    file = f;
  }

  public void readFile(Environment env) throws IOException {
    fileName = file.getName();
    FileInputStream reader = new FileInputStream(file);
    bytes = new byte[(int)file.length()];
    Logger logger = env.getLogManager().getLogger(FileMessage.class, null);
//    DataInputStream dis = new DataInputStream(reader);
//    dis.read
    boolean reading = true;
    int pos = 0;
    while(reading) {
      int num = reader.read(bytes, pos, bytes.length-pos);
      int sum = num+pos;
      if (logger.level <= Logger.INFO) logger.log(
          "num:"+num+" pos:"+pos+" bytes.length:"+bytes.length+" sum:"+sum);
      if (num == -1) {
        reading = false;
        if (logger.level <= Logger.SEVERE) logger.log( 
            "Error reading file "+file);
      }
      pos+=num;
      if (pos == bytes.length) {
        reading = false;
        if (logger.level <= Logger.SEVERE) logger.log( 
            "Done reading file "+file);
      }              
    }
    
  }
  
  public void writeFile() throws IOException {
    write(new File(fileName));
  }
  
  
  private void write(File f) throws IOException {
    FileOutputStream out = new FileOutputStream(f); 
    out.write(bytes);  
  }
  
  public void writeFile(File directory) throws IOException {
    File f = new File(directory, fileName);
    write(f);
  }
  
  public static void main(String[] args) throws IOException {
    FileMessage fm = new FileMessage(args[0]);
    fm.readFile(new Environment());
    fm.fileName = args[1];
    fm.writeFile();    
  }

}
