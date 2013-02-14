/* packet-freepastry.c
* Routines for the FreePastry Binary protocol 
ion
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

#ifdef HAVE_CONFIG_H
# include "config.h"
#endif

#include <gmodule.h>
#include <epan/packet.h>
#include <epan/emem.h>
#include <epan/conversation.h>
#include <epan/dissectors/packet-tcp.h>
#include <epan/prefs.h>

#include "packet-freepastry.h"

/* desegmentation of FreePastry over TCP */
static gboolean freepastry_desegment = TRUE;

static int proto_freepastry = -1;
static int hf_freepastry_header_magic_number = -1;
static int hf_freepastry_header_version_number = -1;
static int hf_freepastry_header_source_route = -1;
static int hf_freepastry_header_current_hop = -1;
static int hf_freepastry_header_num_hop = -1;
static int hf_freepastry_header_source_route_len = -1;
static int hf_freepastry_header_header_direct = -1;
static int hf_freepastry_header_app_id = -1;
static int hf_freepastry_msg_header_address = -1;
static int hf_freepastry_msg_header_has_sender = -1;
static int hf_freepastry_msg_header_priority = -1;
static int hf_freepastry_liveness_msg_type = -1;
static int hf_freepastry_eisa_num_add = -1;
static int hf_freepastry_eisa_ip = -1;
static int hf_freepastry_eisa_port = -1;
static int hf_freepastry_eisa_epoch = -1;
static int hf_freepastry_id_type = -1;
static int hf_freepastry_id_value = -1;
static int hf_freepastry_versionkey_version = -1;
static int hf_freepastry_fragmentkey_id = -1;
static int hf_freepastry_ringid = -1;
static int hf_freepastry_gcid_expiration = -1;
static int hf_freepastry_rs_capacity = -1;
static int hf_freepastry_rs_size = -1;
static int hf_freepastry_rs_closest = -1;
static int hf_freepastry_ns_size = -1;
static int hf_freepastry_mns_type = -1;
static int hf_freepastry_liveness_msg_sent_time = -1;
static int hf_freepastry_ls_size = -1;
static int hf_freepastry_ls_num_unique_handle = -1;
static int hf_freepastry_ls_cw_size = -1;
static int hf_freepastry_ls_ccw_size = -1;
static int hf_freepastry_ls_handle_index = -1;
static int hf_freepastry_router_msg_type  = -1;
static int hf_freepastry_join_msg_type = -1;
static int hf_freepastry_leafset_msg_type = -1;
static int hf_freepastry_routingtable_msg_type = -1;
static int hf_freepastry_msg_size = -1;
static int hf_freepastry_commonapi_msg_type = -1;
static int hf_freepastry_direct_msg_type = -1;
static int hf_freepastry_direct_msg_version = -1;
static int hf_freepastry_router_msg_version = -1;
static int hf_freepastry_commonapi_msg_version = -1;
static int hf_freepastry_join_msg_version = -1;
static int hf_freepastry_routingtable_msg_version = -1;
static int hf_freepastry_leafset_proto_msg_version = -1;
static int hf_freepastry_direct = -1;
static int hf_freepastry_router  = -1;
static int hf_freepastry_commonapi = -1;
static int hf_freepastry_join = -1;
static int hf_freepastry_routingtable = -1;
static int hf_freepastry_leafset_proto = -1;
static int hf_freepastry_liveness = -1;

static gint ett_freepastry = -1;
static gint ett_freepastry_eisa = -1;
static gint ett_freepastry_isa = -1;
static gint ett_freepastry_nh = -1;
static gint ett_freepastry_ns = -1;
static gint ett_freepastry_rs = -1;
static gint ett_freepastry_ls = -1;
static gint ett_freepastry_ls_cw = -1;
static gint ett_freepastry_ls_ccw = -1;

static dissector_handle_t freepastry_udp_handle; 
static dissector_handle_t freepastry_tcp_handle;

static dissector_table_t subdissector_message_version_table;

/*
 * State information stored with a conversation.
 */
struct freepastry_tcp_stream_data {
  guint32   id; /*A unique ID (the frame number)that identify the header*/
  gboolean is_app_stream; /* Is it a normal FreePastry Socket? */
};

/* Address mapping */
static const value_string freepastry_address[] = {
  { DIRECT_ACCESS, 	       "Direct Access" },
  { ROUTER, 	             "Router" },
  { JOIN_PROTOCOL, 	       "Join Protocol" },
  { LEAFSET_PROTOCOL, 	   "Leafset Protocol" },
  { ROUTINGTABLE_PROTOCOL, "Route Protocol" }, 
  { 0, NULL }
};

/*Messages for "Direct access" module*/
static const value_string freepastry_direct_access_msg[] = {
  { SOURCEROUTE_MSG,   	     "Source Route" },
  { LEAFSET_REQUEST_MSG, 	   "LeafSet Request" },
  { LEAFSET_RESPONSE_MSG,    "LeafSet Response" },
  { NODE_ID_REQUEST_MSG, 	   "Node ID Request" },
  { NODE_ID_RESPONSE_MSG,    "Node ID Response" }, 
  { ROUTE_ROW_REQUEST_MSG,   "Route Row Request" },
  { ROUTE_ROW_RESPONSE_MSG,  "Route Row Response" }, 
  { SOURCEROUTE_REQUEST_MSG, "Routes Request" },
  { SOURCEROUTE_RESPONSE_MSG,"Routes Response" },
  { 0, NULL }
};

/*Messages for "Join" module*/
static const value_string freepastry_join_msg[] = {
  { JOIN_REQUEST,           "Join Request" }, 
  { CONSISTENT_JOIN_MSG, 	  "Consistent Join" },
  { 0, NULL }
};

/*Messages for "Router" module*/
static const value_string freepastry_router_msg[] = {
  { ROUTE,              "Route Message" },
  { 0, NULL }
};

/*Messages for "Leafset Maintenance" module*/
static const value_string freepastry_leafset_msg[] = {
  { REQUEST_LEAFSET,        "Request LeafSet" }, 
  { BROADCAST_LEAFSET,      "Broadcast LeafSet" },
  { 0, NULL }
};

/*Messages for "Routing Table Maintenance" module*/
static const value_string freepastry_routingtable_msg[] = {
  { REQUEST_ROUTE_ROW, 	    "Request Route Row" },
  { BROADCAST_ROUTE_ROW,    "Broadcast Route Row" },
  { 0, NULL }
};

/*Messages for "Common API" module*/
static const value_string freepastry_commonapi_msg[] = {
  { 2, 	    "Pastry Endpoint" },
  { 0, NULL }
};

/* Message Priority */
static const value_string freepastry_priority[] = {
  { -15,  "Max" },
  { -10,  "High" },
  { -5, "Medium High" },
  { 0, "Medium" },
  { 5, "Medium Low" }, 
  { 10, "Low" },
  { 0, NULL }
};

/* ID type */
static const value_string freepastry_id_type[] = {
  { ID_TYPE_NORMAL,  "Normal" },
  { ID_TYPE_RINGID,  "RingId" },
  { ID_TYPE_GCID,  "GCId" },
  { ID_TYPE_VERSIONKEY,  "Version Key" },
  { ID_TYPE_FRAGMENTKEY,  "Fragment Key" },
  { 0, NULL }
};

/*UDP Message type mapping*/
static const value_string freepastry_liveness_message[] = {
  { IP_ADDRESS_REQUEST_MSG, 	"IP Address Request" },
  { IP_ADDRESS_RESPONSE_MSG, 	"IP Address Response" },
  { PING_MSG, 	              "Ping Request" },
  { PING_RESPONSE_MESSAGE, 	  "Ping Response" },
  { WRONG_EPOCH_MESSAGE,  	  "Wrong Epoch" },
  { 0, NULL }
};

/*
 * Start: Common FreePastry Objects dissection.
 * These functions can be used by other files in the FreePastry plugin 
 * (Common API dissectors, core message dissectors).
 */

/**
*   @return the size of a "EpochInetSocketAddress" object.
**/
gint
get_epoch_inet_socket_address_len(tvbuff_t *tvb, gint offset)
{
  guint8 nb_addr;
  nb_addr = tvb_get_guint8(tvb, offset);
  return (9+nb_addr*6);
}

