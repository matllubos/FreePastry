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
  {"/message", new MessagePage()}
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