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
package rice.testing.routeconsistent.viewer;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

/*
 * Created on Apr 5, 2004
 */

/**
 * @author Jeff Hoye
 */
public class JeffReader implements Runnable {
  boolean reading = false;
  int readRate = 1000;
  JeffStreamTokenizer st;
  SquareConsumer consumer;
  
  public JeffReader(SquareConsumer sc) {
    consumer = sc;
  }

  FileInputStream is = null;
  BufferedReader r = null;
  
  public void read(File f) throws IOException {
    is = new FileInputStream(f);
    r = new BufferedReader(new InputStreamReader(is));
    st = new JeffStreamTokenizer(r);
    new Thread(this,"Reader Thread").start();    
  }
  
  Object pauseLock = new Object();
  boolean paused = false;
  boolean firstTime = true;
  
  public void run() {
    System.out.println("start loading");
    try {
      double nextTime = 0;
      double timeStep = 1;
      normalMode();
            
      st.eolIsSignificant(true);
      int type;
      reading = true;
      Square event = null;
      while (reading) {    
        synchronized(pauseLock) {
          while(paused) {
            try {
              pauseLock.wait();
            } catch (InterruptedException ie) {
              
            }
          }
        }
            
      st.nextToken();
      switch (st.ttype) {
        case JeffStreamTokenizer.TT_WORD:
          event = new Square();
          event.fileName = st.sval;
          st.nextToken();
          event.lineNum = (int)st.nval;
          st.nextToken();
          event.type = (int)st.nval;
          st.nextToken();
          event.time = (long)st.nval;
          st.nextToken();
          event.left = (int)st.nval;
          // this is the 0
          st.nextToken();
          event.nodeName = st.sval;
          // this is the rest of the name
          st.nextToken();
          event.nodeName+= st.sval;
          
          st.nextToken();
          event.right = (int)st.nval;
          break;          

        case JeffStreamTokenizer.TT_EOL:
          handleEvent(event);
          event = null;
          break;

        case JeffStreamTokenizer.TT_EOF:
          handleEvent(event);
          event = null;
          if (firstTime) {
            firstTime = false;
            consumer.done();
            System.out.println("done loading");
          }
          try {
            Thread.sleep(readRate);
          } catch (InterruptedException ie) {
            return;
          }
          break;                    
        
        }
      }    
    } catch (IOException ioe) {
      ioe.printStackTrace();
      //consumer.exception(ioe);
    }
    try {
      is.close();
      r.close();
    } catch (IOException ioe) {
      
    }
  }



  public static final int SIMBEGIN = 1;
  public static final int SIMEND = 2;
  public static final int CREATE = 3;
  public static final int COORDINATE = 4;
  public static final int MESSAGE = 5;
  public static final int STEPUP = 6;
  public static final int STEPDOWN = 7;
  public static final int RT = 8;
  public static final int VECTOR = 9;
  public static final int TIME = 10;
  public static final int PACKET = 11;
  public static final int MARKER = 12;

  public static final String[] eventNames = {"UNKNOWN","SIMBEGIN","SIMEND","CREATE","COORDINATE","MESSAGE","STEPUP","STEPDOWN","RTENTRY","VECTOR","TIME","PACKET","MARKER"};  

  public void handleEvent(Square e) {
    if (e == null)  {
      return;
    } 
    consumer.addSquare(e);
//    System.out.println(e);
  }

  public void addressMode() {
    st.resetSyntax();
    st.wordChars('0','9');
    st.wordChars('.','.');
    st.wordChars('-','-');    
    st.whitespaceChars('\u0000','\u0020');
  }
  
  public void normalMode() {
    st.resetSyntax();
    
    //# All byte values 'A' through 'Z', 'a' through 'z', and '\u00A0' through '\u00FF' are considered to be alphabetic.
    st.wordChars('A','Z');
    st.wordChars('a','z');
    st.wordChars('\u00A0','\u00FF');

//    String s;
//    s.split("\\.");
    
    //# All byte values '\u0000' through '\u0020' are considered to be white space.
    st.whitespaceChars('\u0000','\u0020');

    //# '/' is a comment character.
    st.commentChar('#');

    //# Single quote '\'' and double quote '"' are string quote characters.
    
    //# Numbers are parsed.
    st.parseNumbers();
    
    
    //# Ends of lines are treated as white space, not as separate tokens.
    st.eolIsSignificant(true);
    
    //# C-style and C++-style comments are not recognized.
  }
  
  public void commentMode() {
    st.resetSyntax();
    
    //# All byte values 'A' through 'Z', 'a' through 'z', and '\u00A0' through '\u00FF' are considered to be alphabetic.
    st.wordChars('A','Z');    
    st.wordChars('a','z');
    st.wordChars('\u00A0','\u00FF');

    st.wordChars('\u0000','\u0020');

    //# '/' is a comment character.
    st.wordChars('#','#');
    st.wordChars('0','9');
    st.wordChars('.','.');
    st.wordChars('-','-');

    //# Numbers are parsed.
//    st.parseNumbers();
    
    //# Ends of lines are treated as white space, not as separate tokens.
    st.eolIsSignificant(true);
  }

  public static void main(String[] args) throws Exception {
    JeffReader r = new JeffReader(null);
    r.read(new File("c:/pastry/vizdata"));
  }


  public void unpause() {
    synchronized(pauseLock) {
      paused = false;
      pauseLock.notifyAll();    
    }
  }

  public void pause() {
    synchronized(pauseLock) {
      paused = true;
    }
  }

  public void stop() {
   reading = false;
   unpause();
  }
  
  public boolean isPaused() {
    return paused; 
  }
}
