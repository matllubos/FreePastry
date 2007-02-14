/* packet-freepastry.h
 * Routines for FREEPASTRY protocol dissection
 * Copyright 2007, David Dugoujon <dav176fr@yahoo.fr>
 *
 *
 * Wireshark - Network traffic analyzer
 * By Gerald Combs <gerald@wireshark.org>
 * Copyright 1998 Gerald Combs
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 */


#ifndef __PACKET_FREEPASTRY_H__
#define __PACKET_FREEPASTRY_H__

/* TCP destination port dedicated to the FreePastry protocol */
#define TCP_PORT_FREEPASTRY   5009
/* UDP destination port dedicated to the FreePastry protocol */
#define UDP_PORT_FREEPASTRY   5009

/*Header flags*/
#define PASTRY_MAGIC_NUMBER    0x2740753A
#define HEADER_SOURCE_ROUTE    0x19531300
#define HEADER_DIRECT          0x061B4974

/* Valid FreePastry address values and message types (TCP)*/
/*Core Messages*/
#define DIRECT_ACCESS    0x00000000 
#define SOURCEROUTE_MSG           1
#define LEAFSET_REQUEST_MSG       4 
#define LEAFSET_RESPONSE_MSG      5
#define NODE_ID_REQUEST_MSG       6
#define NODE_ID_RESPONSE_MSG      7
#define ROUTE_ROW_REQUEST_MSG    10
#define ROUTE_ROW_RESPONSE_MSG   11
#define SOURCEROUTE_REQUEST_MSG  12
#define SOURCEROUTE_RESPONSE_MSG 13

/*Route Messages are used by the Common API*/
#define ROUTER	        0xACBDFE17
#define ROUTE -23525


#define JOIN_PROTOCOL	  0xe80c17e8 
#define JOIN_REQUEST        1
#define CONSISTENT_JOIN_MSG 2

#define LEAFSET_PROTOCOL 	0xf921def1 
#define REQUEST_LEAFSET   1
#define BROADCAST_LEAFSET 2

#define ROUTINGTABLE_PROTOCOL	0x89ce110e 
#define REQUEST_ROUTE_ROW   1
#define BROADCAST_ROUTE_ROW 2

/*Unknown address*/
#define PASTRY_ENDPOINT_MESSAGE 2
/*End Core Messages*/

#define PAST  0x1b8c

/* UDP message types */
#define IP_ADDRESS_REQUEST_MSG  2
#define IP_ADDRESS_RESPONSE_MSG 3
#define PING_MSG                8
#define PING_RESPONSE_MESSAGE   9
#define WRONG_EPOCH_MESSAGE     14

/* ID Types */
#define ID_TYPE_NORMAL      1
#define ID_TYPE_RINGID      2
#define ID_TYPE_GCID        3
#define ID_TYPE_VERSIONKEY  41
#define ID_TYPE_FRAGMENTKEY 42

/* FreePastry constants */
#define LEAFSET_SIZE        24
#define ID_PRINT_SIZE       12

typedef struct sub_message_info_t {
  guint32 address;
  gint16 type;
} sub_message_info_t;

extern gint get_epoch_inet_socket_address_len(tvbuff_t *tvb, gint offset);
extern gint decode_epoch_inet_socket_address(tvbuff_t *tvb, proto_tree *parent_tree, gint offset, gchar *attribute_name);
extern gint get_node_handle_len(tvbuff_t *tvb, gint offset);
extern gchar* get_id_full(tvbuff_t *tvb, gint offset);
extern gchar* get_id(tvbuff_t *tvb, gint offset);
extern gchar* get_id_from_node_handle(tvbuff_t *tvb, gint offset);

extern gint print_id_into_col_info(tvbuff_t *tvb, packet_info *pinfo, gint offset, gchar* info_string);
extern gint print_id_value_into_col_info(tvbuff_t *tvb, packet_info *pinfo, gint offset, gchar* info_string);
extern gint print_ringid_into_col_info(tvbuff_t *tvb, packet_info *pinfo, gint offset, gchar* info_string);
extern gint print_gcid_into_col_info(tvbuff_t *tvb, packet_info *pinfo, gint offset, gchar* info_string);
extern gint decode_type_and_id(tvbuff_t *tvb, proto_tree *tree, gint offset);
extern gint decode_id_from_type(tvbuff_t *tvb, proto_tree *tree, short type, gint offset);
extern gint decode_id_value(tvbuff_t *tvb, proto_tree *tree, gint offset);
extern gint decode_ringid(tvbuff_t *tvb, proto_tree *tree, gint offset);
extern gint decode_gcid(tvbuff_t *tvb, proto_tree *tree, gint offset);
extern gint decode_versionkey(tvbuff_t *tvb, proto_tree *tree, gint offset);
extern gint decode_fragmentkey(tvbuff_t *tvb, proto_tree *tree, gint offset);

extern gint decode_message_version(tvbuff_t *tvb, proto_tree *tree, gint offset, guint32 address);

extern gint decode_nodehandle(tvbuff_t *tvb, proto_tree *parent_tree, gint offset, gchar *attribute_name);
extern gint decode_routeset(tvbuff_t *tvb, proto_tree *parent_tree, gint offset, gchar *attribute_name);
extern gint decode_leafset(tvbuff_t *tvb, proto_tree *parent_tree, gint offset, gchar *attribute_name);
extern gint decode_nodehandleset(tvbuff_t *tvb, proto_tree *parent_tree, gint offset, gchar *attribute_name);
extern gint decode_multiring_nodehandleset(tvbuff_t *tvb, proto_tree *parent_tree, gint offset, gchar *attribute_name);

extern void decode_freepastry_tcp_msg_invariant(tvbuff_t *tvb, packet_info *pinfo, proto_tree *tree, gint offset, guint32 address);

#endif
