/*************************************************************************

"FreePastry" Peer-to-Peer Application Development Substrate 

Copyright 2002, Rice University. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

- Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

- Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

- Neither  the name  of Rice  University (RICE) nor  the names  of its
contributors may be  used to endorse or promote  products derived from
this software without specific prior written permission.

This software is provided by RICE and the contributors on an "as is"
basis, without any representations or warranties of any kind, express
or implied including, but not limited to, representations or
warranties of non-infringement, merchantability or fitness for a
particular purpose. In no event shall RICE or contributors be liable
for any direct, indirect, incidental, special, exemplary, or
consequential damages (including, but not limited to, procurement of
substitute goods or services; loss of use, data, or profits; or
business interruption) however caused and on any theory of liability,
whether in contract, strict liability, or tort (including negligence
or otherwise) arising in any way out of the use of this software, even
if advised of the possibility of such damage.

********************************************************************************/
package rice.visualization.render;

import rice.visualization.*;
import rice.visualization.data.*;

import java.awt.*;
import java.awt.image.*;
import java.util.*;
import javax.swing.*;

public class KeyValueListViewRenderer extends ViewRenderer {
  
  public static int DEFAULT_LINE_SPACING = 15;
  public static int DEFAULT_INDENT = 85;
  
  public KeyValueListViewRenderer(DataProvider visualization) {
    super(visualization);
  }
    
  public boolean canRender(DataView view) {
    return (view instanceof KeyValueListView);
  }
    
  public JPanel render(final DataView v) {
    JPanel panel = new JPanel() {
      public Dimension getPreferredSize() {
        return new Dimension(v.getWidth(), v.getHeight());
      }
      
      public void paintComponent(Graphics g) {
        KeyValueListView view = (KeyValueListView) visualization.getData().getView(v.getName());
        if (view == null)
          return;
        
        Enumeration e = view.getKeyNames();
        int i = 0;
        
        while (e.hasMoreElements()) {
          String key = (String) e.nextElement();
          paintLine(g, i, key, view.getValue(key));
          i++;
        }
      }
    };
    
    panel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createTitledBorder(v.getName()),
                                                       BorderFactory.createEmptyBorder(5,5,5,5)));
    return panel;
  }
    
  protected void paintLine(Graphics g, int number, String a, String b) {
    g.setColor(getDefaultFontColor());
    g.setFont(getDefaultFont());
    int fontHeight = g.getFontMetrics().getHeight();
    
    g.drawString(a + ":", getDefaultBorder(), getDefaultBorder() + fontHeight + number * getLineSpacing());
    g.drawString(b, getDefaultBorder() + getIndent(), getDefaultBorder() + fontHeight + number * getLineSpacing());
  }
  
  protected int getLineSpacing() {
    return DEFAULT_LINE_SPACING;
  }
  
  protected int getIndent() {
    return DEFAULT_INDENT;
  }
  
}
