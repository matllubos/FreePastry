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
// $ANTLR 2.7.5 (20050128): "grammer.g" -> "ImapCommandParser.java"$

package rice.email.proxy.imap.parser.antlr;

import rice.email.proxy.imap.commands.*;
import rice.email.proxy.imap.commands.fetch.*;
import rice.email.proxy.imap.commands.search.*;
import rice.email.proxy.mailbox.*;
import rice.email.proxy.util.*;
import rice.environment.Environment;

import antlr.TokenStreamRecognitionException;
import antlr.CharStreamException;
import antlr.InputBuffer;

import java.io.Reader;
import java.util.*;


public interface ImapCommandParserTokenTypes {
  int EOF = 1;
  int NULL_TREE_LOOKAHEAD = 3;
  int CHECK = 4;
  int NOOP = 5;
  int LOGOUT = 6;
  int CAPABILITY = 7;
  int CREATE = 8;
  int DELETE = 9;
  int LIST = 10;
  int SUBSCRIBE = 11;
  int UNSUBSCRIBE = 12;
  int LSUB = 13;
  int EXAMINE = 14;
  int LOGIN = 15;
  int AUTHENTICATE = 16;
  int SELECT = 17;
  int FETCH = 18;
  int UID = 19;
  int APPEND = 20;
  int COPY = 21;
  int STORE = 22;
  int STATUS = 23;
  int EXPUNGE = 24;
  int CLOSE = 25;
  int BODY = 26;
  int RFC822 = 27;
  int PEEK = 28;
  int HEADER = 29;
  int FIELDS = 30;
  int NOT = 31;
  int TEXT = 32;
  int MIME = 33;
  int SIZE = 34;
  int ALL = 35;
  int FAST = 36;
  int FULL = 37;
  int BODYSTRUCTURE = 38;
  int ENVELOPE = 39;
  int FLAGS = 40;
  int INTERNALDATE = 41;
  int SEARCH = 42;
  int ANSWERED = 43;
  int BCC = 44;
  int BEFORE = 45;
  int CC = 46;
  int DELETED = 47;
  int DRAFT = 48;
  int FLAGGED = 49;
  int FROM = 50;
  int KEYWORD = 51;
  int LARGER = 52;
  int NEW = 53;
  int OLD = 54;
  int ON = 55;
  int OR = 56;
  int RECENT = 57;
  int RENAME = 58;
  int SEEN = 59;
  int SENTBEFORE = 60;
  int SENTON = 61;
  int SENTSINCE = 62;
  int SINCE = 63;
  int SMALLER = 64;
  int SUBJECT = 65;
  int TO = 66;
  int UNANSWERED = 67;
  int UNDELETED = 68;
  int UNDRAFT = 69;
  int UNFLAGGED = 70;
  int UNKEYWORD = 71;
  int UNSEEN = 72;
  int PERIOD = 73;
  int SPACE = 74;
  int LPAREN = 75;
  int RPAREN = 76;
  int LSANGLE = 77;
  int RSANGLE = 78;
  int ATOM = 79;
  int FLAG = 80;
  int LSBRACKET = 81;
  int RSBRACKET = 82;
  int NUMBER = 83;
  int QUOTE = 84;
  int QUOTED_CHAR = 85;
  int STRING = 86;
  int QUOTED = 87;
  int QUOTED_SPECIALS = 88;
  int ATOM_CHAR = 89;
  int CHAR = 90;
  int CTL = 91;
  int PLUS = 92;
  int LITERAL_START = 93;
  int UNKNOWN = 94;
}