/**
*   Decode a "EpochInetSocketAddress" object.
*   @return the new offset or -1 on error.
**/
gint
decode_epoch_inet_socket_address(tvbuff_t *tvb, proto_tree *parent_tree, gint offset, gchar *attribute_name)
{
  proto_item *ti = NULL;
  proto_tree *epoch_inet_socket_address_tree = NULL;
  proto_tree *inet_socket_address_tree = NULL;
  guint8 nb_addr;
  gchar *ip_str;
  guint16 port_number;
  int i;

  /*Get the number of (IPv4 address, port number) couple*/
  nb_addr = tvb_get_guint8(tvb, offset);
  offset++;

  if (tvb_reported_length_remaining(tvb, offset) < (8+nb_addr*6)){
    proto_tree_add_text(parent_tree, tvb, offset, -1, "Too short EISA attribute!");
    return -1;
  }

  /*For each InetSocketAddress*/
  for (i = 0; i < nb_addr; ++i){ 
    ip_str = ip_to_str(tvb_get_ptr(tvb, offset, 4));
    port_number = tvb_get_ntohs(tvb, offset+4);
    if (i == 0) {
      ti = proto_tree_add_text(parent_tree, tvb, (offset -1), (nb_addr*6+ 9),
        "%s: %s:%d (EpochInetSocketAddress with %d address(es))", 
        attribute_name, ip_str, port_number, nb_addr);
      epoch_inet_socket_address_tree = proto_item_add_subtree(ti, ett_freepastry_eisa);
        proto_tree_add_item(epoch_inet_socket_address_tree, hf_freepastry_eisa_num_add, tvb, (offset-1), 1, FALSE);
    }
    ti = proto_tree_add_text(epoch_inet_socket_address_tree, tvb, 
      offset, 6, "InetSocketAddress #%d %s:%d", i+1, ip_str, port_number);
    inet_socket_address_tree = proto_item_add_subtree(ti, ett_freepastry_isa);
    
    proto_tree_add_ipv4(inet_socket_address_tree, 
      hf_freepastry_eisa_ip, tvb, offset, 4, tvb_get_ipv4(tvb, offset));
    proto_tree_add_uint(inet_socket_address_tree, 
      hf_freepastry_eisa_port, tvb, offset+4, 2, port_number);
    offset += 6;
  }
  if (nb_addr != 0) {
    /*Get the epoch (random number)*/
    proto_tree_add_item(epoch_inet_socket_address_tree, hf_freepastry_eisa_epoch, tvb, offset, 8, FALSE);
    offset += 8;
  }
  return offset;
}

/**
*   @return the size of a "Node Handle" object.
**/
gint
get_node_handle_len(tvbuff_t *tvb, gint offset)
{
  guint8 nb_addr;
  nb_addr = tvb_get_guint8(tvb, offset);
  return (29+nb_addr*6);
}

/**
*   Build a buffer with a full representation of the ID.
*   @return a buffer containing the ID.
**/
gchar*
get_id_full(tvbuff_t *tvb, gint offset)
{
  guint32 id1, id2, id3, id4, id5;

  id1 = tvb_get_ntohl(tvb, offset + 16);
  id2 = tvb_get_ntohl(tvb, offset + 12);
  id3 = tvb_get_ntohl(tvb, offset + 8);
  id4 = tvb_get_ntohl(tvb, offset + 4);
  id5 = tvb_get_ntohl(tvb, offset);
  return ep_strdup_printf(
    "0x%02X%02X%02X%02X%02X%02X%02X%02X%02X%02X%02X%02X%02X%02X%02X%02X%02X%02X%02X%02X",
    (id1 >> 24) & 0xff, (id1 >> 16) & 0xff, (id1 >> 8) & 0xff, id1 & 0xff,
    (id2 >> 24) & 0xff, (id2 >> 16) & 0xff, (id2 >> 8) & 0xff, id2 & 0xff,
    (id3 >> 24) & 0xff, (id3 >> 16) & 0xff, (id3 >> 8) & 0xff, id3 & 0xff,
    (id4 >> 24) & 0xff, (id4 >> 16) & 0xff, (id4 >> 8) & 0xff, id4 & 0xff,
    (id5 >> 24) & 0xff, (id5 >> 16) & 0xff, (id5 >> 8) & 0xff, id5 & 0xff);
}

/**
*   Build a buffer with a truncated representation of the ID.
*   @return a buffer containing the truncted ID.
**/
gchar*
get_id(tvbuff_t *tvb, gint offset)
{
  guint32 id = tvb_get_ntohl(tvb, offset + 16);

  /* returned buffer is automatically freed once the current packet dissection completes*/
  return ep_strdup_printf(" <0x%02X%02X%02X..>", (id >> 24) & 0xff,
    (id >> 16) & 0xff, (id >> 8) & 0xff);
}

/**
*   Print an ID in the info column.
*   Used by common API applications.
*   @return the new offset or -1 on failure.
**/
gint
print_id_into_col_info(tvbuff_t *tvb, packet_info *pinfo, gint offset, gchar* info_string){
  gint16 type;
  /*We need to read the ID type*/
  if (tvb_reported_length_remaining(tvb, offset) < 2){
    col_append_fstr(pinfo->cinfo, COL_INFO, " %s (Malformed Message)",
      info_string);
    return -1;
  }
  type = tvb_get_ntohs(tvb, offset);
  offset +=2;
  switch (type){
    case ID_TYPE_NORMAL:
      return print_id_value_into_col_info(tvb,pinfo, offset, info_string);
    case ID_TYPE_RINGID:
      return print_ringid_into_col_info(tvb,pinfo, offset, info_string);
    case ID_TYPE_GCID:
      return print_gcid_into_col_info(tvb,pinfo, offset, info_string);
    case ID_TYPE_VERSIONKEY:
    case ID_TYPE_FRAGMENTKEY:
    default:
      return -1;
  }
}

/**
*   Print an ID value (2O bytes) in the info column.
*   @return the new offset or -1 on failure.
**/
gint
print_id_value_into_col_info(tvbuff_t *tvb, packet_info *pinfo, gint offset, gchar* info_string){
  if (tvb_reported_length_remaining(tvb, offset) < 20){
    col_append_fstr(pinfo->cinfo, COL_INFO, " %s (Malformed Message)",
      info_string);
    return -1;
  }
  col_append_fstr(pinfo->cinfo, COL_INFO, " %s%s",
      info_string, get_id(tvb, offset));
  return offset + 20;
}

/**
*   Print a ring ID in the info column.
*   @return the new offset or -1 on failure.
**/
gint
print_ringid_into_col_info(tvbuff_t *tvb, packet_info *pinfo, gint offset, gchar* info_string){
  if (tvb_reported_length_remaining(tvb, offset) < 44){
    col_append_fstr(pinfo->cinfo, COL_INFO, " %s (Malformed Message)",
      info_string);
    return -1;
  } else {
    gint16 type = tvb_get_ntohs(tvb, offset);
    if (type == ID_TYPE_NORMAL) {
      type = tvb_get_ntohs(tvb, offset + 22);
      if (type == ID_TYPE_NORMAL){
        col_append_fstr(pinfo->cinfo, COL_INFO, " %s%s%s",
        info_string, get_id(tvb, offset + 2), get_id(tvb, offset + 24));
      } else {
        col_append_fstr(pinfo->cinfo, COL_INFO, info_string);
      }
    } else {
      col_append_fstr(pinfo->cinfo, COL_INFO, info_string);
    }
    return offset +44;
  }
}

/**
*   Print a GCID in the info column.
*   @return the new offset or -1 on failure.
**/
gint
print_gcid_into_col_info(tvbuff_t *tvb, packet_info *pinfo, gint offset, gchar* info_string){
  if (tvb_reported_length_remaining(tvb, offset) < 30){
    col_append_fstr(pinfo->cinfo, COL_INFO, " %s (Malformed Message)",
      info_string);
    return -1;
  } else {
    gint16 type = tvb_get_ntohs(tvb, offset);
    if (type == ID_TYPE_GCID) {
      type = tvb_get_ntohs(tvb, offset + 10);
      if (type == ID_TYPE_NORMAL){
        col_append_fstr(pinfo->cinfo, COL_INFO, " %s%s",
        info_string, get_id(tvb, offset + 12));
      } else {
        /*Do not try to print complex ID here*/
        col_append_fstr(pinfo->cinfo, COL_INFO, info_string);
      }
    } else {
      col_append_fstr(pinfo->cinfo, COL_INFO, " %s (Malformed Message)", 
        info_string);
      return -1;
    }
    return offset +44;
  }
}

/**
*   Decode into the tree an ID.
*   Used by common API applications.
*   @return the new offset or -1 on failure.
**/
gint
decode_type_and_id(tvbuff_t *tvb, proto_tree *tree, gint offset)
{
  gint16 type;
  if (tvb_reported_length_remaining(tvb, offset) < 2){
    proto_tree_add_text(tree, tvb, offset, -1 , "Too short attributes!");
    return -1;
  }
  type = tvb_get_ntohs(tvb, offset);
  proto_tree_add_item(tree, hf_freepastry_id_type, tvb, offset, 2, FALSE);
  return decode_id_from_type(tvb, tree, type, offset + 2);
}

