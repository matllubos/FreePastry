package rice.visualization.server;

import rice.visualization.data.*;
import rice.pastry.*;
import rice.pastry.leafset.*;
import rice.pastry.routing.*;

import java.util.*;

public class PastryPanelCreator implements PanelCreator {
  
  public static int ROUTING_TABLE_NUM_ROWS_TO_SHOW = 9;
  
  public DataPanel createPanel(Object[] objects) {
    for (int i=0; i<objects.length; i++) 
      if (objects[i] instanceof PastryNode)
        return createPanel((PastryNode) objects[i]);
    
    return null;
  }
  
  protected DataPanel createPanel(PastryNode node) {
    DataPanel pastryPanel = new DataPanel("Pastry");
    
    Constraints leafsetCons = new Constraints();
    leafsetCons.gridx = 0;
    leafsetCons.gridy = 0;
    leafsetCons.fill = Constraints.HORIZONTAL;
    
    TableView leafsetView = new TableView("Leafset", 1130, 40, leafsetCons);
    Vector strings = new Vector();
    LeafSet leafset = node.getLeafSet();
    
    for (int i=-leafset.ccwSize(); i<=leafset.cwSize(); i++) 
      strings.add(leafset.get(i).getNodeId() + "");
    
    
    leafsetView.addRow((String[]) strings.toArray(new String[0]));
    
    Constraints routeTableCons = new Constraints();
    routeTableCons.gridx = 0;
    routeTableCons.gridy = 1;
    routeTableCons.fill = Constraints.HORIZONTAL;
    
    TableView routeTableView = new TableView("Routing Table", 1130, 160, routeTableCons);
    RoutingTable routingTable = node.getRoutingTable();
      
    for (int i=routingTable.numRows()-1; i>=routingTable.numRows()-ROUTING_TABLE_NUM_ROWS_TO_SHOW; i--) {
      RouteSet[] row = routingTable.getRow(i);
      Vector rowV = new Vector();
    
      for (int j=0; j<row.length; j++) 
        if ((row[j] == null) || (row[j].closestNode() == null))
          rowV.add("");
        else
          rowV.add(row[j].closestNode().getNodeId() + "");
            
      routeTableView.addRow((String[]) rowV.toArray(new String[0]));
    }

    
    pastryPanel.addDataView(leafsetView);
    pastryPanel.addDataView(routeTableView);
    
    return pastryPanel;
  }

}
