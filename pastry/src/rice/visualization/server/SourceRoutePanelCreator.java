package rice.visualization.server;

import rice.visualization.data.*;
import rice.pastry.*;
import rice.pastry.leafset.*;
import rice.pastry.routing.*;
import rice.pastry.socket.*;

import java.util.*;

public class SourceRoutePanelCreator implements PanelCreator {
  
  public DataPanel createPanel(Object[] objects) {
    for (int i=0; i<objects.length; i++) 
      if (objects[i] instanceof SocketPastryNode)
        return createPanel((SocketPastryNode) objects[i]);
    
    return null;
  }
  
  protected DataPanel createPanel(SocketPastryNode node) {
    DataPanel pastryPanel = new DataPanel("Routes");
    HashMap routes = node.getSocketSourceRouteManager().getBest();

    Constraints cons1 = new Constraints();
    cons1.gridx = 1;
    cons1.gridy = 0;
    
    TableView endpointView = new TableView("Active Indirect Routes", 790, 200, cons1);
    endpointView.setSizes(new int[] {250,250,250});

    Iterator keys = routes.keySet().iterator();
    
    int indirect = 0;
    
    while (keys.hasNext()) {
      String[] row = new String[] {"", "", ""};
    
      while (keys.hasNext()) {
        SourceRoute route = (SourceRoute) routes.get(keys.next());
        if ((route != null) && (! route.isDirect())) {
          indirect++;

          if (row[0].equals("")) row[0] = route + "";
          else if (row[1].equals("")) row[1] = route + "";
          else { 
            row[2] = route.toString();
            break;
          }
        }
      }
      
      endpointView.addRow(row);
    }
    
    Constraints cons2 = new Constraints();
    cons2.gridx = 0;
    cons2.gridy = 0;
    
    KeyValueListView overView = new KeyValueListView("Route Overview", 310, 200, cons2);
    overView.add("Total", "" + routes.size());
    overView.add("Indirect", "" + indirect);
    overView.add("Intermediate", node.getSocketSourceRouteManager().getManager().getNumSourceRoutes() + " / " + SocketCollectionManager.MAX_OPEN_SOURCE_ROUTES);
    overView.add("Sockets", node.getSocketSourceRouteManager().getManager().getNumSockets() + " / " + SocketCollectionManager.MAX_OPEN_SOCKETS);
    
    pastryPanel.addDataView(overView);
    pastryPanel.addDataView(endpointView);
    
    return pastryPanel;
  }
  
}
