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

package rice.post.testing;

import java.util.*;

import rice.*;
import rice.Continuation.*;

import rice.pastry.standard.*;

import rice.post.*;
import rice.post.log.*;
import rice.post.messaging.*;

/**
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */
public class LogTest extends PostTest {

  protected static String LOG_TEST_LOG_NAME = "LogTestLog";

  protected static int NUM_ENTRIES = 100;
  
  protected LogTestPostClient[] clients = new LogTestPostClient[NUM_NODES];

  protected Random random;
  
  /**
   * Method which should process the given newly-created node
   *
   * @param num The number o the node
   * @param node The newly created node
   */
  protected void processNode(int num) {
    LogTestPostClient client = new LogTestPostClient(posts[num]);
    clients[num] = client;

    posts[num].addClient(client);

    random = new Random();
  }

  /**
   * Method which should run the test - this is called once all of the
   * nodes have been created and are ready.
   */
  protected void runTest() {
    System.out.println("RUNNING TEST");
    LogTestPostClient client = clients[random.nextInt(NUM_NODES)];

    client.runTest();
  }

  /**
   * Main method which starts this test
   *
   */
  public static void main(String[] args) {
    LogTest test = new LogTest();

    test.start();
  }

  protected  class LogTestPostClient extends PostClient {

    protected LogTestLog log;

    protected Post post;
    
    public LogTestPostClient(Post post) {
      this.post = post;
     
      post.addClient(this);
      getRootFolder(new ListenerContinuation("Fetch root folder"));
    }
    
    /**
     * This method is how the Post object informs the clients
     * that there is an incoming notification.
     *
     * @param nm The incoming notification.
     */
    public void notificationReceived(NotificationMessage nm) {
    }

    /**
     * Inserts a bunch of stuff into the log
     *
     */
    public void runTest() {
      StandardContinuation continuation = new StandardContinuation(new ListenerContinuation("Run Test")) {
        private int i = 0;

        public void receiveResult(Object o) {
          if (i < NUM_ENTRIES) {
            i++;

            log.addLogEntry(new LogTestLogEntry(), this);
            simulate();
          } else {
            System.out.println("DONE");
          }
        }
      };

      continuation.receiveResult(null);
    }      

    /**
    * Returns the Log for ePost's root folder.
     *
     * @param command is the object notified of the result of the folder
     *      retrieval.
     */
    public void getRootFolder(final Continuation command) {
      if (log != null) {
        command.receiveResult(log);
        return;
      }

      post.getPostLog(new StandardContinuation(command) {
        public void receiveResult(Object o) {
          final PostLog mainLog = (PostLog) o;

          if (mainLog == null) {
            command.receiveException(new Exception("PostLog was null - aborting."));
          } else {
            mainLog.getChildLog(getAddress(), new StandardContinuation(parent) {
              public void receiveResult(Object o) {
                log = (LogTestLog) o;

                if (log == null) {
                  Log newLog = new LogTestLog(post);
                  mainLog.addChildLog(newLog, new StandardContinuation(parent) {
                    public void receiveResult(Object o) {
                      log = (LogTestLog) o;

                      command.receiveResult(log);
                    }
                  });
                } else {
                  command.receiveResult(log);
                }
              }
            });
          }
        }
      });
    }
  }

  protected static class LogTestLog extends Log {

    public LogTestLog(Post post) {
      super(LOG_TEST_LOG_NAME, (new RandomNodeIdFactory()).generateNodeId(), post);
    }
    
  }

  protected static class LogTestLogEntry extends LogEntry {

  }
}
