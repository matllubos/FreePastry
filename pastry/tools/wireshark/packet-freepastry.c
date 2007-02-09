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

/* forward references */
static void decode_freepastry_tcp_msg_invariant(tvbuff_t *tvb, packet_info *pinfo, proto_tree *tree, gint offset, guint32 address);

/* desegmentation of FreePastry over TCP */
static gboolean freepastry_desegment = TRUE;

static int proto_freepastry = -1;
static int hf_freepastry_header_magic_number = -1;
static int hf_freepastry_header_version_number = -1;
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
static int hf_freepastry_direct_nodeid_resp_epoch = -1;
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
static int hf_freepastry_direct_access_msg_type = -1;
static int hf_freepastry_msg_version = -1;
static int hf_freepastry_router_sub_address = -1;
static int hf_freepastry_router_target = -1;
static int hf_freepastry_direct_routerow_row = -1;
static int hf_freepastry_direct_routerow_numroutesets = -1;
static int hf_freepastry_direct_routerow_notnull = -1;
static int hf_freepastry_direct_sourceroute_numhops = -1;
static int hf_freepastry_join_consistent_join_is_request  = -1;
static int hf_freepastry_join_consistent_join_num_failed  = -1;
static int hf_freepastry_join_join_rtbasebitlength  = -1;
static int hf_freepastry_join_join_has_join_handle  = -1;
static int hf_freepastry_join_join_last_row  = -1;
static int hf_freepastry_join_join_has_row  = -1;
static int hf_freepastry_join_join_has_col  = -1;
static int hf_freepastry_join_join_has_leafset  = -1;
static int hf_freepastry_leafset_request_leafset_timestamp  = -1;
static int hf_freepastry_leafset_broadcast_leafset_type  = -1;
static int hf_freepastry_leafset_broadcast_leafset_timestamp  = -1;
static int hf_freepastry_routingtable_request_routerow_row  = -1;
static int hf_freepastry_routingtable_broadcast_routerow_num_row  = -1;
static int hf_freepastry_routingtable_broadcast_routerow_notnull  = -1;
static int hf_freepastry_commonapi_pastry_endpoint_version = -1;
static int hf_freepastry_commonapi_pastry_endpoint_priority = -1;

static gint ett_freepastry = -1;
static gint ett_freepastry_eisa = -1;
static gint ett_freepastry_isa = -1;
static gint ett_freepastry_nh = -1;
static gint ett_freepastry_ns = -1;
static gint ett_freepastry_rs = -1;
static gint ett_freepastry_ls = -1;
static gint ett_freepastry_ls_cw = -1;
static gint ett_freepastry_ls_ccw = -1;
static gint ett_freepastry_sr = -1;

static dissector_handle_t freepastry_udp_handle; 
static dissector_handle_t freepastry_tcp_handle;

static dissector_table_t subdissector_table;

/*
 * State information stored with a conversation.
 */
struct freepastry_tcp_stream_data {
  guint32   id; /*A unique ID (the frame number)that identify the header*/
  gboolean is_app_stream; /* Is it a normal FreePastry Socket? */
};

/* Address mapping */
static const value_string freepastry_address[] = {
  { DIRECT_ACCESS, 	"Direct Access" },
  { ROUTER, 	        "Router" },
  { JOIN_PROTOCOL, 	"Join Protocol" },
  { LEAF_PROTOCOL, 	"Leafset Protocol" },
  { ROUTE_PROTOCOL,  	"Route Protocol" }, 
  { 0,       		NULL }
};

/*Messages for "Direct access" module*/
static const value_string freepastry_direct_access_msg[] = {
  { SOURCE_ROUTE, 	        "Source Route" },
  { LEAFSET_REQUEST_MSG, 	  "LeafSet Request" },
  { LEAFSET_RESPONSE_MSG,   "LeafSet Response" },
  { NODE_ID_REQUEST_MSG, 	  "Node ID Request" },
  { NODE_ID_RESPONSE_MSG,   "Node ID Response" }, 
  { ROUTE_ROW_REQUEST_MSG,  "Route Row Request" },
  { ROUTE_ROW_RESPONSE_MSG, "Route Row Response" }, 
  { ROUTES_REQUEST_MSG, 	  "Routes Request" },
  { ROUTES_RESPONSE_MSG,    "Routes Response" },
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
  { PING_MSG, 	              "Ping" },
  { PING_RESPONSE_MESSAGE, 	  "Ping Response" },
  { WRONG_EPOCH_MESSAGE,  	  "Wrong Epoch" },
  { 0, NULL }
};