/**
*   Decode into the tree an ID depending on its type.
*   @return the new offset or -1 on failure.
**/
gint
decode_id_from_type(tvbuff_t *tvb, proto_tree *tree, short type, gint offset)
{
  switch (type){
    case ID_TYPE_NORMAL:
      return decode_id_value(tvb,tree, offset);
    case ID_TYPE_RINGID:
      return decode_ringid(tvb,tree, offset);
    case ID_TYPE_GCID:
      return decode_gcid(tvb, tree, offset);
    case ID_TYPE_VERSIONKEY:
      return decode_versionkey(tvb, tree, offset);
    case ID_TYPE_FRAGMENTKEY:
      return decode_fragmentkey(tvb, tree, offset);
    default:
      return -1;
  }

}

/**
*   Decode into the tree an ID value.
*   @return the new offset or -1 on failure.
**/
gint
decode_id_value(tvbuff_t *tvb, proto_tree *tree, gint offset)
{
  if (tvb_reported_length_remaining(tvb, offset) < 20){
    proto_tree_add_text(tree, tvb, offset, -1, "Too short attributes!");
    return -1;
  }
  proto_tree_add_string(tree, hf_freepastry_id_value, tvb, offset, 20, get_id_full(tvb, offset));
  return offset + 20;
}

/**
*   Decode into the tree a ring ID.
*   @return the new offset or -1 on failure.
**/
gint
decode_ringid(tvbuff_t *tvb, proto_tree *tree, gint offset)
{
  /*Ring Id*/
  offset = decode_type_and_id(tvb, tree, offset);
  if (offset != -1){
    /* Id*/
    offset = decode_type_and_id(tvb, tree, offset);
  }
  return offset;
}

/**
*   Decode into the tree a GCID.
*   @return the new offset or -1 on failure.
**/
gint
decode_gcid(tvbuff_t *tvb, proto_tree *tree, gint offset)
{
  gint16 type;
  if (tvb_reported_length_remaining(tvb, offset) < 2){
    proto_tree_add_text(tree, tvb, offset, -1 , "Too short attributes!");
    return -1;
  }
  type = tvb_get_ntohs(tvb, offset);
  proto_tree_add_item(tree, hf_freepastry_id_type, tvb, offset, 2, type);
  if (type != ID_TYPE_GCID){
    proto_tree_add_text(tree, tvb, offset, -1 , "Not a GCID!");
    return -1;
  }
  offset += 2;
  if (tvb_reported_length_remaining(tvb, offset) < 8){
    proto_tree_add_text(tree, tvb, offset, -1 , "Too short attributes!");
    return -1;
  }
  proto_tree_add_item(tree, hf_freepastry_gcid_expiration, tvb, offset, 8, FALSE);
  return decode_type_and_id(tvb, tree, offset + 8);
}

/**
*   Decode into the tree a VersionKey (used by Glacier).
*   @return the new offset or -1 on failure.
**/
gint
decode_versionkey(tvbuff_t *tvb, proto_tree *tree, gint offset)
{
  if (tvb_reported_length_remaining(tvb, offset) < 8){
    proto_tree_add_text(tree, tvb, offset, -1 , "Too short attributes!");
    return -1;
  }
  proto_tree_add_item(tree, hf_freepastry_versionkey_version, tvb, offset, 8, FALSE);
  return decode_type_and_id(tvb, tree, offset + 8);
}

/**
*   Decode into the tree a FragmentKey (used by Glacier).
*   @return the new offset or -1 on failure.
**/
gint
decode_fragmentkey(tvbuff_t *tvb, proto_tree *tree, gint offset)
{
  if (tvb_reported_length_remaining(tvb, offset) < 4){
    proto_tree_add_text(tree, tvb, offset, -1 , "Too short attributes!");
    return -1;
  }
  proto_tree_add_item(tree, hf_freepastry_fragmentkey_id, tvb, offset, 4, FALSE);
  return decode_versionkey(tvb, tree, offset + 4);
}


/**
*   Build a buffer with a truncated representation of the ID in a Node Handle.
*   @return a buffer containing the truncted ID.
**/
gchar*
get_id_from_node_handle(tvbuff_t *tvb, gint offset)
{
  guint32 id;
  
  offset += get_epoch_inet_socket_address_len(tvb, offset);
  id = tvb_get_ntohl(tvb, offset + 16);
  /* returned buffer is automatically freed once the current packet dissection completes*/
  return ep_strdup_printf("<0x%02X%02X%02X..>", (id >> 24) & 0xff,
    (id >> 16) & 0xff, (id >> 8) & 0xff);
}

/**
*   Decode a "Node Handle" object.
*   @return the new offset or -1 on error.
**/
gint
decode_nodehandle(tvbuff_t *tvb, proto_tree *parent_tree, gint offset, gchar *attribute_name)
{
  proto_item  *ti = NULL;
  proto_tree  *node_handle_tree = NULL;

  ti = proto_tree_add_text(parent_tree, tvb, offset, 1, attribute_name);
  node_handle_tree = proto_item_add_subtree(ti, ett_freepastry_nh);
  offset = decode_epoch_inet_socket_address(tvb, node_handle_tree, offset, "EpochInetSocketAddress");
  
  if (offset != -1){
    /*20 bytes = ID Length*/
    if (tvb_reported_length_remaining(tvb, offset) >= 20) {
      gchar* short_id = get_id(tvb, offset);
      
      proto_item_append_text(ti, short_id);
      proto_tree_add_string(node_handle_tree, hf_freepastry_id_value, tvb, offset, 20, get_id_full(tvb, offset));
      offset += 20;
      proto_item_set_end(ti, tvb, offset);
    } else {
      proto_tree_add_text(node_handle_tree, tvb, offset, -1, "Too short attribute!");
      return -1;
    }
  }
  return offset;
}

/**
*   Decode a "Route Set" object.
*   @return the new offset or -1 on error.
**/
gint
decode_routeset(tvbuff_t *tvb, proto_tree *parent_tree, gint offset, gchar *attribute_name)
{
  proto_item *ti = NULL;
  proto_tree *routeset_tree = NULL;
  guint8 routeset_size;
  guint8 routeset_closest;
  int i;

  ti = proto_tree_add_text(parent_tree, tvb, offset, 1,
    "%s :", attribute_name);
  routeset_tree = proto_item_add_subtree(ti, ett_freepastry_rs);
  proto_tree_add_item(routeset_tree, hf_freepastry_rs_capacity, tvb, offset, 1, FALSE);
  offset++;
  routeset_size = tvb_get_guint8(tvb, offset);
  proto_tree_add_uint(routeset_tree, hf_freepastry_rs_size, tvb, offset, 1, routeset_size);
  offset++;
  routeset_closest = tvb_get_guint8(tvb, offset);
  proto_tree_add_uint(routeset_tree, hf_freepastry_rs_closest, tvb, offset, 1, routeset_closest);
  offset++;
  proto_item_append_text(ti, " (%d Node Handles)", routeset_size);

  for (i=0; i < routeset_size; ++i){
    if ((i+1) == routeset_closest){
      proto_item_append_text(ti, " (closest %s)", get_id_from_node_handle(tvb, offset));
    }
    offset = decode_nodehandle(tvb, routeset_tree, offset, "Node Handle");
    if (offset == -1) {
      return -1;
    }
  }
  proto_item_set_end(ti, tvb, offset);
  return offset;
}

/**
*   Decode a "Node Handle Set" object.
*   @return the new offset or -1 on error.
**/
gint
decode_nodehandleset(tvbuff_t *tvb, proto_tree *parent_tree, gint offset, gchar *attribute_name)
{
  proto_item *ti = NULL;
  proto_tree *nodeset_tree = NULL;
  guint16 nodeset_size;
  int i;

  ti = proto_tree_add_text(parent_tree, tvb, offset, 1,
    "%s :", attribute_name);
  nodeset_tree = proto_item_add_subtree(ti, ett_freepastry_ns);

  nodeset_size = tvb_get_ntohs(tvb, offset);
  proto_tree_add_uint(nodeset_tree, hf_freepastry_ns_size, tvb, offset, 2, nodeset_size);
  offset +=2;

  proto_item_append_text(ti, " (%d Node Handles)", nodeset_size);
  for (i=0; i < nodeset_size; ++i){
    offset = decode_nodehandle(tvb, nodeset_tree, offset, "Node Handle");
    if (offset == -1) {
      return -1;
    }
  }
  proto_item_set_end(ti, tvb, offset);
  return offset;
}


/**
*   Decode a "Multiring Node Handle Set" object.
*   @return the new offset or -1 on error.
**/
gint
decode_multiring_nodehandleset(tvbuff_t *tvb, proto_tree *parent_tree, gint offset, gchar *attribute_name)
{
  offset = decode_type_and_id(tvb, parent_tree, offset);
  proto_tree_add_item(parent_tree, hf_freepastry_mns_type, tvb, offset + 20, 2, FALSE);
  offset = decode_nodehandleset(tvb, parent_tree, offset + 22, attribute_name);
  return offset;
}

