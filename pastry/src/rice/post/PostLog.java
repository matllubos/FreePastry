/*******************************************************************************

"FreePastry" Peer-to-Peer Application Development Substrate

Copyright 2002-2007, Rice University. Copyright 2006-2007, Max Planck Institute 
for Software Systems.  All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

- Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

- Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

- Neither the name of Rice  University (RICE), Max Planck Institute for Software 
Systems (MPI-SWS) nor the names of its contributors may be used to endorse or 
promote products derived from this software without specific prior written 
permission.

This software is provided by RICE, MPI-SWS and the contributors on an "as is" 
basis, without any representations or warranties of any kind, express or implied 
including, but not limited to, representations or warranties of 
non-infringement, merchantability or fitness for a particular purpose. In no 
event shall RICE, MPI-SWS or contributors be liable for any direct, indirect, 
incidental, special, exemplary, or consequential damages (including, but not 
limited to, procurement of substitute goods or services; loss of use, data, or 
profits; or business interruption) however caused and on any theory of 
liability, whether in contract, strict liability, or tort (including negligence
or otherwise) arising in any way out of the use of this software, even if 
advised of the possibility of such damage.

*******************************************************************************/ 
package rice.post;

import java.io.*;
import java.security.*;
import java.security.cert.*;

import rice.*;
import rice.post.log.*;
import rice.post.storage.*;
import rice.post.security.*;

/**
 * This class represents the Log which sits at the address of the user's
 * PostUserAddress and points to the logs of other applications.  This
 * log itself has no entries.
 * 
 * This object overrides Log in order to contain the user's public key
 * and certificate for other users in the system to read.
 * 
 * @version $Id$
 */
@SuppressWarnings("unchecked")
public class PostLog extends Log {

  /**
   * Serialver for backwards compatibility
   */
  public static final long serialVersionUID = 5516854362333868152L;

  /**
   * The user of this log.
   */
  private PostEntityAddress user;

  /**
   * The public key of the user.
   */
  private PublicKey key;

  /**
   * The certificate of the user.
   */
  private PostCertificate certificate;
  
  /**
   * Any extra data, or specifically, the head of one's aggregation log
   */
  protected Serializable aggregate;
  
  /**
   * Constructor for PostLog.  Package protected: only Post can create
   * a PostLog.
   *
   * @param user The user whom this PostLog is for
   * @param key The user's public key.
   * @param cert This user's certification
   * @param post The local Post service
   * @param command The command to call once done
   */
  PostLog(PostEntityAddress user, PublicKey key, PostCertificate cert, Post post, Continuation command) {
    super("User " + user.toString() + "'s log", user.getAddress(), post);
 
    this.user = user;
    this.key = key;
    this.certificate = cert;
    
    sync(command);
  }
  
  /**
   * Constructor for PostLog, which effectively renames a previous user to a new username.  
   * All of the user's metadata is taken from the the provided PostLog.
   *
   * @param user The user whom this PostLog is for
   * @param key The user's public key.
   * @param cert This user's certification
   * @param post The local Post service
   * @param previous The previous PostLog of the user
   * @param command The command to call once done
   */
  PostLog(PostEntityAddress user, PublicKey key, PostCertificate cert, Post post, PostLog previous, Continuation command) {
    super("User " + user.toString() + "'s log", user.getAddress(), post);
    
    this.user = user;
    this.key = key;
    this.certificate = cert;
    
    // now, we copy the necessary metadata over
    this.children = previous.children;
    this.topEntryReference = previous.topEntryReference;
    this.topEntryReferences = previous.topEntryReferences;
    
    sync(command);
  }
  
  /**
   * @return THe head of the aggregation log
   */
  public Serializable getAggregateHead() {
    return aggregate;
  }
  
  /**
   * Updates the head of the aggregation log
   *
   * @param aggregate The current head of the aggregation log
   */
  public void setAggregateHead(Serializable aggregate) {
    this.aggregate = aggregate;
  }
    
  /**
   * @return The user who owns this PostLog.
   */
  public PostEntityAddress getEntityAddress() {
    return user;
  }
    
  /**
   * @return The public key of the user who owns this PostLog.
   */
  public PublicKey getPublicKey() {
    return key;
  }

  /**
   * @return The certificate for the user who owns this PostLog.
   */
  public PostCertificate getCertificate() {
    return certificate;
  }

  public String toString() {
    return "PostLog[" + user + "]";
  }
}

