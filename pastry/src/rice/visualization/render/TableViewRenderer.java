package rice.visualization.render;

import java.awt.*;
import java.awt.image.*;
import java.util.*;
import javax.swing.*;

import rice.visualization.*;
import rice.visualization.data.*;

public class TableViewRenderer extends ViewRenderer {
  
  public static int DEFAULT_CELL_WIDTH = 69;
  public static int DEFAULT_CELL_HEIGHT = 15;
  public static int DEFAULT_CELL_PADDING = 3;
  
  public static java.awt.Color DEFAULT_BACKGROUND_COLOR = java.awt.Color.white;
  public static java.awt.Color DEFAULT_FOREGROUND_COLOR = java.awt.Color.gray;
  
  public TableViewRenderer(Visualization visualization) {
    super(visualization);
  }  
  
  public boolean canRender(DataView view) {
    return (view instanceof TableView);
  }
  
  public JPanel render(final DataView v) {
    JPanel panel = new JPanel() {
      public Dimension getPreferredSize() {
        return new Dimension(v.getWidth(), v.getHeight());
      }
      
      public void paintComponent(Graphics g) {
        TableView view = (TableView) visualization.getData().getView(v.getName());
        int[] widths = getSizes(view);
        
        for (int i=0; i<view.getRowCount(); i++) {
          String[] row = view.getRow(i);
          paintRow(g, widths, i, row.length);
          
          for (int j=0; j<row.length; j++) {
            paintCell(g, widths, j, i, row[j]);
          }
        }
      }
    }; 
    
    panel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createTitledBorder(v.getName()),
                                                       BorderFactory.createEmptyBorder(5,5,5,5)));
    return panel; 
  }
  
  protected void paintRow(Graphics g, int[] widths, int row, int num) {
    g.setColor(getDefaultBackgroundColor());
    g.fillRect(getDefaultBorder(), getDefaultBorder() + row * getCellHeight(),
               getWidth(widths, num), getCellHeight());
    
    g.setColor(getDefaultForegroundColor());
    g.drawLine(getDefaultBorder(), getDefaultBorder() + row * getCellHeight(),
               getDefaultBorder() + getWidth(widths, num), getDefaultBorder() + row * getCellHeight());
    g.drawLine(getDefaultBorder(), getDefaultBorder() + (row + 1) * getCellHeight(),
               getDefaultBorder() + getWidth(widths, num), getDefaultBorder() + (row + 1) * getCellHeight());
    g.drawLine(getDefaultBorder(), getDefaultBorder() + row * getCellHeight(),
               getDefaultBorder(), getDefaultBorder() + (row + 1) * getCellHeight());
    
    for (int i=1; i<num+1; i++) 
      g.drawLine(getDefaultBorder() + getWidth(widths, i), getDefaultBorder() + row * getCellHeight(),
                 getDefaultBorder() + getWidth(widths, i), getDefaultBorder() + (row + 1) * getCellHeight());
  }
  
  protected void paintCell(Graphics g, int[] widths, int x, int y, String s) {
    g.setColor(getDefaultFontColor());
    g.setFont(getDefaultSmallFont());
    int fontHeight = g.getFontMetrics().getHeight();
    
    g.drawString(s, getDefaultBorder() + getWidth(widths, x) + getCellPadding(), getDefaultBorder() + fontHeight + y * getCellHeight());
  }
  
  protected int getWidth(int[] widths, int column) {
    int total = 0;
    
    for (int i=0; i<column; i++)
      total += widths[i];
  
    return total;
  }
  
  protected int[] getSizes(TableView view) {
    if (view.getSizes() != null)
      return view.getSizes();
    
    if (view.getRowCount() > 0)
      return fillArray(view.getRow(0).length, getCellWidth());
    
    return fillArray(1, getCellWidth());
  }
  
  protected int[] fillArray(int num, int fill) {
    int[] result = new int[num];
    
    for (int i=0; i<num; i++)
      result[i] = fill;
    
    return result;
  }
  
  protected java.awt.Color getDefaultBackgroundColor() {
    return DEFAULT_BACKGROUND_COLOR;
  }
  
  protected java.awt.Color getDefaultForegroundColor() {
    return DEFAULT_FOREGROUND_COLOR;
  }
  
  protected int getCellPadding() {
    return DEFAULT_CELL_PADDING;
  }
  
  protected int getCellWidth() {
    return DEFAULT_CELL_WIDTH;
  }
  
  protected int getCellHeight() {
    return DEFAULT_CELL_HEIGHT;
  }
  
}
