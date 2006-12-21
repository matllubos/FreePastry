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
package rice.email.proxy.mailbox;

import java.util.List;

public interface FlagList {
  
  /**
   * static names of all the server-provided flags
   */
  public static final String DELETED_FLAG = "\\Deleted";
  public static final String ANSWERED_FLAG = "\\Answered";
  public static final String SEEN_FLAG = "\\Seen";
  public static final String DRAFT_FLAG = "\\Draft";
  public static final String FLAGGED_FLAG = "\\Flagged";
  public static final String RECENT_FLAG = "\\Recent";
  
  /**
   * Methods which allow the modification of flags
   */
  void setFlag(String flag, boolean value);
  void setDeleted(boolean value);
  void setSeen(boolean value);
  void setDraft(boolean value);
  void setFlagged(boolean value);
  void setAnswered(boolean value);
  
  /**
   * Methods which allow the querying of flags
   */
  boolean isSet(String flag);
  boolean isDeleted();
  boolean isSeen();
  boolean isDraft();
  boolean isAnswered();
  boolean isFlagged();
  
  /**
   * Utility method for conversion to a string
   */
  String toFlagString();
  List getFlags();
  
  /**
   * Methods which support the session flags, as well as
   * the \Recent flag
   */
  boolean isRecent();
  void setRecent(boolean value);
  
  /**
   * Causes any changes in this FlagList's state to be written to
   * the associated Mailbox. This allows colapsing several changes
   * into one disk write, one SQL command, etc.
   */
  void commit() throws MailboxException;
}