/**
*   @return the size of a Leafset object.
**/
gint
get_leafset_len(tvbuff_t *tvb, gint offset)
{
  guint8 num_unique_handle;
  guint8 num_cw_size;
  guint8 num_ccw_size;
  int i;
  guint8 pos = offset +1;
  
  num_unique_handle = tvb_get_guint8(tvb, pos);
  pos++;
  num_cw_size = tvb_get_guint8(tvb, pos);
  pos++;
  num_ccw_size = tvb_get_guint8(tvb, pos);
  pos++;
  pos += get_node_handle_len(tvb, pos);
  for (i = 0; i < num_unique_handle; ++i){
    pos += get_node_handle_len(tvb, pos);
  }
  return (pos + num_cw_size + num_ccw_size - offset);
}

/**
*   Decode a "Leafset" object.
*   @return the new offset or -1 on error.
**/
gint
decode_leafset(tvbuff_t *tvb, proto_tree *parent_tree, gint offset, gchar *attribute_name)
{
  proto_item *ti = NULL;
  proto_item *ti_cw = NULL;
  proto_item *ti_ccw = NULL;
  proto_tree *leafset_tree = NULL;
  proto_tree *cw_tree = NULL;
  proto_tree *ccw_tree = NULL;
  guint8 num_unique_handle;
  guint8 num_cw_size;
  guint8 num_ccw_size;
  
  /*these vars are for drawing the LeafSet, they need to be filled in with the sub values before the LS can be rendered*/
  gchar *base_handle_id;
  gchar *node_handle_id[24];
  guint8 ccw_index[12];
  guint8 cw_index[12];
  guint32 leafset_string_length;
  gchar *leafset_string;
  guint32 leafset_string_offset;

  int i;

  ti = proto_tree_add_text(parent_tree, tvb, offset, 1, attribute_name);
  
  leafset_tree = proto_item_add_subtree(ti, ett_freepastry_ls);
  /*The total capacity of the leafset (not including the base handle)*/
  proto_tree_add_item(leafset_tree, hf_freepastry_ls_size, tvb, offset, 1, FALSE);
  offset++;
  /*The number of NodeHandles to read*/
  num_unique_handle = tvb_get_guint8(tvb, offset);
  if (num_unique_handle > 24){
    proto_tree_add_text(leafset_tree, tvb, 1, -1, "Too many node handles");
    return -1;
  }
  proto_tree_add_uint(leafset_tree, hf_freepastry_ls_num_unique_handle,
    tvb, offset, 1, num_unique_handle);
  offset++;
  /*The number of element of the clockwise similar set*/
  num_cw_size = tvb_get_guint8(tvb, offset);
  if (num_cw_size > 12){
    proto_tree_add_text(leafset_tree, tvb, 1, -1, 
      "Too many node handles in clockwise similar set");
    return -1;
  }
  proto_tree_add_uint(leafset_tree, hf_freepastry_ls_cw_size,
    tvb, offset, 1, num_cw_size);
  offset++;  
  /*The number of element of the counter clockwise similar set*/
  num_ccw_size = tvb_get_guint8(tvb, offset);
  if (num_ccw_size > 12){
    proto_tree_add_text(leafset_tree, tvb, 1, -1, 
      "Too many node handles in counterclockwise similar set");
    return -1;
  }
  proto_tree_add_uint(leafset_tree, hf_freepastry_ls_ccw_size,
    tvb, offset, 1, num_ccw_size);
  offset++;
  /*The base NodeHandle*/
  base_handle_id = get_id_from_node_handle(tvb,offset);
  offset = decode_nodehandle(tvb, leafset_tree, offset, "Base Node Handle");
  
  /*The unique handles*/
  for (i = 0; i < num_unique_handle; ++i){
    if (offset == -1) {
      return -1;
    }
    node_handle_id[i] = get_id_from_node_handle(tvb,offset);
    offset = decode_nodehandle(tvb, leafset_tree, offset, ep_strdup_printf("Node Handle #%d", i));
  }
  /*The cw addresses*/
  ti_cw = proto_tree_add_text(leafset_tree, tvb, offset, num_cw_size, "Clockwise Similar Set");
  cw_tree = proto_item_add_subtree(ti_cw, ett_freepastry_ls_cw);
  for (i = 0; i < num_cw_size; ++i){
    cw_index[i] = tvb_get_guint8(tvb, offset);
    proto_tree_add_item(cw_tree, hf_freepastry_ls_handle_index, tvb, offset, 1, FALSE);
    offset++;
  }
  proto_item_set_end(ti_cw, tvb, offset);
  /*The ccw addresses*/
  ti_ccw = proto_tree_add_text(leafset_tree, tvb, offset, num_ccw_size, "Counter Clockwise Similar Set");
  ccw_tree = proto_item_add_subtree(ti_ccw, ett_freepastry_ls_ccw);
  for (i = 0; i < num_ccw_size; ++i){
    ccw_index[i] = tvb_get_guint8(tvb, offset);
    proto_tree_add_item(ccw_tree, hf_freepastry_ls_handle_index, tvb, offset, 1, FALSE);
    offset++;
  }
  proto_item_set_end(ti_ccw, tvb, offset);
  
  /* draw the leafset: heading space 2 brackets, null terminator */
  leafset_string_length = 4+(num_ccw_size+num_cw_size+1)*ID_PRINT_SIZE;
  leafset_string = ep_alloc(leafset_string_length);
  leafset_string_offset = 0;

  g_snprintf(leafset_string, leafset_string_length - leafset_string_offset, " ");
  leafset_string_offset++;

  for (i=num_ccw_size-1; i>=0; i--) {
    g_snprintf(leafset_string+leafset_string_offset, leafset_string_length - leafset_string_offset, node_handle_id[ccw_index[i]]);
    leafset_string_offset+=ID_PRINT_SIZE;
  }

  g_snprintf(leafset_string+leafset_string_offset, leafset_string_length - leafset_string_offset, "[%s]", base_handle_id);
  leafset_string_offset+=2+ID_PRINT_SIZE;

  for (i=0; i < num_ccw_size; i++) {
    g_snprintf(leafset_string+leafset_string_offset, leafset_string_length - leafset_string_offset, node_handle_id[cw_index[i]]);
    leafset_string_offset+=ID_PRINT_SIZE;
  }

  proto_item_append_text(ti,leafset_string);

  proto_item_set_end(ti, tvb, offset);
  return offset;
}

gint decode_message_version(tvbuff_t *tvb, proto_tree *tree, gint offset, guint32 address)
{
  /*We already know that there is enough byte for version field here*/
  switch (address){
    case DIRECT_ACCESS:
      proto_tree_add_item(tree, hf_freepastry_direct_msg_version, tvb, offset, 1, FALSE);
      break;
    case ROUTER:
      proto_tree_add_item(tree, hf_freepastry_router_msg_version, tvb, offset, 1, FALSE);
      break;
    case JOIN_PROTOCOL:
      proto_tree_add_item(tree, hf_freepastry_join_msg_version, tvb, offset, 1, FALSE);
      break;
    case LEAFSET_PROTOCOL:
      proto_tree_add_item(tree, hf_freepastry_leafset_proto_msg_version, tvb, offset, 1, FALSE);
      break;
    case ROUTINGTABLE_PROTOCOL:
      proto_tree_add_item(tree, hf_freepastry_routingtable_msg_version, tvb, offset, 1, FALSE);
      break;
    default:/*Common API*/
      proto_tree_add_item(tree, hf_freepastry_commonapi_msg_version, tvb, offset, 1, FALSE);
  }
  return offset+1;
}
/*
 * End: Common FreePastry Objects dissection.
 */


