package rice.visualization.render;

import java.awt.*;
import java.awt.image.*;
import java.util.*;
import javax.swing.*;

import rice.visualization.data.*;

public class ViewRendererFactory {
  
  protected Vector renderers;
  
  public ViewRendererFactory() {
    this.renderers = new Vector();
  }
  
  public static ViewRendererFactory buildFactory(DataProvider visualization) {
    ViewRendererFactory factory = new ViewRendererFactory();
    factory.addRenderer(new TableViewRenderer(visualization));
    factory.addRenderer(new KeyValueListViewRenderer(visualization));
    factory.addRenderer(new LineGraphViewRenderer(visualization));
    factory.addRenderer(new PieChartViewRenderer(visualization));
    
    return factory;
  }
  
  public void addRenderer(ViewRenderer renderer) {
    renderers.add(renderer);
  }
  
  public JPanel render(DataView view) {
    ViewRenderer renderer = getRenderer(view);
    
    if (renderer != null)
      return renderer.render(view);
    else 
      throw new IllegalArgumentException("No renderer could be found for " + view.getClass().getName());
  }
  
  protected ViewRenderer getRenderer(DataView view) {
    for (int i=0; i<renderers.size(); i++)
      if (((ViewRenderer) renderers.elementAt(i)).canRender(view))
        return (ViewRenderer) renderers.elementAt(i);
    
    return null;
  }
}