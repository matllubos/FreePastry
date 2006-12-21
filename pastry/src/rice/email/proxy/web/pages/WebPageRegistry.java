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
package rice.email.proxy.web.pages;

import java.io.IOException;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

public class WebPageRegistry {
  
  private static Map pages = new HashMap();
  private static Object[][] PAGES = new Object[][]
  {
  {"/", new LoginPage()}, {"/folder", new FolderPage()},
  {"/main", new MainPage()}, {"/hierarchy", new HierarchyPage()},
  {"/message", new MessagePage()}, {"/top", new TopPage()}
  };
  
  public void load() {
    for (int i = 0; i < PAGES.length; i++) {
      String name = PAGES[i][0].toString();
      
      if (pages.containsKey(name))
        continue;
      
      WebPage page = (WebPage) PAGES[i][1];
      registerPage(name, page);
    }
  }
  
  private void registerPage(String name, WebPage page)  {
    pages.put(name, page);
  }
  
  public WebPage getPage(String name) {
    return (WebPage) pages.get(name);
  }
}