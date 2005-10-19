package rice.email.proxy.mail;

import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class MailAddress
{

    // this isn't anywhere close to meeting the specs
    static final String DOT_STRING = "(?:[^\\.]+(?:\\.[^\\.]+)*)";
    static final String LOCAL_PART = DOT_STRING;
    static final String DOMAIN     = DOT_STRING;
    static final String AT_DOMAIN  = "@" + DOMAIN;
    static final String ADL        = AT_DOMAIN + "(?:," + 
                                     AT_DOMAIN + ")*";
    static final String MAILBOX = "(" + LOCAL_PART + ")@(" + 
                                  DOMAIN + ")";
    static final String PATH = "(" + ADL + ":)?" + MAILBOX;
    static final Pattern pat = Pattern.compile(PATH);

    // members
    String host;
    String user;

    public MailAddress(String str)
                throws MalformedAddressException
    {
        Matcher mat = pat.matcher(str);
        if (!mat.matches())
            throw new MalformedAddressException("Malformed MailAddress");

        user = mat.group(2);
        host = mat.group(3);
    }

    public String getHost()
    {

        return host;
    }

    public String getUser()
    {

        return user;
    }
    
    public String toString() {
    	return getUser() + "@" + getHost();
    }
}