package rice.visualization.server;

import rice.visualization.data.*;
import rice.pastry.*;
import rice.pastry.leafset.*;
import rice.pastry.routing.*;

import java.util.*;

public class PastryPanelCreator implements PanelCreator {
  
  public static int ROUTING_TABLE_NUM_ROWS_TO_SHOW = 7;
  
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
    
    TableView leafsetView = new TableView("Leafset", 1130, 70, leafsetCons);
    String[] row1 = new String[12];
    String[] row2 = new String[12];
    String[] row3 = new String[12];
    LeafSet leafset = node.getLeafSet();
    
    for (int i=0; i<12; i++) {
      row1[i] = (leafset.get(-12 + i) == null ? "" : leafset.get(-12 + i).getNodeId() + "");   
      row2[i] = (i == 0 ? leafset.get(0).getNodeId() + "" : "");
      row3[i] = (leafset.get(1 + i) == null ? "" : leafset.get(1 + i).getNodeId() + "");   
    }
    
    leafsetView.addRow(row1);    
    leafsetView.addRow(row2);    
    leafsetView.addRow(row3);    
    
    Constraints routeTableCons = new Constraints();
    routeTableCons.gridx = 0;
    routeTableCons.gridy = 1;
    routeTableCons.fill = Constraints.HORIZONTAL;
    
    TableView routeTableView = new TableView("Routing Table", 1130, 130, routeTableCons);
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