/*
 * Start: Common FreePastry Objects dissection.
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
  gint remaining;
  int i;

  /*Get the number of (IPv4 address, port number) couple*/
  nb_addr = tvb_get_guint8(tvb, offset);
  offset++;

  remaining = tvb_reported_length_remaining(tvb, offset);
  if (remaining < (8+nb_addr*6)){
    proto_tree_add_text(parent_tree, tvb, offset, remaining, "Too short EISA attribute!");
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
*   Decode a "Source Route" object.
*   @return the new offset or -1 on error.
**/
gint
decode_sourceroute(tvbuff_t *tvb, proto_tree *parent_tree, gint offset, gchar *attribute_name)
{
  proto_item *ti = NULL;
  proto_tree *sourceroute_tree = NULL; 
  guint32 i;
  guint32 sourceroute_size = tvb_get_ntohl(tvb, offset);

  ti = proto_tree_add_text(parent_tree, tvb, offset, 1, attribute_name);
  sourceroute_tree = proto_item_add_subtree(ti, ett_freepastry_sr);
  proto_tree_add_uint(sourceroute_tree, hf_freepastry_direct_sourceroute_numhops, tvb, offset, 4, sourceroute_size);
  offset += 4;
  
  for (i=0; i < sourceroute_size; ++i){
    offset = decode_epoch_inet_socket_address(tvb, sourceroute_tree, offset, "Hop");
    if (offset == -1){
      return -1;
    }
  }
  proto_item_set_end(ti, tvb, offset);
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

gint
print_id_into_col_info(tvbuff_t *tvb, packet_info *pinfo, gint offset, gchar* info_string){
  gint16 type;
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
  return ep_strdup_printf(" <0x%02X%02X%02X..>", (id >> 24) & 0xff,
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
    gint remaining = tvb_reported_length_remaining(tvb, offset);
    /*20 bytes = ID Length*/
    if (remaining >= 20) {
      gchar* short_id = get_id(tvb, offset);
      
      proto_item_append_text(ti, short_id);
      proto_tree_add_string(node_handle_tree, hf_freepastry_id_value, tvb, offset, 20, get_id_full(tvb, offset));
      offset += 20;
      proto_item_set_end(ti, tvb, offset);
    } else {
      proto_tree_add_text(node_handle_tree, tvb, offset, remaining, "Too short attribute!");
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
  int i;

  ti = proto_tree_add_text(parent_tree, tvb, offset, 1, attribute_name);
  leafset_tree = proto_item_add_subtree(ti, ett_freepastry_ls);
  /*The total capacity of the leafset (not including the base handle)*/
  proto_tree_add_item(leafset_tree, hf_freepastry_ls_size, tvb, offset, 1, FALSE);
  offset++;
  /*The number of NodeHandles to read*/
  num_unique_handle = tvb_get_guint8(tvb, offset);
  proto_tree_add_uint(leafset_tree, hf_freepastry_ls_num_unique_handle,
    tvb, offset, 1, num_unique_handle);
  offset++;
  /*The number of element of the clockwise similar set*/
  num_cw_size = tvb_get_guint8(tvb, offset);
  proto_tree_add_uint(leafset_tree, hf_freepastry_ls_cw_size,
    tvb, offset, 1, num_cw_size);
  offset++;  
  /*The number of element of the counter clockwise similar set*/
  num_ccw_size = tvb_get_guint8(tvb, offset);
  proto_tree_add_uint(leafset_tree, hf_freepastry_ls_ccw_size,
    tvb, offset, 1, num_ccw_size);
  offset++;
  /*The base NodeHandle*/
  offset = decode_nodehandle(tvb, leafset_tree, offset, "Base Node Handle");
  /*The unique handles*/
  for (i = 0; i < num_unique_handle; ++i){
    if (offset == -1) {
      return -1;
    }
    offset = decode_nodehandle(tvb, leafset_tree, offset, ep_strdup_printf("Node Handle #%d", i+1));
  }
  /*The cw addresses*/
  ti_cw = proto_tree_add_text(leafset_tree, tvb, offset, num_cw_size, "Clockwise Similar Set");
  cw_tree = proto_item_add_subtree(ti_cw, ett_freepastry_ls_cw);
  for (i = 0; i < num_cw_size; ++i){
    proto_tree_add_item(cw_tree, hf_freepastry_ls_handle_index, tvb, offset, 1, FALSE);
    offset++;
  }
  proto_item_set_end(ti_cw, tvb, offset);
  /*The ccw addresses*/
  ti_ccw = proto_tree_add_text(leafset_tree, tvb, offset, num_ccw_size, "Counter Clockwise Similar Set");
  ccw_tree = proto_item_add_subtree(ti_ccw, ett_freepastry_ls_ccw);
  for (i = 0; i < num_ccw_size; ++i){
    proto_tree_add_item(ccw_tree, hf_freepastry_ls_handle_index, tvb, offset, 1, FALSE);
    offset++;
  }
  proto_item_set_end(ti_ccw, tvb, offset);
  proto_item_set_end(ti, tvb, offset);
  return offset;
}
/*
 * End: Common FreePastry Objects dissection.
 */


/*
 * Start: TCP FreePastry Core Message dissection.
 */

/*Direct Acces Messages*/
static void
decode_msg_leafset_request(tvbuff_t *tvb, packet_info *pinfo _U_, proto_tree *tree,
                           gint offset, guint16 short_address _U_) {
  if (tree){
    proto_tree_add_item(tree, hf_freepastry_msg_version, tvb, offset, 1, FALSE);
  }
}

static void
decode_msg_leafset_response(tvbuff_t *tvb, packet_info *pinfo _U_, proto_tree *tree,
                            gint offset, guint16 short_address _U_) {
  if (tree){
    proto_tree_add_item(tree, hf_freepastry_msg_version, tvb, offset, 1, FALSE);
    offset = decode_leafset(tvb, tree, offset + 1, "Leafset");
  }
}

static void
decode_msg_id_request(tvbuff_t *tvb, packet_info *pinfo _U_, proto_tree *tree,
                      gint offset, guint16 short_address _U_) {
  if (tree){
    proto_tree_add_item(tree, hf_freepastry_msg_version, tvb, offset, 1, FALSE);
  }
}

static void
decode_msg_id_response(tvbuff_t *tvb, packet_info *pinfo, proto_tree *tree,
                       gint offset, guint16 short_address _U_) {
  if (tvb_reported_length_remaining(tvb, offset) < 29){
    proto_tree_add_text(tree, tvb, offset, -1, "Malformed message!");
    return;
  }

  if (check_col(pinfo->cinfo, COL_INFO)){
    col_append_str(pinfo->cinfo, COL_INFO, get_id(tvb, offset + 1));
  }

  if (tree){
    proto_tree_add_item(tree, hf_freepastry_msg_version, tvb, offset, 1, FALSE);
    offset++;
    proto_tree_add_string(tree, hf_freepastry_id_value, tvb, offset, 20, get_id_full(tvb, offset));
    proto_tree_add_item(tree, hf_freepastry_direct_nodeid_resp_epoch, tvb, offset+20, 8, FALSE);
  }
}

static void
decode_msg_row_request(tvbuff_t *tvb, packet_info *pinfo, proto_tree *tree,
                       gint offset, guint16 short_address _U_) {
  guint32 requested_row = tvb_get_ntohl(tvb, offset +1);
  if (check_col(pinfo->cinfo, COL_INFO)){
    col_append_fstr(pinfo->cinfo, COL_INFO, " %d", requested_row);
  }
  if (tree){
    proto_tree_add_item(tree, hf_freepastry_msg_version, tvb, offset, 1, FALSE);
    proto_tree_add_uint(tree, hf_freepastry_direct_routerow_row, tvb, offset+1, 4, requested_row);
  }
}

static void
decode_msg_row_response(tvbuff_t *tvb, packet_info *pinfo, proto_tree *tree,
                        gint offset, guint16 short_address _U_) {
  guint32 num_routeset = tvb_get_ntohl(tvb, offset +1);
  if (check_col(pinfo->cinfo, COL_INFO)){
    col_append_fstr(pinfo->cinfo, COL_INFO, " (%d Route Sets)", num_routeset);
  }
  if (tree)
  {
    guint32 i;
    proto_tree_add_item(tree, hf_freepastry_msg_version, tvb, offset, 1, FALSE);
    proto_tree_add_uint(tree, hf_freepastry_direct_routerow_numroutesets, tvb, offset+1, 4, num_routeset);
    offset += 5;
    for (i = 0; i < num_routeset; ++i){
      gboolean not_null = FALSE;
      /*has sender?*/
      if (tvb_get_guint8(tvb, offset) != 0){
        not_null = TRUE;
      }
      proto_tree_add_boolean(tree, hf_freepastry_direct_routerow_notnull, tvb, offset, 1, not_null);
      offset++;
      if (not_null){
        offset = decode_routeset(tvb, tree, offset, ep_strdup_printf("RouteSet for col 0x%X", i));
        if (offset == -1){
          return;
        }
      }
    }
  }
}

static void
decode_msg_routes_request(tvbuff_t *tvb, packet_info *pinfo _U_, proto_tree *tree,
                          gint offset, guint16 short_address _U_) {
  if (tree){
    proto_tree_add_item(tree, hf_freepastry_msg_version, tvb, offset, 1, FALSE);
  }
}

static void
decode_msg_routes_response(tvbuff_t *tvb, packet_info *pinfo _U_, proto_tree *tree,
                           gint offset, guint16 short_address _U_) {
  if (tree){
    proto_tree_add_item(tree, hf_freepastry_msg_version, tvb, offset, 1, FALSE);
    decode_sourceroute(tvb, tree, offset + 1, "Source Route");
  }
}

static void
decode_msg_source_route(tvbuff_t *tvb, packet_info *pinfo _U_, proto_tree *tree,
                        gint offset, guint16 short_address _U_) {
  if (tree){
    proto_tree_add_item(tree, hf_freepastry_msg_version, tvb, offset, 1, FALSE);
    decode_sourceroute(tvb, tree, offset + 1, "Source Route");
  }
}

/* Join Messages */

static void
decode_msg_join_request(tvbuff_t *tvb, packet_info *pinfo _U_, proto_tree *tree,
                        gint offset, guint16 short_address _U_) {
  if (tree){
    gboolean has_join_handle = FALSE;
    gboolean has_leafset = FALSE;
    guint8 rt_base_bit_length;
    guint8 num_row;
    guint8 num_col;
    guint8 i;
    guint8 j;

    rt_base_bit_length = tvb_get_guint8(tvb, offset + 1);
    proto_tree_add_item(tree, hf_freepastry_msg_version, tvb, offset, 1, FALSE);
    proto_tree_add_item(tree, hf_freepastry_join_join_rtbasebitlength, tvb, offset + 1, 1, rt_base_bit_length);
    offset += 2;
    offset = decode_nodehandle(tvb, tree, offset, "Node Handle");
    if (offset == -1) {
      return;
    }
    if (tvb_get_guint8(tvb, offset) != 0){
      has_join_handle = TRUE;
    }
    proto_tree_add_boolean(tree, hf_freepastry_join_join_has_join_handle, tvb, offset, 1, has_join_handle);
    offset++;
    if (has_join_handle) {
      offset = decode_nodehandle(tvb, tree, offset, "Join Handle");
    }
    if (offset == -1) {
      return;
    }
    proto_tree_add_item(tree, hf_freepastry_join_join_last_row, tvb, offset, 2, FALSE);
    offset += 2;
    if (rt_base_bit_length) {
      num_row = 160/rt_base_bit_length;
    } else {/*division by zero*/
      return;
    }
    num_col = 1 << rt_base_bit_length;
    for (i = 0; i < num_row; ++i){
      gboolean has_row = FALSE;
      if (tvb_get_guint8(tvb, offset) != 0){
        has_row = TRUE;
      }
      proto_tree_add_boolean(tree, hf_freepastry_join_join_has_row, tvb, offset, 1, has_row);
      offset++;
      if (has_row) {
        for (j = 0; j < num_col; ++j){
          gboolean has_col = FALSE;
          if (tvb_get_guint8(tvb, offset) != 0){
            has_col = TRUE;
          }
          proto_tree_add_boolean(tree, hf_freepastry_join_join_has_col, tvb, offset, 1, has_col);
          offset++;
          if (has_col) {
            offset = decode_routeset(tvb, tree, offset, ep_strdup_printf("RouteSet (%d, 0x%X)", i+1, j));
            if (offset == -1){
              return;
            }
          }
        }/*end for each col*/
      }/*end has row entry*/
    }/*end for each row*/

    if (tvb_get_guint8(tvb, offset) != 0){
      has_leafset = TRUE;
    }
    proto_tree_add_boolean(tree, hf_freepastry_join_join_has_leafset, tvb, offset, 1, has_leafset);
    if (has_leafset) {
      offset = decode_leafset(tvb, tree, offset + 1, "LeafSet");
    }
  }
}

static void
decode_msg_consistent_join(tvbuff_t *tvb, packet_info *pinfo _U_, proto_tree *tree,
                           gint offset, guint16 short_address _U_) {
  if (tree){
    guint32 num_failed;
    guint32 i;

    proto_tree_add_item(tree, hf_freepastry_msg_version, tvb, offset, 1, FALSE);
    offset = decode_leafset(tvb, tree, offset + 1, "Leafset");
    if (offset == -1){
      return;
    }
    proto_tree_add_boolean(tree, hf_freepastry_join_consistent_join_is_request, tvb, offset, 1, FALSE);
    num_failed = tvb_get_ntohl(tvb, offset + 1);
    proto_tree_add_uint(tree, hf_freepastry_join_consistent_join_num_failed, tvb, offset+1, 4, num_failed);
    offset += 5;
    for (i = 0; i < num_failed; ++i){
      if (offset == -1) {
        return;
      }
      offset = decode_nodehandle(tvb, tree, offset, "Failed Node Handle");
    }
  }
}

/* Leafset Maintenance Messages*/

static void
decode_msg_request_leafset(tvbuff_t *tvb, packet_info *pinfo _U_, proto_tree *tree,
                           gint offset, guint16 short_address _U_) {
  if (tree){
    proto_tree_add_item(tree, hf_freepastry_msg_version, tvb, offset, 1, FALSE);
    proto_tree_add_item(tree, hf_freepastry_leafset_request_leafset_timestamp, tvb, offset + 1, 8, FALSE);
  }
}

static void
decode_msg_broadcast_leafset(tvbuff_t *tvb, packet_info *pinfo _U_, proto_tree *tree,
                             gint offset, guint16 short_address _U_) {
  if (tree){
    proto_tree_add_item(tree, hf_freepastry_msg_version, tvb, offset, 1, FALSE);
    offset = decode_nodehandle(tvb, tree, offset + 1, "From Handle");
    if (offset == -1) {
      return;
    }
    offset = decode_leafset(tvb, tree, offset, "LeafSet");
    if (offset == -1) {
      return;
    }
    proto_tree_add_item(tree, hf_freepastry_leafset_broadcast_leafset_type, tvb, offset, 1, FALSE);
    proto_tree_add_item(tree, hf_freepastry_leafset_broadcast_leafset_timestamp, tvb, offset + 1, 8, FALSE);
  }
}

/* Routing Table Maintenance Messages*/

static void
decode_msg_request_route_row(tvbuff_t *tvb, packet_info *pinfo, proto_tree *tree,
                             gint offset, guint16 short_address _U_) {
  guint32 requested_row = tvb_get_ntohl(tvb, offset +1);
  if (check_col(pinfo->cinfo, COL_INFO)){
    col_append_fstr(pinfo->cinfo, COL_INFO, " %d", requested_row);
  }
  if (tree){
    proto_tree_add_item(tree, hf_freepastry_msg_version, tvb, offset, 1, FALSE);
    proto_tree_add_uint(tree, hf_freepastry_routingtable_request_routerow_row, tvb, offset+1, 4, requested_row);
  }
}

static void
decode_msg_route_row(tvbuff_t *tvb, packet_info *pinfo, proto_tree *tree,
                     gint offset, guint16 short_address _U_) {
  guint8 num_rows;
  if (tree)
  {
    proto_tree_add_item(tree, hf_freepastry_msg_version, tvb, offset, 1, FALSE);
    offset = decode_nodehandle(tvb, tree, offset + 1, "From Handle");
  } else {
    offset = get_node_handle_len(tvb, offset + 1);
  }
  
  if (offset == -1) {
    return;
  }

  num_rows = tvb_get_guint8(tvb, offset);
  if (check_col(pinfo->cinfo, COL_INFO)){
    col_append_fstr(pinfo->cinfo, COL_INFO, " (%d Rows)", num_rows);
  }

  if (tree)
  {
    guint32 i;
    proto_tree_add_uint(tree, hf_freepastry_routingtable_broadcast_routerow_num_row, tvb, offset, 1, num_rows);
    offset++;
    for (i = 0; i < num_rows; ++i){
      gboolean not_null = FALSE;
      /*row is not null?*/
      if (tvb_get_guint8(tvb, offset) != 0){
        not_null = TRUE;
      }
      proto_tree_add_boolean(tree, hf_freepastry_routingtable_broadcast_routerow_notnull, tvb, offset, 1, not_null);
      offset++;
      if (not_null){
        offset = decode_routeset(tvb, tree, offset, ep_strdup_printf("RouteSet for col 0x%X", i));
        if (offset == -1){
          return;
        }
      }
    }
  }
}

/* Router Message */

static void
decode_msg_route(tvbuff_t *tvb, packet_info *pinfo, proto_tree *tree,
                 gint offset, guint16 short_address _U_){
  guint32 address = tvb_get_ntohl(tvb, offset+1);
  if (tree){
    proto_tree_add_item(tree, hf_freepastry_msg_version, tvb, offset, 1, FALSE);
    proto_tree_add_item(tree, hf_freepastry_router_sub_address, tvb, offset + 1, 4, FALSE);
    offset += 5;
    if (tvb_reported_length_remaining(tvb, offset) < 20){
      proto_tree_add_text(tree, tvb, offset, -1, "Malformed message!");
      return;
    }
    proto_tree_add_string(tree, hf_freepastry_router_target, tvb, offset, 20, get_id_full(tvb, offset));
    offset = decode_nodehandle(tvb, tree, offset + 20, "Previous Hop");
  } else {
    offset = get_node_handle_len(tvb, offset + 25);
  }

  if (offset != -1){
    decode_freepastry_tcp_msg_invariant(tvb, pinfo, tree, offset, address);
  }
}

/* Common API Message */

static void
decode_msg_pastry_endpoint(tvbuff_t *tvb, packet_info *pinfo, proto_tree *tree,
                           gint offset, guint16 short_address) {
  tvbuff_t *next_tvb;

  if (tvb_reported_length_remaining(tvb, offset+2) <= 0){
      return;	/* no more data */
  }

  if (tree){
    proto_tree_add_item(tree, hf_freepastry_commonapi_pastry_endpoint_version, tvb, offset, 1, FALSE);
    proto_tree_add_item(tree, hf_freepastry_commonapi_pastry_endpoint_priority, tvb, offset+1, 1, FALSE);
  }

  next_tvb = tvb_new_subset(tvb, offset + 2, -1, -1);
  dissector_try_port(subdissector_table, short_address, next_tvb, pinfo, tree);
}

static void
handle_not_supported_msg(tvbuff_t *tvb _U_, packet_info *pinfo  _U_, proto_tree *tree _U_,
                         gint offset _U_, guint16 short_address _U_) {
  return;
}

/*
 * End: TCP FreePastry Core Message dissection.
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
          /*TODO? test if there are enough bytes available*/
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
 * Encapsulated into another one (e.g. into a Route Message)
**/
static void
decode_freepastry_tcp_msg_invariant(tvbuff_t *tvb, packet_info *pinfo, proto_tree *tree, gint offset, guint32 address)
{
  gboolean has_sender = FALSE;
  msg_decoder_t decode_msg = handle_not_supported_msg;
  gint16  type          = 0;
  guint16 short_address = 0;
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
        proto_tree_add_item(tree, hf_freepastry_direct_access_msg_type, tvb, offset, 2, FALSE);
      }
      switch (type){
        case SOURCE_ROUTE:
          decode_msg = decode_msg_source_route;
          break;
        case LEAFSET_REQUEST_MSG:
          decode_msg = decode_msg_leafset_request;
          break;
        case LEAFSET_RESPONSE_MSG:
          decode_msg = decode_msg_leafset_response;
          break;
        case NODE_ID_REQUEST_MSG:
          decode_msg = decode_msg_id_request;
          break;
        case NODE_ID_RESPONSE_MSG:
          decode_msg = decode_msg_id_response;
          break;
        case ROUTE_ROW_REQUEST_MSG:
          decode_msg = decode_msg_row_request;
          break;
        case ROUTE_ROW_RESPONSE_MSG:
          decode_msg = decode_msg_row_response;
          break;
        case ROUTES_REQUEST_MSG:
          decode_msg = decode_msg_routes_request;
          break;
        case ROUTES_RESPONSE_MSG:
          decode_msg = decode_msg_routes_response;
          break;
        /*default: nothing to be done here*/
      }
      break;
    case ROUTER:
      type_string = val_to_str(type, freepastry_router_msg, "<Unknown type %d>");
      if (tree){
        proto_tree_add_item(tree, hf_freepastry_router_msg_type, tvb, offset, 2, FALSE);
      }
      if (type == ROUTE){
        decode_msg = decode_msg_route;
      }
      break;
    case JOIN_PROTOCOL:
      type_string = val_to_str(type, freepastry_join_msg, "<Unknown type %d>");
      if (tree){
        proto_tree_add_item(tree, hf_freepastry_join_msg_type, tvb, offset, 2, FALSE);
      }
      switch (type){
        case JOIN_REQUEST:
          decode_msg = decode_msg_join_request;
          break;
        case CONSISTENT_JOIN_MSG:
          decode_msg = decode_msg_consistent_join;
          break;
      }
      break;
    case LEAF_PROTOCOL:
      type_string = val_to_str(type, freepastry_leafset_msg, "<Unknown type %d>");
      if (tree){
        proto_tree_add_item(tree, hf_freepastry_leafset_msg_type, tvb, offset, 2, FALSE);
      }
      switch (type){
        case REQUEST_LEAFSET:
          decode_msg = decode_msg_request_leafset;
          break;
        case BROADCAST_LEAFSET:
          decode_msg = decode_msg_broadcast_leafset;
          break;
      }
      break;
    case ROUTE_PROTOCOL:
      type_string = val_to_str(type, freepastry_routingtable_msg, "<Unknown type %d>");
      if (tree){
        proto_tree_add_item(tree, hf_freepastry_routingtable_msg_type, tvb, offset, 2, FALSE);
      }
      switch (type){
        case REQUEST_ROUTE_ROW:
          decode_msg = decode_msg_request_route_row;
          break;
        case BROADCAST_ROUTE_ROW:
          decode_msg = decode_msg_route_row;
          break;
      }
      break;
    default:
      type_string = val_to_str(type, freepastry_commonapi_msg, "<Unknown type %d>");
      if (tree){
        proto_tree_add_item(tree, hf_freepastry_commonapi_msg_type, tvb, offset, 2, FALSE);
      }
      if (type == PASTRY_ENDPOINT_MESSAGE){
          decode_msg = decode_msg_pastry_endpoint;
          short_address = (guint16) ((address >> 16) & 0xffff);
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

  if (offset != -1) {
    decode_msg(tvb, pinfo, tree, offset, short_address);
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
 * Dissect a TCP FreePastry message
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

/* associate data with conversation */
static void
attach_data_to_conversation(conversation_t *conversation, guint32 id, gboolean is_app_stream)
{
  struct freepastry_tcp_stream_data *tcp_stream_data;
  
  /* Is there a request structure attached to this conversation?*/
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
    { &hf_freepastry_msg_size,
    { "Message size",	"freepastry.msg_size",
    FT_UINT32, BASE_DEC, NULL, 0x0,
    "", HFILL }},
    { &hf_freepastry_eisa_num_add,
    { "Number of addresses",	"freepastry.eisa.num",
    FT_UINT8, BASE_DEC, NULL, 0x0,
    "", HFILL }},
    { &hf_freepastry_eisa_ip,
    { "IP address",	"freepastry.eisa.ip",
    FT_IPv4, BASE_DEC, NULL, 0x0,
    "", HFILL }},
    { &hf_freepastry_eisa_port,
    { "Port number",	"freepastry.eisa.port",
    FT_UINT16, BASE_DEC, NULL, 0x0,
    "", HFILL }},
    { &hf_freepastry_eisa_epoch,
    { "Epoch",	"freepastry.header.eisa.epoch",
    FT_UINT64, BASE_DEC, NULL, 0x0,
    "", HFILL }},
    { &hf_freepastry_id_type,
    { "ID type",	"freepastry.id_type",
    FT_INT16, BASE_DEC, VALS(freepastry_id_type), 0x0,
    "", HFILL }},
    { &hf_freepastry_id_value,
    { "ID value", "freepastry.id",
    FT_STRING, BASE_NONE, NULL, 0x0,
    "", HFILL }},
    { &hf_freepastry_ringid,
    { "ID value", "freepastry.ringid",
    FT_STRING, BASE_NONE, NULL, 0x0,
    "", HFILL }},
    { &hf_freepastry_gcid_expiration,
    { "Expiration",	"freepastry.gcid.expiration",
    FT_UINT64, BASE_DEC, NULL, 0x0,
    "", HFILL }},
    { &hf_freepastry_versionkey_version,
    { "Version",	"freepastry.versionkey.version",
    FT_UINT64, BASE_DEC, NULL, 0x0,
    "", HFILL }},
    { &hf_freepastry_fragmentkey_id,
    { "Fragment key id",	"freepastry.fragmentkey.id",
    FT_UINT32, BASE_DEC, NULL, 0x0,
    "", HFILL }},
    { &hf_freepastry_rs_capacity,
    { "Capacity",	"freepastry.routeset.capacity",
    FT_UINT8, BASE_DEC, NULL, 0x0,
    "", HFILL }},
    { &hf_freepastry_rs_size,
    { "RouteSet size",	"freepastry.routeset.size",
    FT_UINT8, BASE_DEC, NULL, 0x0,
    "", HFILL }},
    { &hf_freepastry_rs_closest,
    { "Closest",	"freepastry.routeset.closest",
    FT_UINT8, BASE_DEC, NULL, 0x0,
    "", HFILL }},
    { &hf_freepastry_ns_size,
    { "NodeHandleSet size",	"freepastry.nodehandleset.size",
    FT_UINT8, BASE_DEC, NULL, 0x0,
    "", HFILL }},
    { &hf_freepastry_mns_type,
    { "Internal node handle set type",	"freepastry.multiringnodehandleset.type",
    FT_UINT8, BASE_DEC, NULL, 0x0,
    "", HFILL }},
    { &hf_freepastry_ls_size,
    { "Leafset size",	"freepastry.leafset.size",
    FT_UINT8, BASE_DEC, NULL, 0x0,
    "", HFILL }},
    { &hf_freepastry_ls_num_unique_handle,
    { "Number of unique handles",	"freepastry.leafset.num_unique",
    FT_UINT8, BASE_DEC, NULL, 0x0,
    "", HFILL }},
    { &hf_freepastry_ls_cw_size,
    { "Clockwise size",	"freepastry.leafset.cw_size",
    FT_UINT8, BASE_DEC, NULL, 0x0,
    "", HFILL }},
    { &hf_freepastry_ls_ccw_size,
    { "Counter clockwise size",	"freepastry.leafset.ccw_size",
    FT_UINT8, BASE_DEC, NULL, 0x0,
    "", HFILL }},
    { &hf_freepastry_ls_handle_index,
    { "Index",	"freepastry.leafset.hdl_index",
    FT_UINT8, BASE_DEC, NULL, 0x0,
    "", HFILL }},
    { &hf_freepastry_header_magic_number,
    { "Magic number",		"freepastry.header.magic_number",
    FT_UINT32, BASE_HEX, NULL, 0x0,
    "", HFILL }},
    { &hf_freepastry_header_version_number,
    { "Version number",		"freepastry.header.version",
    FT_UINT32, BASE_DEC, NULL, 0x0,
    "", HFILL }},
    { &hf_freepastry_header_current_hop,
    { "Hop counter", "freepastry.header.hop_counter",
    FT_UINT8, BASE_DEC, NULL, 0x0,
    "", HFILL }},
    { &hf_freepastry_header_num_hop,
    { "Number of hops",	"freepastry.header.num_hop",
    FT_UINT8, BASE_DEC, NULL, 0x0,
    "", HFILL }},
    { &hf_freepastry_header_source_route_len,
    { "Source route length",	"freepastry.header.source_route_len",
    FT_UINT16, BASE_DEC, NULL, 0x0,
    "", HFILL }},
    { &hf_freepastry_header_header_direct,
    { "Header direct",	"freepastry.header.header_direct",
    FT_UINT32, BASE_HEX, NULL, 0x0,
    "", HFILL }},
    { &hf_freepastry_header_app_id,
    { "Application ID",	"freepastry.header.app_id",
    FT_UINT32, BASE_DEC, NULL, 0x0,
    "", HFILL }},
    { &hf_freepastry_msg_header_address,
    { "Address",	"freepastry.msg_header.address",
    FT_UINT32, BASE_HEX, VALS(freepastry_address), 0x0,
    "", HFILL }},
    { &hf_freepastry_msg_header_has_sender,
    { "Has sender",	"freepastry.msg_header.has_sender",
    FT_BOOLEAN, 8, NULL, 0x0,
    "", HFILL }},
    { &hf_freepastry_msg_header_priority,
    { "Priority",	"freepastry.msg_header.priority",
    FT_INT8, BASE_DEC, VALS(freepastry_priority), 0x0,
    "", HFILL }},
    { &hf_freepastry_liveness_msg_type,
    { "Message type",	"freepastry.liveness.type",
    FT_INT16, BASE_DEC, VALS(freepastry_liveness_message), 0x0,
    "", HFILL }},
    { &hf_freepastry_liveness_msg_sent_time,
    { "Sent time",	"freepastry.liveness.send_time",
    FT_UINT64, BASE_DEC, NULL, 0x0,
    "", HFILL }},
    { &hf_freepastry_router_msg_type,
    { "Message type",	"freepastry.router.type",
    FT_INT16, BASE_DEC, VALS(freepastry_router_msg), 0x0,
    "", HFILL }},
    { &hf_freepastry_join_msg_type,
    { "Message type",	"freepastry.join.type",
    FT_INT16, BASE_DEC, VALS(freepastry_join_msg), 0x0,
    "", HFILL }},
    { &hf_freepastry_leafset_msg_type,
    { "Message type",	"freepastry.leafset.type",
    FT_INT16, BASE_DEC, VALS(freepastry_leafset_msg), 0x0,
    "", HFILL }},
    { &hf_freepastry_routingtable_msg_type,
    { "Message type",	"freepastry.routingtable.type",
    FT_INT16, BASE_DEC, VALS(freepastry_routingtable_msg), 0x0,
    "", HFILL }},
    { &hf_freepastry_direct_access_msg_type,
    { "Message type",	"freepastry.direct.type",
    FT_INT16, BASE_DEC, VALS(freepastry_direct_access_msg), 0x0,
    "", HFILL }},
    { &hf_freepastry_commonapi_msg_type,
    { "Message type",	"freepastry.commonapi.type",
    FT_INT16, BASE_DEC, VALS(freepastry_commonapi_msg), 0x0,
    "", HFILL }},
    { &hf_freepastry_msg_version,
    { "Version",	"freepastry.msg.version",
    FT_UINT8, BASE_DEC, NULL, 0x0,
    "", HFILL }},
    { &hf_freepastry_router_sub_address,
    { "Sub message address",	"freepastry.router.route.sub",
    FT_UINT32, BASE_HEX, VALS(freepastry_address), 0x0,
    "", HFILL }},
    { &hf_freepastry_router_target,
    { "Target", "freepastry.router.route.target",
    FT_STRING, BASE_NONE, NULL, 0x0,
    "", HFILL }},
    { &hf_freepastry_direct_nodeid_resp_epoch,
    { "Epoch",	"freepastry.direct.nodeid.resp.epoch",
    FT_UINT64, BASE_DEC, NULL, 0x0,
    "", HFILL }},
    { &hf_freepastry_direct_routerow_row,
    { "Requested row",	"freepastry.direct.route_row.row",
    FT_UINT32, BASE_DEC, NULL, 0x0,
    "", HFILL }},
    { &hf_freepastry_direct_routerow_numroutesets,
    { "Number of RouteSets",	"freepastry.direct.route_row.numrouteset",
    FT_UINT32, BASE_DEC, NULL, 0x0,
    "", HFILL }},
    { &hf_freepastry_direct_routerow_notnull,
    { "RouteSet is not null",	"freepastry.direct.route_row.notnullrouteset",
    FT_BOOLEAN, 8, NULL, 0x0,
    "", HFILL }},
    { &hf_freepastry_direct_sourceroute_numhops,
    { "Number of hops",	"freepastry.direct.source_route.num_hops",
    FT_UINT32, BASE_DEC, NULL, 0x0,
    "", HFILL }},
    { &hf_freepastry_join_consistent_join_is_request,
    { "Is request",	"freepastry.direct.join.consistent_join.is_request",
    FT_BOOLEAN, 8, NULL, 0x0,
    "", HFILL }},
    { &hf_freepastry_join_consistent_join_num_failed,
    { "Number of failed set",	"freepastry.join.consistent_join.num_failed_set",
    FT_UINT32, BASE_DEC, NULL, 0x0,
    "", HFILL }},
    { &hf_freepastry_join_join_rtbasebitlength,
    { "RT base bit length",	"freepastry.join.join.rtbasebitlength",
    FT_UINT32, BASE_DEC, NULL, 0x0,
    "", HFILL }},
    { &hf_freepastry_join_join_has_join_handle,
    { "Has join handle",	"freepastry.join.join.has_join_handle",
    FT_BOOLEAN, 8, NULL, 0x0,
    "", HFILL }},
    { &hf_freepastry_join_join_last_row,
    { "Row index",	"freepastry.join.join.last_row",
    FT_UINT16, BASE_DEC, NULL, 0x0,
    "", HFILL }},
    { &hf_freepastry_join_join_has_row,
    { "Has row",	"freepastry.join.join.has_row",
    FT_BOOLEAN, 8, NULL, 0x0,
    "", HFILL }},
    { &hf_freepastry_join_join_has_col,
    { "Has column",	"freepastry.join.join.has_col",
    FT_BOOLEAN, 8, NULL, 0x0,
    "", HFILL }},
    { &hf_freepastry_join_join_has_leafset,
    { "Has leafset",	"freepastry.join.join.has_leafset",
    FT_BOOLEAN, 8, NULL, 0x0,
    "", HFILL }},
    { &hf_freepastry_leafset_request_leafset_timestamp,
    { "Timestamp",	"freepastry.leafset.request_leafset.timestamp",
    FT_UINT64, BASE_DEC, NULL, 0x0,
    "", HFILL }},
    { &hf_freepastry_leafset_broadcast_leafset_type,
    { "Type",	"freepastry.leafset.broadcast_leafset.type",
    FT_UINT8, BASE_DEC, NULL, 0x0,
    "", HFILL }},
    { &hf_freepastry_leafset_broadcast_leafset_timestamp,
    { "Timestamp",	"freepastry.leafset.broadcast_leafset.timestamp",
    FT_UINT64, BASE_DEC, NULL, 0x0,
    "", HFILL }},
    { &hf_freepastry_routingtable_request_routerow_row,
    { "Row",	"freepastry.routingtable.request_routerow.row",
    FT_UINT8, BASE_DEC, NULL, 0x0,
    "", HFILL }},
    { &hf_freepastry_routingtable_broadcast_routerow_num_row,
    { "Number of RouteSets",	"freepastry.routingtable.broadcast_routerow.numrouteset",
    FT_UINT8, BASE_DEC, NULL, 0x0,
    "", HFILL }},
    { &hf_freepastry_routingtable_broadcast_routerow_notnull,
    { "RouteSet is not null",	"freepastry.routingtable.broadcast_routerow.notnullrouteset",
    FT_BOOLEAN, 8, NULL, 0x0,
    "", HFILL }},
    { &hf_freepastry_commonapi_pastry_endpoint_version,
    { "Version number",		"freepastry.commonapi.pastry_endpoint.version",
    FT_UINT32, BASE_DEC, NULL, 0x0,
    "", HFILL }},
    { &hf_freepastry_commonapi_pastry_endpoint_priority,
    { "Priority",	"freepastry.commonapi.pastry_endpoint.priority",
    FT_INT8, BASE_DEC, VALS(freepastry_priority), 0x0,
    "", HFILL }}
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
    &ett_freepastry_ls_ccw,
    &ett_freepastry_sr
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

	subdissector_table = register_dissector_table("commonapi.app", "Common API Application", FT_UINT16, BASE_HEX);
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
