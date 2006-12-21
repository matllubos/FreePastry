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
/*
 * Created on Mar 11, 2004
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package rice.visualization.client;

import java.io.Serializable;

/**
 * @author jeffh
 *
 * To change the template for this generated type comment go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
public class UpdateJarResponse implements Serializable {

  Exception e;
  public static final int OK=0;
  public static final int EXCEPTION=1;
  public static final int FILE_COPY_NOT_ALLOWED=2;
  public static final int NEW_EXEC_NOT_ALLOWED=3;
  
  int response = 0;

  
  public UpdateJarResponse() {
    this(OK);
  }

  public UpdateJarResponse(int reason) {
    this(null,reason);
  }

  public UpdateJarResponse(Exception e) {
    this(e,OK);
    if (e != null) {
      response = EXCEPTION;
    }
  }
  
  public UpdateJarResponse(Exception e, int response) {
    this.e = e;
    this.response = response;
  }
  
  public boolean success() {
    return e == null;
  }

  public Exception getException() {
    return e;
  }
  
  public int getResponse() {
    return response;
  }
}