/**
*   Dissect a FreePastry UDP Message
**/
static void
dissect_freepastry_common_udp(tvbuff_t *tvb, packet_info *pinfo, proto_tree *tree)
{
  gint offset = 0;
  guint8 nb_hops = 0;
  guint32 address;
  guint16 type = 0;
  gboolean has_sender = FALSE;

  int i = 0;
  proto_item *ti =  NULL;
  proto_tree *freepastry_tree = NULL;

  if (tvb_length(tvb) < 16 ) {
    return;
  }

  if (tvb_get_ntohl(tvb, offset) != PASTRY_MAGIC_NUMBER){
    return;
  }

  /*This is a FreePastry message for sure*/
  if (check_col(pinfo->cinfo, COL_PROTOCOL)){
    col_set_str(pinfo->cinfo, COL_PROTOCOL, "FreePastry");
  }
   if (check_col(pinfo->cinfo, COL_INFO)){
    col_clear (pinfo->cinfo, COL_INFO);
    col_add_fstr(pinfo->cinfo, COL_INFO, "%d > %d",
      pinfo->srcport, pinfo->destport);
  }

  if (tree){
    ti = proto_tree_add_item(tree, proto_freepastry, tvb, 0, -1, FALSE);
    freepastry_tree = proto_item_add_subtree(ti, ett_freepastry);
    proto_tree_add_item_hidden(tree, hf_freepastry_liveness, tvb, offset, -1, FALSE);
    proto_tree_add_item(freepastry_tree, hf_freepastry_header_magic_number, tvb, offset, 4, FALSE);
    proto_tree_add_item(freepastry_tree, hf_freepastry_header_version_number, tvb, offset+4, 4, FALSE);
    proto_tree_add_item(freepastry_tree, hf_freepastry_header_current_hop, tvb, offset+8, 1, FALSE);
  }
  /*magic_number + version + hop_counter*/
  offset += 9;

  nb_hops = tvb_get_guint8(tvb, offset);
  
  if (tree){
    proto_tree_add_uint(freepastry_tree, hf_freepastry_header_num_hop, tvb, offset, 1, nb_hops);
    proto_tree_add_item(freepastry_tree, hf_freepastry_header_source_route_len, tvb, offset + 1, 2, FALSE);
  }
  /*num_hops + source_route_len*/
  offset += 3;
  if (tree){
    for (i = 0; i < nb_hops; ++i){
      offset = decode_epoch_inet_socket_address(tvb, freepastry_tree, offset, "Hop");
      if (offset == -1){
        return;
      }
    }
  } else {
    for (i = 0; i < nb_hops; ++i){
      offset += get_epoch_inet_socket_address_len(tvb, offset);
    }
  }

  address = tvb_get_ntohl(tvb, offset);
  /*app specific socket*/
  if (address != 0){
    if (check_col(pinfo->cinfo, COL_INFO)){
      col_append_str(pinfo->cinfo, COL_INFO, "Application Specific Message");
    }
    return;
  }

  /*has sender?*/
  if (tvb_get_guint8(tvb, offset+4) != 0){
    has_sender = TRUE;
  }

  if (tree){
    proto_tree_add_item(freepastry_tree, hf_freepastry_msg_header_address, tvb, offset, 4, FALSE);
    proto_tree_add_boolean(freepastry_tree, hf_freepastry_msg_header_has_sender, tvb, offset+4, 1, has_sender);
    proto_tree_add_item(freepastry_tree, hf_freepastry_msg_header_priority, tvb, offset+5, 1, FALSE);
  }
  offset +=6;

  /*parse type*/
  type = tvb_get_ntohs(tvb, offset);
  if (check_col(pinfo->cinfo, COL_INFO)){
    col_append_fstr(pinfo->cinfo, COL_INFO, " %s",
			val_to_str(type, freepastry_liveness_message, "<Unknown type %d>"));
  }

  if (tree){
    proto_tree_add_item(freepastry_tree, hf_freepastry_liveness_msg_type, tvb, offset, 2, FALSE);
    offset += 2;

    /*parse nodehandle sender*/
    if (has_sender){
      offset = decode_nodehandle(tvb, freepastry_tree, offset, "Sender");
    }
    
    if (offset != -1) {
      switch (type) {
        case IP_ADDRESS_REQUEST_MSG:
        case IP_ADDRESS_RESPONSE_MSG:
        case PING_MSG:
        case PING_RESPONSE_MESSAGE:
          proto_tree_add_item(freepastry_tree, hf_freepastry_liveness_msg_sent_time, tvb, offset, 8, FALSE);
          break;
        case WRONG_EPOCH_MESSAGE:
          proto_tree_add_item(freepastry_tree, hf_freepastry_liveness_msg_sent_time, tvb, offset, 8, FALSE);
          offset += 8;
          offset = decode_epoch_inet_socket_address(tvb, tree, offset, "Incorrect");
          if (offset != -1){
            offset = decode_epoch_inet_socket_address(tvb, tree, offset, "Correct");
          }
          break;
        default:
          proto_tree_add_text(tree, tvb, offset, 1, "Unkown type");
      }
    }
  }
}

/*
 * Start: TCP FreePastry Core Message dissection.
 */

/**
 * Decode the common structure of a FreePastry TCP message.
 * TCP fragments are already reassembled (message is complete)
 * Such a function is needed because a FreePastry Message can be
 * Encapsulated into another one (e.g. into a Route Message).
 * This function can be called from this file or a 
 * packet-freepastry-core-vX.c file.
**/
void
decode_freepastry_tcp_msg_invariant(tvbuff_t *tvb, packet_info *pinfo, proto_tree *tree, gint offset, guint32 address)
{
  gboolean has_sender = FALSE;
  gint16  type          = 0;
  sub_message_info_t *sub_message_info = NULL;
  tvbuff_t *next_tvb = NULL;
  const gchar *type_string;

  /*has sender?*/
  if (tvb_get_guint8(tvb, offset) != 0){
    has_sender = TRUE;
  }

  if (tree){
    proto_tree_add_boolean(tree, hf_freepastry_msg_header_has_sender, tvb, offset, 1, has_sender);
    proto_tree_add_item(tree, hf_freepastry_msg_header_priority, tvb, offset+1, 1, FALSE);
  }
  offset += 2;

  /*parse type*/
  type = tvb_get_ntohs(tvb, offset);
  switch (address){
    case DIRECT_ACCESS:
      type_string = val_to_str(type, freepastry_direct_access_msg, "<Unknown type %d>");
      if (tree){
        proto_tree_add_item_hidden(tree, hf_freepastry_direct, tvb, offset, -1, FALSE);
        proto_tree_add_item(tree, hf_freepastry_direct_msg_type, tvb, offset, 2, FALSE);
      }
      break;
    case ROUTER:
      type_string = val_to_str(type, freepastry_router_msg, "<Unknown type %d>");
      if (tree){
        proto_tree_add_item_hidden(tree, hf_freepastry_router, tvb, offset, -1, FALSE);
        proto_tree_add_item(tree, hf_freepastry_router_msg_type, tvb, offset, 2, FALSE);
      }
      break;
    case JOIN_PROTOCOL:
      type_string = val_to_str(type, freepastry_join_msg, "<Unknown type %d>");
      if (tree){
        proto_tree_add_item_hidden(tree, hf_freepastry_join, tvb, offset, -1, FALSE);
        proto_tree_add_item(tree, hf_freepastry_join_msg_type, tvb, offset, 2, FALSE);
      }
      break;
    case LEAFSET_PROTOCOL:
      type_string = val_to_str(type, freepastry_leafset_msg, "<Unknown type %d>");
      if (tree){
        proto_tree_add_item_hidden(tree, hf_freepastry_leafset_proto, tvb, offset, -1, FALSE);
        proto_tree_add_item(tree, hf_freepastry_leafset_msg_type, tvb, offset, 2, FALSE);
      }
      break;
    case ROUTINGTABLE_PROTOCOL:
      type_string = val_to_str(type, freepastry_routingtable_msg, "<Unknown type %d>");
      if (tree){
        proto_tree_add_item_hidden(tree, hf_freepastry_routingtable, tvb, offset, -1, FALSE);
        proto_tree_add_item(tree, hf_freepastry_routingtable_msg_type, tvb, offset, 2, FALSE);
      }
      break;
    default:
      type_string = val_to_str(type, freepastry_commonapi_msg, "<Unknown address %d>");
      if (tree){
        proto_tree_add_item_hidden(tree, hf_freepastry_commonapi, tvb, offset, -1, FALSE);
        proto_tree_add_item(tree, hf_freepastry_commonapi_msg_type, tvb, offset, 2, FALSE);
      }
  }

  if (check_col(pinfo->cinfo, COL_INFO)){
    col_append_fstr(pinfo->cinfo, COL_INFO, " %s", type_string);
  }

  offset += 2;
  if (has_sender){
    if (tree) {
      offset = decode_nodehandle(tvb, tree, offset, "Sender");
    } else {
      offset = get_node_handle_len(tvb, offset);
    }
  }

  /*Call the FreePastry messages dissector*/
  /*We need to read protocol version*/
  if (tvb_reported_length_remaining(tvb, offset) >= 1){
    
    /*Save internal data for message dissector*/
    sub_message_info = ep_new(sub_message_info_t);
    sub_message_info->address = address;
    sub_message_info->type = type;
    pinfo->private_data = sub_message_info;

    next_tvb = tvb_new_subset(tvb, offset, -1, -1);
    //    dissector_try_port(subdissector_message_version_table, tvb_get_guint8(tvb, offset), next_tvb, pinfo, tree);
    dissector_try_port(subdissector_message_version_table, 0, next_tvb, pinfo, tree);
  }
}

