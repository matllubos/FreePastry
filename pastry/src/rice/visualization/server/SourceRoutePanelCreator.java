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
package rice.visualization.server;

import rice.visualization.data.*;
import rice.pastry.*;
import rice.pastry.leafset.*;
import rice.pastry.routing.*;
import rice.pastry.socket.*;
import rice.pastry.transport.TLPastryNode;

import java.util.*;

public class SourceRoutePanelCreator implements PanelCreator {
  
  public DataPanel createPanel(Object[] objects) {
    for (int i=0; i<objects.length; i++) 
      if (objects[i] instanceof TLPastryNode)
        return createPanel((TLPastryNode) objects[i]);
    
    return null;
  }
  
  protected DataPanel createPanel(TLPastryNode node) {
    DataPanel pastryPanel = new DataPanel("Routes");
    // NOTE: this is broken with the new transport layer
    HashMap routes = null; //node.getSocketSourceRouteManager().getBest();

    Constraints cons1 = new Constraints();
    cons1.gridx = 1;
    cons1.gridy = 0;
    
    TableView endpointView = new TableView("Active Indirect Routes", 790, 200, cons1);
    endpointView.setSizes(new int[] {250,250,250});

    Iterator keys = routes.keySet().iterator();
    
    int indirect = 0;
    
    while (keys.hasNext()) {
      String[] row = new String[] {"", "", ""};
    
//      while (keys.hasNext()) {
//        SourceRoute route = (SourceRoute) routes.get(keys.next());
//        if ((route != null) && (! route.isDirect())) {
//          indirect++;
//
//          if (row[0].equals("")) row[0] = route + "";
//          else if (row[1].equals("")) row[1] = route + "";
//          else { 
//            row[2] = route.toString();
//            break;
//          }
//        }
//      }
      
      endpointView.addRow(row);
    }
    
    Constraints cons2 = new Constraints();
    cons2.gridx = 0;
    cons2.gridy = 0;
    
    KeyValueListView overView = new KeyValueListView("Route Overview", 310, 200, cons2);
    overView.add("Total", "" + routes.size());
    overView.add("Indirect", "" + indirect);
//    overView.add("Intermediate", node.getSocketSourceRouteManager().getManager().getNumSourceRoutes() + " / " + node.getSocketSourceRouteManager().getManager().MAX_OPEN_SOURCE_ROUTES);
//    overView.add("Sockets", node.getSocketSourceRouteManager().getManager().getNumSockets() + " / " + node.getSocketSourceRouteManager().getManager().MAX_OPEN_SOCKETS);
    
    pastryPanel.addDataView(overView);
    pastryPanel.addDataView(endpointView);
    
    return pastryPanel;
  }
  
}
