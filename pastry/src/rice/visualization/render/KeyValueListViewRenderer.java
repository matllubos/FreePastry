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
  
  public KeyValueListViewRenderer(Visualization visualization) {
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