/**
*   Compute the size of a FreePastry TCP Message. Needed by the desegmentation feature.
*   @return the size of the PDU.
**/
static guint get_freepastry_pdu_len(packet_info *pinfo _U_, tvbuff_t *tvb, int offset)
{
  /* That length doesn't include the length of the header field itself (4bytes);
   * add that in.
   */
  return (guint) (tvb_get_ntohl(tvb, offset) + 4);
}
 
/*
 * Dissect a TCP FreePastry message. Called by the desegmentation feature
 * TCP fragments are already reassembled (message is complete)
 */
static void
dissect_freepastry_pdu(tvbuff_t *tvb, packet_info *pinfo, proto_tree *tree)
{
  proto_item *ti =  NULL;
  proto_tree *freepastry_tree = NULL;
  gint offset = 0;
  guint32 address;

  if (check_col(pinfo->cinfo, COL_PROTOCOL)){
    col_set_str(pinfo->cinfo, COL_PROTOCOL, "FreePastry");
  }  
  if (check_col(pinfo->cinfo, COL_INFO)){
    col_clear (pinfo->cinfo, COL_INFO);
    col_add_fstr(pinfo->cinfo, COL_INFO, "%d > %d",
      pinfo->srcport, pinfo->destport);
  }
  
  address = tvb_get_ntohl(tvb, offset+4);
  ti = proto_tree_add_item(tree, proto_freepastry, tvb, 0, -1, FALSE);
  freepastry_tree = proto_item_add_subtree(ti, ett_freepastry);
  if (tree){
    proto_tree_add_item(freepastry_tree, hf_freepastry_msg_size, tvb, offset, 4, FALSE);
    proto_tree_add_item(freepastry_tree, hf_freepastry_msg_header_address, tvb, offset+4, 4, FALSE);
  }
  offset += 8;
  decode_freepastry_tcp_msg_invariant(tvb, pinfo, freepastry_tree, offset, address);
}

/**
*   Save FreePastry data for this conversation:
*     - ID of the packet related to FreePastry TCP stream header
*     - True if this session is an AppSocket
**/
static void
attach_data_to_conversation(conversation_t *conversation, guint32 id, gboolean is_app_stream)
{
  struct freepastry_tcp_stream_data *tcp_stream_data;
  
  /* Is there already some data attached to this conversation?*/
  tcp_stream_data = conversation_get_proto_data(conversation, proto_freepastry);
  if (!tcp_stream_data) {
    tcp_stream_data = se_alloc(sizeof(struct freepastry_tcp_stream_data));
    tcp_stream_data->id = id;
    tcp_stream_data->is_app_stream = is_app_stream;
    conversation_add_proto_data(conversation, proto_freepastry, tcp_stream_data);
  }
}

/**
*   Dissect a tvbuff that is supposed to contain a FreePastry TCP Message header.
*   Note: Message size is unknown and can be segmented
*   @return TRUE if the dissected tvb contained FreePastry data otherwise FALSE.
**/
static gboolean
dissect_freepastry_tcp_header(tvbuff_t *tvb, packet_info *pinfo, proto_tree *tree, conversation_t *conversation)
{
  guint quick_parse_offset = 0;
  gint epoch_inet_socket_address_len = 0;
  gboolean next_message_to_parse = FALSE;
  gboolean is_source_routed = FALSE;
  gboolean is_app_stream = FALSE;
  guint32 app_id;

  /*Check that the header message is complete*/
  if (tvb_length(tvb) < 16 ) {/*16 = minimal header message (no source route)*/
    /*ask for more bytes*/
    pinfo->desegment_offset = 0;
    pinfo->desegment_len = 16;
    return FALSE;
  }
  
  if (tvb_get_ntohl(tvb, quick_parse_offset) != PASTRY_MAGIC_NUMBER){
    /*It is not a FreePastry stream for sure*/
    return FALSE;
  }

  quick_parse_offset += 8;
  if (tvb_get_ntohl(tvb, quick_parse_offset) == HEADER_SOURCE_ROUTE){
    is_source_routed = TRUE;
    quick_parse_offset +=4;
    do {/*while there are hops (EISA)*/
      if (tvb_reported_length_remaining(tvb, quick_parse_offset) < 1){
        /*Require more bytes*/
        pinfo->desegment_offset = 0;
        pinfo->desegment_len = quick_parse_offset + 23;/*15+8 = minimal header with one more EISA*/
        return FALSE;
      }
      epoch_inet_socket_address_len = get_epoch_inet_socket_address_len(tvb, quick_parse_offset);
      if (tvb_reported_length_remaining(tvb, quick_parse_offset) < (epoch_inet_socket_address_len + 8)){
        /*Require more bytes*/
        pinfo->desegment_offset = 0;
        /*epoch_inet_socket_address + direct header + app ID*/
        pinfo->desegment_len = quick_parse_offset + epoch_inet_socket_address_len + 8;
        return FALSE;
      }
      quick_parse_offset += epoch_inet_socket_address_len;
    } while (tvb_get_ntohl(tvb, quick_parse_offset) == HEADER_DIRECT);
  }
  app_id = tvb_get_ntohl(tvb, quick_parse_offset + 4);
  if (app_id != 0x0){
      is_app_stream = TRUE;
  }
  quick_parse_offset += 8;/*Direct Header + App ID*/
  /*The header message is complete!*/
  if (conversation == NULL) { /* No conversation, create one */
    conversation = conversation_new(pinfo->fd->num, &pinfo->src, &pinfo->dst, pinfo->ptype,
      pinfo->srcport, pinfo->destport, 0);
  }
  attach_data_to_conversation(conversation, pinfo->fd->num, is_app_stream);
  
  /*Check if there are some data for the next message in the buffer*/
  if (tvb_length(tvb) != (quick_parse_offset + 1)) {
    next_message_to_parse = TRUE;
  }

  /*Here starts the real job*/
  if (check_col(pinfo->cinfo, COL_PROTOCOL)){
    col_set_str(pinfo->cinfo, COL_PROTOCOL, "FreePastry");
  }
  if(check_col(pinfo->cinfo,COL_INFO)){
    col_add_fstr(pinfo->cinfo, COL_INFO, "%d > %d New FreePastry socket",
      pinfo->srcport, pinfo->destport);
    if (is_source_routed){
      col_append_str(pinfo->cinfo, COL_INFO, " (source routed)");
    }
  }
  if (tree) {
    proto_item *ti =  NULL;
    proto_tree *freepastry_tree = NULL;
    gint offset = 0;

    ti = proto_tree_add_item(tree, proto_freepastry, tvb, 0, -1, FALSE);
    freepastry_tree = proto_item_add_subtree(ti, ett_freepastry);

    proto_tree_add_item(freepastry_tree, hf_freepastry_header_magic_number, tvb, offset, 4, FALSE);
    offset += 4;
    proto_tree_add_item(freepastry_tree, hf_freepastry_header_version_number, tvb, offset, 4, FALSE);
    offset += 4;
    /*is it a source route header or a direct header?*/
    if (tvb_get_ntohl(tvb, offset) == HEADER_SOURCE_ROUTE){
      proto_tree_add_item(freepastry_tree, hf_freepastry_header_source_route, tvb, offset, 4, FALSE);
      offset += 4;
      do {
        offset = decode_epoch_inet_socket_address(tvb, tree, offset, "Hops");
        if (offset == -1){
          /*It is a corrupted packet but it is actually a FP packet*/
          return TRUE;
        }
      } while (tvb_get_ntohl(tvb, offset) == HEADER_DIRECT);
    }
    proto_tree_add_item(freepastry_tree, hf_freepastry_header_header_direct, tvb, offset, 4, FALSE);
    offset += 4;
    proto_tree_add_uint(freepastry_tree, hf_freepastry_header_app_id, tvb, offset, 4, app_id);
    offset += 4;
  }
  
  /* Make the dissector for this conversation the non-heuristic
  FreePastry dissector. */
  conversation_set_dissector(conversation, freepastry_tcp_handle);
  
  /*TODO Test tvb+offset stuff*/
  /*if (next_message_to_parse) {
    g_warning("There is another message to parse!!!");
    tcp_dissect_pdus(tvb+offset, pinfo, tree, freepastry_desegment, 4, get_freepastry_pdu_len,
      dissect_freepastry_pdu);
  }*/

  return TRUE;
}

/**
*   Tag an AppSockets stream
**/
static void
dissect_freepastry_tcp_app(packet_info *pinfo)
{
  if (check_col(pinfo->cinfo, COL_PROTOCOL)){
    col_set_str(pinfo->cinfo, COL_PROTOCOL, "FreePastry");
  }

  if(check_col(pinfo->cinfo,COL_INFO)){
    col_add_fstr(pinfo->cinfo, COL_INFO, "%d > %d Application Socket",
      pinfo->srcport, pinfo->destport);
  }
}

