package rice.email.proxy.mailbox;

import rice.email.proxy.mail.StoredMessage;


public abstract class MsgFilter {
  
  public abstract boolean includes(StoredMessage msg);
  
  public static final MsgFilter NOT(final MsgFilter filter) {
    return new MsgFilter() {
      public boolean includes(StoredMessage msg) {
        return !filter.includes(msg);
      }
    };
  }
  
  public static final MsgFilter ALL = new MsgFilter() {
    public boolean includes(StoredMessage msg) {
      return true;
    }
  };
  
  public static final MsgFilter RECENT = new MsgFilter() {
    public boolean includes(StoredMessage msg) {
      return msg.getFlagList().isRecent();
    }
  };
  
  public static final MsgFilter DELETED = new MsgFilter() {
    public boolean includes(StoredMessage msg) {
      return msg.getFlagList().isDeleted();
    }
  };
  
  public static final MsgFilter SEEN = new MsgFilter() {
    public boolean includes(StoredMessage msg) {
      return msg.getFlagList().isSeen();
    }
  };
}