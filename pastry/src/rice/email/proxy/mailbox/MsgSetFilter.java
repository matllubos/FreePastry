package rice.email.proxy.mailbox;

import rice.email.proxy.mail.StoredMessage;

import java.util.regex.Pattern;


public class MsgSetFilter
    extends MsgFilter
{
    MsgRangeFilter[] filters;

    public MsgSetFilter(String set, boolean uid)
    {
        String[] ranges = set.split(",");
        filters = new MsgRangeFilter[ranges.length];

        for (int i = 0; i < ranges.length; i++)
        {
            String range = ranges[i];
            filters[i] = new MsgRangeFilter(range, uid);
        }
    }

    public boolean includes(StoredMessage msg)
    {
        for (int i = 0; i < filters.length; i++)
        {
            MsgRangeFilter filter = filters[i];
            if (filter.includes(msg))

                return true;
        }

        return false;
    }
}