/**
*   Dissect a tvbuff containing a FreePastry TCP Message
*   @return TRUE if the dissected tvb contained FreePastry data otherwise FALSE.
**/
static gboolean
dissect_freepastry_common_tcp(tvbuff_t *tvb, packet_info *pinfo, proto_tree *tree)
{
  gboolean result = FALSE;
  conversation_t *conversation;
  struct freepastry_tcp_stream_data *tcp_stream_data;

  conversation = find_conversation(pinfo->fd->num, &pinfo->src, &pinfo->dst, pinfo->ptype,
    pinfo->srcport, pinfo->destport, 0);

  if (conversation == NULL) {
    result = dissect_freepastry_tcp_header(tvb, pinfo, tree, conversation);
  } else {
    tcp_stream_data = conversation_get_proto_data(conversation, proto_freepastry);
    if (!tcp_stream_data) {
      /*Should not happen, data is attached just after conversation creation*/
      result = dissect_freepastry_tcp_header(tvb, pinfo, tree, conversation);
    } else if (tcp_stream_data->id == pinfo->fd->num){
      /* It is a stream header message */
      result = dissect_freepastry_tcp_header(tvb, pinfo, tree, conversation);
    } else if (tcp_stream_data->is_app_stream){
      /* It is an AppSocket */
      dissect_freepastry_tcp_app(pinfo);
      result = TRUE;
    } else {
      /* It is a core FreePastry message, activate desegmentation*/
      tcp_dissect_pdus(tvb, pinfo, tree, freepastry_desegment, 4, get_freepastry_pdu_len,
        dissect_freepastry_pdu);
      result = TRUE;
    }
  }
  return result;
}

/*
 * End: TCP FreePastry Core Message dissection.
 */

/**
* Dissect a tvbuff containing a FreePastry UDP Message
* (called because we registered a specific port number)
**/
static void
dissect_freepastry_port_udp(tvbuff_t *tvb, packet_info *pinfo, proto_tree *tree)
{
  dissect_freepastry_common_udp(tvb, pinfo, tree);
  return;
}

/**
* Dissect a tvbuff containing a FreePastry TCP Message
* (called because we registered a specific port number)
**/
static void
dissect_freepastry_port_tcp(tvbuff_t *tvb, packet_info *pinfo, proto_tree *tree)
{
  dissect_freepastry_common_tcp(tvb, pinfo, tree);
  return;
}

/**
*   Heuristically dissect a tvbuff containing a FreePastry UDP Message
*   @return TRUE if the dissected tvb contained FreePastry data otherwise FALSE.
**/
static gboolean 
dissect_freepastry_heur_udp(tvbuff_t * tvb, packet_info * pinfo, proto_tree * tree)
{
  /*Message is too small*/
	if (tvb_length(tvb) < 16 ) {
    return FALSE;
  }
  /*Is it a FreePastry Message?*/
  if (tvb_get_ntohl(tvb, 0) != PASTRY_MAGIC_NUMBER){
    return FALSE;
  }

  dissect_freepastry_common_udp(tvb, pinfo, tree);
  return TRUE;
}

/**
*   Heuristically dissect a tvbuff containing a FreePastry TCP Message
*   @return TRUE if the dissected tvb contained FreePastry data otherwise FALSE.
**/
static gboolean 
dissect_freepastry_heur_tcp(tvbuff_t * tvb, packet_info * pinfo, proto_tree * tree)
{
  return dissect_freepastry_common_tcp(tvb, pinfo, tree);
}


