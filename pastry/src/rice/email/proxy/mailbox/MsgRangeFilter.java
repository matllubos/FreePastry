package rice.email.proxy.mailbox;

import rice.email.proxy.mail.StoredMessage;

import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class MsgRangeFilter
    extends MsgFilter
{
    static final Pattern TWO_PART = Pattern.compile(
                                            "(\\d+|\\*):(\\d+|\\*)");
    int _top;
    int _bottom;
    boolean _isUID;

    public MsgRangeFilter(String rng, boolean uid)
    {
        if (rng.indexOf(':') == -1)
        {
            int value             = Integer.parseInt(rng);
            _top                  = value;
            _bottom               = value;
        }
        else
        {
            Matcher mat = TWO_PART.matcher(rng);
            mat.matches();

            if (mat.groupCount() != 2)
              throw new RuntimeException("GroupCount was not 2!");
            
            String bot = mat.group(1);
            String top = mat.group(2);
            if (bot.equals("*"))
                _bottom = 0;
            else
                _bottom = Integer.parseInt(bot);

            if (top.equals("*"))
                _top = Integer.MAX_VALUE;
            else
                _top = Integer.parseInt(top);

            if ((_top < 0) || (_bottom < 0)) {
              throw new MailboxException("Invalid range given");
            }

        }

        _isUID = uid;
    }

    public boolean includes(StoredMessage msg)
    {
        int msgValue = (_isUID ? msg.getUID() : msg.getSequenceNumber());

        return msgValue >= _bottom && msgValue <= _top;
    }
}