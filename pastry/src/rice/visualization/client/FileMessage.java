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