void
proto_register_freepastry(void)
{
  
  static hf_register_info hf[] = {
    { &hf_freepastry_direct,
	  { "Direct messages", "freepastry.direct", 
    FT_NONE, BASE_NONE, NULL, 0x0, 
    "Show only direct access traffic", HFILL }},
    { &hf_freepastry_router,
	  { "Router messages", "freepastry.router", 
    FT_NONE, BASE_NONE, NULL, 0x0, 
    "Show only router traffic", HFILL }},
    { &hf_freepastry_commonapi,
	  { "Common API messages", "freepastry.commonapi", 
    FT_NONE, BASE_NONE, NULL, 0x0, 
    "Show only common API traffic", HFILL }},
    { &hf_freepastry_join,
	  { "Join protocol", "freepastry.join", 
    FT_NONE, BASE_NONE, NULL, 0x0, 
    "Show only join protocol traffic", HFILL }},
    { &hf_freepastry_routingtable,
	  { "Routing table maintenance protocol", "freepastry.routingtable", 
    FT_NONE, BASE_NONE, NULL, 0x0, 
    "Show only routing table maintenance protocol traffic", HFILL }},
    { &hf_freepastry_leafset_proto,
	  { "Leafset maintenance protocol", "freepastry.leafset_proto", 
    FT_NONE, BASE_NONE, NULL, 0x0, 
    "Show only leafset maintenance protocol traffic", HFILL }},
    { &hf_freepastry_liveness,
	  { "Liveness messages", "freepastry.liveness", 
    FT_NONE, BASE_NONE, NULL, 0x0, 
    "Show only liveness traffic", HFILL }},
    { &hf_freepastry_msg_size,
    { "Message size",	"freepastry.msg_size",
    FT_UINT32, BASE_DEC, NULL, 0x0,
    "Message size", HFILL }},
    { &hf_freepastry_eisa_num_add,
    { "Number of addresses",	"freepastry.eisa.num",
    FT_UINT8, BASE_DEC, NULL, 0x0,
    "Number of address in an EpochInetSocketAddress", HFILL }},
    { &hf_freepastry_eisa_ip,
    { "IP address",	"freepastry.eisa.ip",
    FT_IPv4, BASE_DEC, NULL, 0x0,
    "IPv4 address in an EpochInetSocketAddress", HFILL }},
    { &hf_freepastry_eisa_port,
    { "Port number",	"freepastry.eisa.port",
    FT_UINT16, BASE_DEC, NULL, 0x0,
    "Port number in an EpochInetSocketAddress", HFILL }},
    { &hf_freepastry_eisa_epoch,
    { "Epoch",	"freepastry.header.eisa.epoch",
    FT_UINT64, BASE_DEC, NULL, 0x0,
    "Epoch in an EpochInetSocketAddress", HFILL }},
    { &hf_freepastry_id_type,
    { "ID type",	"freepastry.id_type",
    FT_INT16, BASE_DEC, VALS(freepastry_id_type), 0x0,
    "ID type", HFILL }},
    { &hf_freepastry_id_value,
    { "ID value", "freepastry.id",
    FT_STRING, BASE_NONE, NULL, 0x0,
    "ID value", HFILL }},
    { &hf_freepastry_ringid,
    { "ID value", "freepastry.ringid",
    FT_STRING, BASE_NONE, NULL, 0x0,
    "ID value for ring ID", HFILL }},
    { &hf_freepastry_gcid_expiration,
    { "Expiration",	"freepastry.gcid.expiration",
    FT_UINT64, BASE_DEC, NULL, 0x0,
    "Expiration for GCID", HFILL }},
    { &hf_freepastry_versionkey_version,
    { "Version",	"freepastry.versionkey.version",
    FT_UINT64, BASE_DEC, NULL, 0x0,
    "Version number for VersionKey", HFILL }},
    { &hf_freepastry_fragmentkey_id,
    { "Fragment key ID",	"freepastry.fragmentkey.id",
    FT_UINT32, BASE_DEC, NULL, 0x0,
    "Fragment key ID", HFILL }},
    { &hf_freepastry_rs_capacity,
    { "Capacity",	"freepastry.routeset.capacity",
    FT_UINT8, BASE_DEC, NULL, 0x0,
    "Capacity of a RouteSet", HFILL }},
    { &hf_freepastry_rs_size,
    { "RouteSet size",	"freepastry.routeset.size",
    FT_UINT8, BASE_DEC, NULL, 0x0,
    "Size of a RouteSet", HFILL }},
    { &hf_freepastry_rs_closest,
    { "Closest",	"freepastry.routeset.closest",
    FT_UINT8, BASE_DEC, NULL, 0x0,
    "Index of the closest node in a RouteSet", HFILL }},
    { &hf_freepastry_ns_size,
    { "NodeHandleSet size",	"freepastry.nodehandleset.size",
    FT_UINT8, BASE_DEC, NULL, 0x0,
    "Size of a NodeSet", HFILL }},
    { &hf_freepastry_mns_type,
    { "Internal Multiring NodeHandleSet type",	"freepastry.multiringnodehandleset.type",
    FT_UINT8, BASE_DEC, NULL, 0x0,
    "Type for a Multiring NodeHandleSet", HFILL }},
    { &hf_freepastry_ls_size,
    { "Leafset size",	"freepastry.leafset.size",
    FT_UINT8, BASE_DEC, NULL, 0x0,
    "Size of a leafset", HFILL }},
    { &hf_freepastry_ls_num_unique_handle,
    { "Number of unique handles",	"freepastry.leafset.num_unique",
    FT_UINT8, BASE_DEC, NULL, 0x0,
    "Number of unique handles in a leafset", HFILL }},
    { &hf_freepastry_ls_cw_size,
    { "Clockwise size",	"freepastry.leafset.cw_size",
    FT_UINT8, BASE_DEC, NULL, 0x0,
    "Number of NodeHandle inside a clockwise SimilarSet", HFILL }},
    { &hf_freepastry_ls_ccw_size,
    { "Counter clockwise size",	"freepastry.leafset.ccw_size",
    FT_UINT8, BASE_DEC, NULL, 0x0,
    "Number of NodeHandle inside a counter clockwise SimilarSet", HFILL }},
    { &hf_freepastry_ls_handle_index,
    { "Index",	"freepastry.leafset.hdl_index",
    FT_UINT8, BASE_DEC, NULL, 0x0,
    "", HFILL }},
    { &hf_freepastry_header_magic_number,
    { "Magic number",		"freepastry.header.magic_number",
    FT_UINT32, BASE_HEX, NULL, 0x0,
    "Magic Number", HFILL }},
    { &hf_freepastry_header_source_route,
    { "Source Route Header",		"freepastry.header.sourceroute",
    FT_UINT32, BASE_HEX, NULL, 0x0,
    "Source Route Header", HFILL }},
    { &hf_freepastry_header_version_number,
    { "Version number",		"freepastry.header.version",
    FT_UINT32, BASE_DEC, NULL, 0x0,
    "Protocol version number", HFILL }},
    { &hf_freepastry_header_current_hop,
    { "Hop counter", "freepastry.header.hop_counter",
    FT_UINT8, BASE_DEC, NULL, 0x0,
    "Hop counter", HFILL }},
    { &hf_freepastry_header_num_hop,
    { "Number of hops",	"freepastry.header.num_hop",
    FT_UINT8, BASE_DEC, NULL, 0x0,
    "Number of hops", HFILL }},
    { &hf_freepastry_header_source_route_len,
    { "Source route length",	"freepastry.header.source_route_len",
    FT_UINT16, BASE_DEC, NULL, 0x0,
    "Size of the virtual link", HFILL }},
    { &hf_freepastry_header_header_direct,
    { "Header direct",	"freepastry.header.header_direct",
    FT_UINT32, BASE_HEX, NULL, 0x0,
    "Header direct", HFILL }},
    { &hf_freepastry_header_app_id,
    { "Application ID",	"freepastry.header.app_id",
    FT_UINT32, BASE_DEC, NULL, 0x0,
    "Application ID", HFILL }},
    { &hf_freepastry_msg_header_address,
    { "Address",	"freepastry.msg_header.address",
    FT_UINT32, BASE_HEX, VALS(freepastry_address), 0x0,
    "The application in the overlay that the message goes to", HFILL }},
    { &hf_freepastry_msg_header_has_sender,
    { "Has sender",	"freepastry.msg_header.has_sender",
    FT_BOOLEAN, 8, NULL, 0x0,
    "True if the sender of the message is specified", HFILL }},
    { &hf_freepastry_msg_header_priority,
    { "Priority",	"freepastry.msg_header.priority",
    FT_INT8, BASE_DEC, VALS(freepastry_priority), 0x0,
    "Priority", HFILL }},
    { &hf_freepastry_liveness_msg_type,
    { "Message type",	"freepastry.liveness.type",
    FT_INT16, BASE_DEC, VALS(freepastry_liveness_message), 0x0,
    "Message type for liveness messages", HFILL }},
    { &hf_freepastry_liveness_msg_sent_time,
    { "Sent",	"freepastry.liveness.send_time",
    FT_UINT64, BASE_DEC, NULL, 0x0,
    "When a Ping message has been sent", HFILL }},
    { &hf_freepastry_router_msg_type,
    { "Message type",	"freepastry.router.type",
    FT_INT16, BASE_DEC, VALS(freepastry_router_msg), 0x0,
    "Message type for router messages", HFILL }},
    { &hf_freepastry_join_msg_type,
    { "Message type",	"freepastry.join.type",
    FT_INT16, BASE_DEC, VALS(freepastry_join_msg), 0x0,
    "Message type for join protocol messages", HFILL }},
    { &hf_freepastry_leafset_msg_type,
    { "Message type",	"freepastry.leafset.type",
    FT_INT16, BASE_DEC, VALS(freepastry_leafset_msg), 0x0,
    "Message type for leafset maintenance protocol messages", HFILL }},
    { &hf_freepastry_routingtable_msg_type,
    { "Message type",	"freepastry.routingtable.type",
    FT_INT16, BASE_DEC, VALS(freepastry_routingtable_msg), 0x0,
    "Message type for routing table maintenance protocol messages", HFILL }},
    { &hf_freepastry_direct_msg_type,
    { "Message type",	"freepastry.direct.type",
    FT_INT16, BASE_DEC, VALS(freepastry_direct_access_msg), 0x0,
    "Message type for direct access messages", HFILL }},
    { &hf_freepastry_commonapi_msg_type,
    { "Message type",	"freepastry.commonapi.type",
    FT_INT16, BASE_DEC, VALS(freepastry_commonapi_msg), 0x0,
    "Message type for common API messages", HFILL }},
    { &hf_freepastry_direct_msg_version,
    { "Version",	"freepastry.direct.msg_version",
    FT_UINT8, BASE_DEC, NULL, 0x0,
    "Version for direct access messages", HFILL }},
    { &hf_freepastry_router_msg_version,
    { "Version",	"freepastry.router_proto.msg_version",
    FT_UINT8, BASE_DEC, NULL, 0x0,
    "Version for routing table maintenance messages", HFILL }},
    { &hf_freepastry_commonapi_msg_version,
    { "Version",	"freepastry.commonapi.msg_version",
    FT_UINT8, BASE_DEC, NULL, 0x0,
    "Version for common API messages", HFILL }},
    { &hf_freepastry_join_msg_version,
    { "Version",	"freepastry.join.msg_version",
    FT_UINT8, BASE_DEC, NULL, 0x0,
    "Version for join protocol messages", HFILL }},
    { &hf_freepastry_routingtable_msg_version,
    { "Version",	"freepastry.routingtable.msg_version",
    FT_UINT8, BASE_DEC, NULL, 0x0,
    "Version for routing table maintenance messages", HFILL }},
    { &hf_freepastry_leafset_proto_msg_version,
    { "Version",	"freepastry.leafset_proto.msg_version",
    FT_UINT8, BASE_DEC, NULL, 0x0,
    "Version for leafset maintenance messages", HFILL }}
  };
  
  /* Setup protocol subtree array */
  static gint *ett[] = {
    &ett_freepastry,
    &ett_freepastry_eisa,
    &ett_freepastry_isa,
    &ett_freepastry_nh,
    &ett_freepastry_rs,
    &ett_freepastry_ns,
    &ett_freepastry_ls,
    &ett_freepastry_ls_cw,
    &ett_freepastry_ls_ccw
  };

  module_t *freepastry_module;	

  if (proto_freepastry == -1) {
    proto_freepastry = proto_register_protocol (
      "FreePastry Binary Protocol",	/* name */
      "FreePastry",		              /* short name */
      "freepastry"		              /* abbrev */
      );
  }
  freepastry_module	= prefs_register_protocol(proto_freepastry, NULL);
  prefs_register_bool_preference(freepastry_module, "desegment_freepastry_messages",
    		"Reassemble FreePastry messages spanning multiple TCP segments",
    		"Whether the FreePastry dissector should desegment all messages spanning multiple TCP segments",
    		&freepastry_desegment);
  proto_register_field_array(proto_freepastry, hf, array_length(hf));
  proto_register_subtree_array(ett, array_length(ett));

  subdissector_message_version_table = register_dissector_table("freepastry.msg", "FreePastry Message", 
    FT_UINT8, BASE_DEC);
}


void
proto_reg_handoff_freepastry(void)
{
  static int Initialized=FALSE;

  if (!Initialized) {
    freepastry_udp_handle = create_dissector_handle(dissect_freepastry_port_udp, proto_freepastry);
    freepastry_tcp_handle = create_dissector_handle(dissect_freepastry_port_tcp, proto_freepastry);
    dissector_add("udp.port", UDP_PORT_FREEPASTRY, freepastry_udp_handle);
    dissector_add("tcp.port", TCP_PORT_FREEPASTRY, freepastry_tcp_handle);
    heur_dissector_add("udp", dissect_freepastry_heur_udp, proto_freepastry);
    heur_dissector_add("tcp", dissect_freepastry_heur_tcp, proto_freepastry);
  }
}
