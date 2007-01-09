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
package rice.visualization.data;

import java.awt.*;
import java.io.*;

public class Color implements Serializable {

  public static final Color blue = new Color(0);
  public static final Color red = new Color(1);
  public static final Color yellow = new Color(2);
  public static final Color green = new Color(3);
  public static final Color lightGray = new Color(4);
  public static final Color orange = new Color(5);
  public static final Color pink = new Color(6);
  public static final Color darkGray = new Color(7);
  public static final Color white = new Color(8);
  public static final Color black = new Color(9);
  public static final Color magenta = new Color(10);
  public static final Color cyan = new Color(11);
  public static final Color gray = new Color(12);

  private int color;
  
  private Color(int color) {
    this.color = color;
  }
  
  public boolean equals(Object o) {
    return ((Color) o).color == color;
  }
  
  public java.awt.Color trans() {
    if (this.equals(blue))
      return java.awt.Color.blue;
    else if (this.equals(red))
      return java.awt.Color.red;
    else if (this.equals(yellow))
      return java.awt.Color.yellow;
    else if (this.equals(green))
      return java.awt.Color.green;
    else if (this.equals(lightGray))
      return java.awt.Color.lightGray;
    else if (this.equals(orange))
      return java.awt.Color.orange;
    else if (this.equals(pink))
      return java.awt.Color.pink;
    else if (this.equals(darkGray))
      return java.awt.Color.darkGray;
    else if (this.equals(white))
      return java.awt.Color.white;
    else if (this.equals(black))
      return java.awt.Color.black;
    else if (this.equals(magenta))
      return java.awt.Color.magenta;
    else if (this.equals(cyan))
      return java.awt.Color.cyan;
    else if (this.equals(gray))
      return java.awt.Color.gray;
    
    return java.awt.Color.red;
  }
}