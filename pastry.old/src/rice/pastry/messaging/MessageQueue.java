//////////////////////////////////////////////////////////////////////////////
// Rice Open Source Pastry Implementation                  //               //
//                                                         //  R I C E      //
// Copyright (c)                                           //               //
// Romer Gil                   rgil@cs.rice.edu            //   UNIVERSITY  //
// Andrew Ladd                 aladd@cs.rice.edu           //               //
// Tsuen Wan Ngan              twngan@cs.rice.edu          ///////////////////
//                                                                          //
// This program is free software; you can redistribute it and/or            //
// modify it under the terms of the GNU General Public License              //
// as published by the Free Software Foundation; either version 2           //
// of the License, or (at your option) any later version.                   //
//                                                                          //
// This program is distributed in the hope that it will be useful,          //
// but WITHOUT ANY WARRANTY; without even the implied warranty of           //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the            //
// GNU General Public License for more details.                             //
//                                                                          //
// You should have received a copy of the GNU General Public License        //
// along with this program; if not, write to the Free Software              //
// Foundation, Inc., 59 Temple Place - Suite 330,                           //
// Boston, MA  02111-1307, USA.                                             //
//                                                                          //
// This license has been added in concordance with the developer rights     //
// for non-commercial and research distribution granted by Rice University  //
// software and patent policy 333-99.  This notice may not be removed.      //
//////////////////////////////////////////////////////////////////////////////

package rice.pastry.messaging;

/**
 * The Pastry message queue. 
 * <P>
 * A (FIFO) queue containing all the messages to be processed which dispatch the 
 * messages to {@link MessageDispatch MessageDispatch} one by one.
 *
 * @author Tsuen Wan Ngan
 */

public class MessageQueue extends Thread {

    private MessageDispatch _md;
    private MessageItem _first;
    private MessageItem _last;

    // whether the queue should continue dispatching
    private boolean _continue;

    // how long it sleeps when no message before trying to dispatch again
    private static final long _sometime = 3000;

    /**
     * Constructor.
     *
     * @param md the message dispatch to dispatch messages
     *
     * @param psm the Pastry security manager to decide whether to dispatch
     */

    public MessageQueue(MessageDispatch md) {
        _md = md;
    }

    /**
     * Test if this queue has no message.
     *
     * @return true if and only if the queue has no message
     *
     */

    boolean isEmpty() {
        return (_first == null);
    }

    /**
     * Returns the first message in the queue.
     *
     * @return the first message in the queue
     *
     */

    Message getFront() {
        return (_first == null)? null:_first.msg;
    }

    /**
     * To continuously dispatching messages in the queue.
     *
     */

    public void run() {
        _continue = true;

        while (_continue) {
            if (isEmpty()) {
                try {
                    sleep(_sometime);
                } catch (InterruptedException e) {}
            }
            else
                if (_psm.allowDispatch(getFront()))
                    _md.dispatchMessage(dequeue());
        }
    }

    /**
     * Request the message queue to stop dispatching.
     *
     */

    public void stopWhenReady() {
        _continue = false;
    }

    /**
     * Append a message at the end of the queue.
     *
     * @param msg the message to be appended to the queue
     *
     * @return true if and only if the enqueue is successful
     *
     */

    public synchronized boolean enqueue(Message msg) {
        if (msg == null)
             return false;

        if (_first == null) {
            _first = _last = new MessageItem(msg);
            return true;
        }

        _last.next = new MessageItem(msg);
        _last = _last.next;
        return true;
    }

    /**
     * Dequeue and return the first message in the queue.
     *
     * @return the next message in the queue, or null if no next message
     *
     */

    private synchronized Message dequeue() {
        Message next;

	if (_first == null)
            return null;

        next = _first.msg;
        _first = _first.next;

        if (_first == null)
            _last = null;

        return next;
    }

    /**
     * Inner class for linked-list.
     *
     */

    private class MessageItem {
        Message msg;
        MessageItem next;

        MessageItem(Message m) {
            msg = m;
        }
    }
}

