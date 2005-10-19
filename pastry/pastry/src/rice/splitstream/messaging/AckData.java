package rice.splitstream.messaging;

import java.io.*;

/**
 *
 * @deprecated This version of SplitStream has been deprecated - please use the version
 *   located in the rice.p2p.splitstream package.
 */
public class AckData implements Serializable{
    private ControlFindParentResponseMessage m_cfprmsg = null;
    private ControlPropogatePathMessage m_cpgmsg = null;

    public AckData(ControlFindParentResponseMessage cfprmsg, ControlPropogatePathMessage cpgmsg){
	m_cfprmsg = cfprmsg;
	m_cpgmsg = cpgmsg;
    }
    
    public ControlFindParentResponseMessage getFindParentResponseMessage(){
	return m_cfprmsg;
    }

    public ControlPropogatePathMessage getPropogatePathMessage(){
	return m_cpgmsg;
    }

}
