package rice.email.proxy.util;

public class UIDFactory
{
    static final int POS_INT_MASK = ((1 << 31) - 1);

    public static int getUniqueId()
    {

        return (int) (System.currentTimeMillis() & POS_INT_MASK);
    }
}