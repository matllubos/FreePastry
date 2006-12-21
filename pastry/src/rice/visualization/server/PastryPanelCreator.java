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
