/*
 * Created on Mar 11, 2004
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package rice.visualization.client;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.Properties;

/**
 * @author jeffh
 *
 * To change the template for this generated type comment go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
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

  public void readFile() throws IOException {
    fileName = file.getName();
    FileInputStream reader = new FileInputStream(file);
    bytes = new byte[(int)file.length()];
    
//    DataInputStream dis = new DataInputStream(reader);
//    dis.read
    boolean reading = true;
    int pos = 0;
    while(reading) {
      int num = reader.read(bytes, pos, bytes.length-pos);
      int sum = num+pos;
      System.out.println("num:"+num+" pos:"+pos+" bytes.length:"+bytes.length+" sum:"+sum);
      if (num == -1) {
        reading = false;
        System.out.println("Error reading file "+file);
      }
      pos+=num;
      if (pos == bytes.length) {
        reading = false;
        System.out.println("Done reading file "+file);
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
    fm.readFile();
    fm.fileName = args[1];
    fm.writeFile();    
  }

}
