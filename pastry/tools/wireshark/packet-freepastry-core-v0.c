/* packet-freepastry-core-v0.c
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
#include <epan/prefs.h>

#include "packet-freepastry.h"

static int proto_freepastry_core_v0 = -1;

static int hf_freepastry_router_sub_address = -1;
static int hf_freepastry_router_target = -1;
static int hf_freepastry_router_has_destination_handle  = -1;
static int hf_freepastry_direct_nodeid_resp_id_value = -1;
static int hf_freepastry_direct_nodeid_resp_epoch = -1;
static int hf_freepastry_direct_routerow_row = -1;
static int hf_freepastry_direct_routerow_numroutesets = -1;
static int hf_freepastry_direct_routerow_notnull = -1;
static int hf_freepastry_direct_sourceroute_numhops = -1;
static int hf_freepastry_direct_sourceroute_resp_numsourceroutes = -1;
static int hf_freepastry_join_consistent_join_is_request  = -1;
static int hf_freepastry_join_consistent_join_num_failed  = -1;
static int hf_freepastry_join_join_req_rtbasebitlength  = -1;
static int hf_freepastry_join_join_req_has_join_handle  = -1;
static int hf_freepastry_join_join_req_last_row  = -1;
static int hf_freepastry_join_join_req_has_row  = -1;
static int hf_freepastry_join_join_req_has_col  = -1;
static int hf_freepastry_join_join_req_has_leafset  = -1;
static int hf_freepastry_leafset_request_leafset_timestamp  = -1;
static int hf_freepastry_leafset_broadcast_leafset_type  = -1;
static int hf_freepastry_leafset_broadcast_leafset_timestamp  = -1;
static int hf_freepastry_routingtable_routerow_req_row  = -1;
static int hf_freepastry_routingtable_broadcast_routerow_num_row  = -1;
static int hf_freepastry_routingtable_broadcast_routerow_notnull  = -1;
static int hf_freepastry_msg_pastry_endpoint_priority = -1;
static int hf_freepastry_direct_v0 = -1;
static int hf_freepastry_router_v0  = -1;
static int hf_freepastry_commonapi_v0 = -1;
static int hf_freepastry_join_v0 = -1;
static int hf_freepastry_routingtable_v0 = -1;
static int hf_freepastry_leafset_proto_v0 = -1;
static int hf_freepastry_direct_leafset_req = -1;
static int hf_freepastry_direct_leafset_resp = -1;
static int hf_freepastry_direct_nodeid_req = -1;
static int hf_freepastry_direct_nodeid_resp = -1;
static int hf_freepastry_direct_routerow_req = -1;
static int hf_freepastry_direct_routerow_resp = -1;
static int hf_freepastry_direct_sourceroute_req = -1;
static int hf_freepastry_direct_sourceroute_resp = -1;
static int hf_freepastry_direct_sourceroute = -1;
static int hf_freepastry_router_route  = -1;
static int hf_freepastry_commonapi_pastry_endpoint = -1;
static int hf_freepastry_join_join_req = -1;
static int hf_freepastry_join_consistent_join = -1;
static int hf_freepastry_routingtable_routerow_req = -1;
static int hf_freepastry_routingtable_broadcast_routerow = -1;
static int hf_freepastry_leafset_proto_leafset_req = -1;
static int hf_freepastry_leafset_proto_broadcast_leafset = -1;

static gint ett_freepastry_core_v0 = -1;
static gint ett_freepastry_core_v0_sr = -1;

static dissector_handle_t freepastry_core_v0_handle;

/*To dissect common API applications messages*/
static dissector_table_t subdissector_commonapi_table;

/*TODO duplicate data in packet-freepastry*/
/* Address mapping */
static const value_string freepastry_address[] = {
  { DIRECT_ACCESS, 	       "Direct Access" },
  { ROUTER, 	             "Router" },
  { JOIN_PROTOCOL, 	       "Join Protocol" },
  { LEAFSET_PROTOCOL, 	   "Leafset Protocol" },
  { ROUTINGTABLE_PROTOCOL, "Route Protocol" }, 
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

/**
*   Decode a "Source Route" object.
*   @return the new offset or -1 on error.
**/
static gint
decode_sourceroute(tvbuff_t *tvb, proto_tree *parent_tree, gint offset, gchar *attribute_name)
{
  proto_item *ti = NULL;
  proto_tree *sourceroute_tree = NULL; 
  guint32 i;
  guint32 sourceroute_size = tvb_get_ntohl(tvb, offset);
  ti = proto_tree_add_text(parent_tree, tvb, offset, 1, attribute_name);
  sourceroute_tree = proto_item_add_subtree(ti, ett_freepastry_core_v0_sr);
  proto_tree_add_uint(sourceroute_tree, hf_freepastry_direct_sourceroute_numhops, tvb, offset, 4, sourceroute_size);
  offset += 4;
  /*for each hop (do not parse the last hop)*/
  for (i=0; i < (sourceroute_size - 1); ++i){
    offset = decode_epoch_inet_socket_address(tvb, sourceroute_tree, offset, "Hop");
    if (offset == -1){
      return -1;
    }
  }
  /*Parse the last node in the sourceroute*/
  if (sourceroute_size > 0) {
    gchar *ip_str;
    guint16 port_number;
    gint former_offset = offset;
    offset = decode_epoch_inet_socket_address(tvb, sourceroute_tree, offset, "Hop");
    if (offset == -1){
      return -1;
    }
    /*Print final destination on the subtree root*/
    ip_str = ip_to_str(tvb_get_ptr(tvb, former_offset + 1, 4));
    former_offset += 5;
    port_number = tvb_get_ntohs(tvb, former_offset);
    proto_item_append_text(ti, " -> %s:%d", ip_str, port_number);
  }
  proto_item_set_end(ti, tvb, offset);
  return offset;
}

/*
 * Start: TCP FreePastry Core Message dissection.
 */

/*Direct Acces Messages*/
static void
decode_msg_leafset_request(tvbuff_t *tvb, packet_info *pinfo _U_, proto_tree *tree) {
  gint offset = 0;
  if (tree){
    decode_message_version(tvb,tree, offset, DIRECT_ACCESS);
  }
}

static void
decode_msg_leafset_response(tvbuff_t *tvb, packet_info *pinfo _U_, proto_tree *tree) {
  gint offset = 0;
  if (tree){
    offset = decode_message_version(tvb,tree, offset, DIRECT_ACCESS);
    offset = decode_leafset(tvb, tree, offset, "Leafset");
  }
}

static void
decode_msg_id_request(tvbuff_t *tvb, packet_info *pinfo _U_, proto_tree *tree) {
  gint offset = 0;
  if (tree){
    decode_message_version(tvb,tree, offset, DIRECT_ACCESS);
  }
}

static void
decode_msg_id_response(tvbuff_t *tvb, packet_info *pinfo, proto_tree *tree) {
  gint offset = 0;
  if (tvb_reported_length_remaining(tvb, offset) < 29){
    proto_tree_add_text(tree, tvb, offset, -1, "Malformed message!");
    return;
  }

  if (check_col(pinfo->cinfo, COL_INFO)){
    col_append_str(pinfo->cinfo, COL_INFO, get_id(tvb, offset + 1));
  }

  if (tree){
    offset = decode_message_version(tvb,tree, offset, DIRECT_ACCESS);
    proto_tree_add_string(tree, hf_freepastry_direct_nodeid_resp_id_value, tvb, offset, 20, get_id_full(tvb, offset));
    proto_tree_add_item(tree, hf_freepastry_direct_nodeid_resp_epoch, tvb, offset+20, 8, FALSE);
  }
}

static void
decode_msg_row_request(tvbuff_t *tvb, packet_info *pinfo, proto_tree *tree) {
  gint offset = 0;
  guint32 requested_row = tvb_get_ntohl(tvb, offset +1);
  if (check_col(pinfo->cinfo, COL_INFO)){
    col_append_fstr(pinfo->cinfo, COL_INFO, " %d", requested_row);
  }
  if (tree){
    offset = decode_message_version(tvb,tree, offset, DIRECT_ACCESS);
    proto_tree_add_uint(tree, hf_freepastry_direct_routerow_row, tvb, offset, 4, requested_row);
  }
}

static void
decode_msg_row_response(tvbuff_t *tvb, packet_info *pinfo, proto_tree *tree) {
  gint offset = 0;
  guint32 num_routeset = tvb_get_ntohl(tvb, offset +1);
  if (check_col(pinfo->cinfo, COL_INFO)){
    col_append_fstr(pinfo->cinfo, COL_INFO, " (%d Route Sets)", num_routeset);
  }
  if (tree)
  {
    guint32 i;
    offset = decode_message_version(tvb,tree, offset, DIRECT_ACCESS);
    proto_tree_add_uint(tree, hf_freepastry_direct_routerow_numroutesets, tvb, offset, 4, num_routeset);
    offset += 4;
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
decode_msg_routes_request(tvbuff_t *tvb, packet_info *pinfo _U_, proto_tree *tree) {
  gint offset = 0;
  if (tree){
    decode_message_version(tvb,tree, offset, DIRECT_ACCESS);
  }
}

static void
decode_msg_routes_response(tvbuff_t *tvb, packet_info *pinfo _U_, proto_tree *tree) {
  gint offset = 0;
  if (tree){
    guint32 i;
    guint32 num_sourceroutes = tvb_get_ntohl(tvb, offset+1);
    offset = decode_message_version(tvb,tree, offset, DIRECT_ACCESS);
    proto_tree_add_uint(tree, hf_freepastry_direct_sourceroute_resp_numsourceroutes, 
      tvb, offset, 4, num_sourceroutes);
    offset += 4;
    for (i=0; i < num_sourceroutes; ++i){
      /*TODO check version*/
      offset = decode_message_version(tvb,tree, offset, DIRECT_ACCESS);
      offset = decode_sourceroute(tvb, tree, offset, "Source Route");
      if (offset == -1){
        return;
      }
    }
  }
}

static void
decode_msg_source_route(tvbuff_t *tvb, packet_info *pinfo _U_, proto_tree *tree) {
  gint offset = 0;
  if (tree){
    offset = decode_message_version(tvb,tree, offset, DIRECT_ACCESS);
    decode_sourceroute(tvb, tree, offset, "Source Route");
  }
}

/* Join Messages */

static void
decode_msg_join_request(tvbuff_t *tvb, packet_info *pinfo _U_, proto_tree *tree) {
  gint offset = 0;
  if (tree){
    gboolean has_join_handle = FALSE;
    gboolean has_leafset = FALSE;
    guint8 rt_base_bit_length;
    guint8 num_row;
    guint8 num_col;
    guint8 i;
    guint8 j;

    rt_base_bit_length = tvb_get_guint8(tvb, offset + 1);
    offset = decode_message_version(tvb,tree, offset, JOIN_PROTOCOL);
    proto_tree_add_item(tree, hf_freepastry_join_join_req_rtbasebitlength, tvb, offset, 1, rt_base_bit_length);
    offset += 1;
    offset = decode_nodehandle(tvb, tree, offset, "Node Handle");
    if (offset == -1) {
      return;
    }
    if (tvb_get_guint8(tvb, offset) != 0){
      has_join_handle = TRUE;
    }
    proto_tree_add_boolean(tree, hf_freepastry_join_join_req_has_join_handle, tvb, offset, 1, has_join_handle);
    offset++;
    if (has_join_handle) {
      offset = decode_nodehandle(tvb, tree, offset, "Join Handle");
    }
    if (offset == -1) {
      return;
    }
    proto_tree_add_item(tree, hf_freepastry_join_join_req_last_row, tvb, offset, 2, FALSE);
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
      proto_tree_add_boolean(tree, hf_freepastry_join_join_req_has_row, tvb, offset, 1, has_row);
      offset++;
      if (has_row) {
        for (j = 0; j < num_col; ++j){
          gboolean has_col = FALSE;
          if (tvb_get_guint8(tvb, offset) != 0){
            has_col = TRUE;
          }
          proto_tree_add_boolean(tree, hf_freepastry_join_join_req_has_col, tvb, offset, 1, has_col);
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
    proto_tree_add_boolean(tree, hf_freepastry_join_join_req_has_leafset, tvb, offset, 1, has_leafset);
    if (has_leafset) {
      offset = decode_leafset(tvb, tree, offset + 1, "LeafSet");
    }
  }
}

static void
decode_msg_consistent_join(tvbuff_t *tvb, packet_info *pinfo _U_, proto_tree *tree) {
  gint offset = 0;
  if (tree){
    guint32 num_failed;
    guint32 i;

    offset = decode_message_version(tvb,tree, offset, JOIN_PROTOCOL);
    offset = decode_leafset(tvb, tree, offset, "Leafset");
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
decode_msg_request_leafset(tvbuff_t *tvb, packet_info *pinfo _U_, proto_tree *tree) {
  gint offset = 0;
  if (tree){
    offset = decode_message_version(tvb,tree, offset, LEAFSET_PROTOCOL);
    proto_tree_add_item(tree, hf_freepastry_leafset_request_leafset_timestamp, tvb, offset, 8, FALSE);
  }
}

static void
decode_msg_broadcast_leafset(tvbuff_t *tvb, packet_info *pinfo _U_, proto_tree *tree) {
  gint offset = 0;
  if (tree){
    offset = decode_message_version(tvb,tree, offset, LEAFSET_PROTOCOL);
    offset = decode_nodehandle(tvb, tree, offset, "From Handle");
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
decode_msg_request_route_row(tvbuff_t *tvb, packet_info *pinfo, proto_tree *tree) {
  gint offset = 0;
  guint32 requested_row = tvb_get_ntohl(tvb, offset +1);
  if (check_col(pinfo->cinfo, COL_INFO)){
    col_append_fstr(pinfo->cinfo, COL_INFO, " %d", requested_row);
  }
  if (tree){
    offset = decode_message_version(tvb,tree, offset, ROUTINGTABLE_PROTOCOL);
    proto_tree_add_uint(tree, hf_freepastry_routingtable_routerow_req_row, tvb, offset, 4, requested_row);
  }
}

static void
decode_msg_route_row(tvbuff_t *tvb, packet_info *pinfo, proto_tree *tree) {
  guint8 num_rows;
  gint offset = 0;
  if (tree)
  {
    offset = decode_message_version(tvb,tree, offset, ROUTINGTABLE_PROTOCOL);
    offset = decode_nodehandle(tvb, tree, offset, "From Handle");
  } else {
    offset++;
    offset += get_node_handle_len(tvb, offset);
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
decode_msg_route(tvbuff_t *tvb, packet_info *pinfo, proto_tree *tree){
  gint offset = 0;  
  guint8 version_num;
  gboolean has_destination = FALSE;
  guint32 address = tvb_get_ntohl(tvb, offset+1);
  version_num = tvb_get_guint8(tvb, offset);
  if (tree){
    offset = decode_message_version(tvb,tree, offset, ROUTER);
    proto_tree_add_item(tree, hf_freepastry_router_sub_address, tvb, offset, 4, FALSE);
    offset += 4;
    if (tvb_reported_length_remaining(tvb, offset) < 20){
      proto_tree_add_text(tree, tvb, offset, -1, "Malformed message!");
      return;
    }
    switch(version_num) {
    case 0:
      proto_tree_add_string(tree, hf_freepastry_router_target, tvb, offset, 20, get_id_full(tvb, offset));
      offset += 20;
      break;
    case 1:
      if (tvb_get_guint8(tvb, offset) != 0){
        has_destination = TRUE;
      }
      proto_tree_add_boolean(tree, hf_freepastry_router_has_destination_handle, tvb, offset, 1, has_destination);
      offset++;
      if (has_destination) {
        offset = decode_nodehandle(tvb, tree, offset, "Destination Handle");
      } else {
	proto_tree_add_string(tree, hf_freepastry_router_target, tvb, offset, 20, get_id_full(tvb, offset));
	offset += 20;
      }
      if (offset == -1) {
        return;
      }
      break;
    }

    offset = decode_nodehandle(tvb, tree, offset, "Previous Hop");
  } else {    
    offset += 5; // version+auxAddress
    switch(version_num) {
    case 0:
      offset += 20;
      break;
    case 1:
      if (tvb_get_guint8(tvb, offset) != 0){
        has_destination = TRUE;
      }
      offset++; // hasDestHandle
      if (has_destination) {
        offset += get_node_handle_len(tvb, offset); // the destHandle
      } else {
	offset += 20;
      } 
    }    
    if (offset == -1) return; // error
    offset += get_node_handle_len(tvb, offset); // the previous Hop
  }

  if (offset != -1){
    decode_freepastry_tcp_msg_invariant(tvb, pinfo, tree, offset, address);
  }
}

/* Common API Message */

static void
decode_msg_pastry_endpoint(tvbuff_t *tvb, packet_info *pinfo, proto_tree *tree, guint32 address) {
  gint offset = 0;
  tvbuff_t *next_tvb;
  guint16 short_address = (guint16) ((address >> 16) & 0xffff);

  if (tvb_reported_length_remaining(tvb, offset+2) <= 0){
      return;	/* no more data */
  }

  if (tree){
    offset = decode_message_version(tvb,tree, offset, address);
    proto_tree_add_item(tree, hf_freepastry_msg_pastry_endpoint_priority, tvb, offset, 1, FALSE);
  }

  next_tvb = tvb_new_subset(tvb, offset + 1, -1, -1);
  dissector_try_port(subdissector_commonapi_table, short_address, next_tvb, pinfo, tree);
}

static void
handle_not_supported_msg(tvbuff_t *tvb, packet_info *pinfo  _U_, proto_tree *tree _U_) {
  if (check_col(pinfo->cinfo, COL_INFO)){
      col_append_str(pinfo->cinfo, COL_INFO, "Unsupported");
  }
  if (tree){
    proto_tree_add_text(tree, tvb, 1, -1, "Unsupported message for this version");
  }
}

/**
*   Heuristically dissect a tvbuff containing a FreePastry TCP Message
**/
static void 
dissect_freepastry_core_v0(tvbuff_t * tvb, packet_info * pinfo, proto_tree * tree)
{
  sub_message_info_t *message_info = (sub_message_info_t *)pinfo->private_data;
  switch (message_info->address){
    case DIRECT_ACCESS:
      proto_tree_add_item_hidden(tree, hf_freepastry_direct_v0, tvb, 0, -1, FALSE);
      switch (message_info->type){
        case LEAFSET_REQUEST_MSG:
          proto_tree_add_item_hidden(tree, hf_freepastry_direct_leafset_req, tvb, 0, -1, FALSE);
          decode_msg_leafset_request(tvb, pinfo, tree);
          break;
        case LEAFSET_RESPONSE_MSG:
          proto_tree_add_item_hidden(tree, hf_freepastry_direct_leafset_resp, tvb, 0, -1, FALSE);
          decode_msg_leafset_response(tvb, pinfo, tree);
          break;
        case NODE_ID_REQUEST_MSG:
          proto_tree_add_item_hidden(tree, hf_freepastry_direct_nodeid_req, tvb, 0, -1, FALSE);
          decode_msg_id_request(tvb, pinfo, tree);
          break;
        case NODE_ID_RESPONSE_MSG:
          proto_tree_add_item_hidden(tree, hf_freepastry_direct_nodeid_resp, tvb, 0, -1, FALSE);
          decode_msg_id_response(tvb, pinfo, tree);
          break;
        case ROUTE_ROW_REQUEST_MSG:
          proto_tree_add_item_hidden(tree, hf_freepastry_direct_routerow_req, tvb, 0, -1, FALSE);
          decode_msg_row_request(tvb, pinfo, tree);
          break;
        case ROUTE_ROW_RESPONSE_MSG:
          proto_tree_add_item_hidden(tree, hf_freepastry_direct_routerow_resp, tvb, 0, -1, FALSE);
          decode_msg_row_response(tvb, pinfo, tree);
          break;
        case SOURCEROUTE_REQUEST_MSG:
          proto_tree_add_item_hidden(tree, hf_freepastry_direct_sourceroute_req, tvb, 0, -1, FALSE);
          decode_msg_routes_request(tvb, pinfo, tree);
          break;
        case SOURCEROUTE_RESPONSE_MSG:
          proto_tree_add_item_hidden(tree, hf_freepastry_direct_sourceroute_resp, tvb, 0, -1, FALSE);
          decode_msg_routes_response(tvb, pinfo, tree);
          break;
       case SOURCEROUTE_MSG:
          proto_tree_add_item_hidden(tree, hf_freepastry_direct_sourceroute, tvb, 0, -1, FALSE);
          decode_msg_source_route(tvb, pinfo, tree);
          break;
        default:
          handle_not_supported_msg(tvb, pinfo, tree);
      }
      break;
    case ROUTER:
      proto_tree_add_item_hidden(tree, hf_freepastry_router_v0, tvb, 0, -1, FALSE);
       switch (message_info->type){
        case ROUTE:
          proto_tree_add_item_hidden(tree, hf_freepastry_router_route, tvb, 0, -1, FALSE);
          decode_msg_route(tvb, pinfo, tree);
          break;
        default:
          handle_not_supported_msg(tvb, pinfo, tree);
      }
      break;
    case JOIN_PROTOCOL:
      proto_tree_add_item_hidden(tree, hf_freepastry_join_v0, tvb, 0, -1, FALSE);
      switch (message_info->type){
        case JOIN_REQUEST:
          proto_tree_add_item_hidden(tree, hf_freepastry_join_join_req, tvb, 0, -1, FALSE);
          decode_msg_join_request(tvb, pinfo, tree);
          break;
        case CONSISTENT_JOIN_MSG:
          proto_tree_add_item_hidden(tree, hf_freepastry_join_consistent_join, tvb, 0, -1, FALSE);
          decode_msg_consistent_join(tvb, pinfo, tree);
          break;
        default:
          handle_not_supported_msg(tvb, pinfo, tree);
      }
      break;
    case LEAFSET_PROTOCOL:
      proto_tree_add_item_hidden(tree, hf_freepastry_leafset_proto_v0, tvb, 0, -1, FALSE);
      switch (message_info->type){
        case REQUEST_LEAFSET:
          proto_tree_add_item_hidden(tree, hf_freepastry_leafset_proto_leafset_req, tvb, 0, -1, FALSE);
          decode_msg_request_leafset(tvb, pinfo, tree);
          break;
        case BROADCAST_LEAFSET:
          proto_tree_add_item_hidden(tree, hf_freepastry_leafset_proto_broadcast_leafset, tvb, 0, -1, FALSE);
          decode_msg_broadcast_leafset(tvb, pinfo, tree);
          break;
        default:
          handle_not_supported_msg(tvb, pinfo, tree);
      }
      break;
    case ROUTINGTABLE_PROTOCOL:
      proto_tree_add_item_hidden(tree, hf_freepastry_routingtable_v0, tvb, 0, -1, FALSE);
      switch (message_info->type){
        case REQUEST_ROUTE_ROW:
          proto_tree_add_item_hidden(tree, hf_freepastry_routingtable_routerow_req, tvb, 0, -1, FALSE);
          decode_msg_request_route_row(tvb, pinfo, tree);
          break;
        case BROADCAST_ROUTE_ROW:
          proto_tree_add_item_hidden(tree, hf_freepastry_routingtable_broadcast_routerow, tvb, 0, -1, FALSE);
          decode_msg_route_row(tvb, pinfo, tree);
          break;
        default:
          handle_not_supported_msg(tvb, pinfo, tree);
      }
      break;
    default:
      proto_tree_add_item_hidden(tree, hf_freepastry_commonapi_v0, tvb, 0, -1, FALSE);
      switch (message_info->type){
        case PASTRY_ENDPOINT_MESSAGE:
          proto_tree_add_item_hidden(tree, hf_freepastry_commonapi_pastry_endpoint, tvb, 0, -1, FALSE);
          decode_msg_pastry_endpoint(tvb, pinfo, tree, message_info->address);
          break;
        default:
          handle_not_supported_msg(tvb, pinfo, tree);
      }/*switch type*/
  }/*switch address*/
}


void
proto_register_freepastry_core_v0(void)
{
  static hf_register_info hf[] = {
    { &hf_freepastry_direct_v0,
	  { "Direct messages", "freepastry.direct.v0", 
    FT_NONE, BASE_NONE, NULL, 0x0, 
    "Show only direct access traffic", HFILL }},
    { &hf_freepastry_router_v0,
	  { "Router messages", "freepastry.router.v0", 
    FT_NONE, BASE_NONE, NULL, 0x0, 
    "Show only router traffic", HFILL }},
    { &hf_freepastry_commonapi_v0,
	  { "Common API messages", "freepastry.commonapi.v0", 
    FT_NONE, BASE_NONE, NULL, 0x0, 
    "Show only common API traffic", HFILL }},
    { &hf_freepastry_join_v0,
	  { "Join protocol", "freepastry.join.v0", 
    FT_NONE, BASE_NONE, NULL, 0x0, 
    "Show only join protocol traffic", HFILL }},
    { &hf_freepastry_routingtable_v0,
	  { "Routing table maintenance protocol", "freepastry.routingtable.v0", 
    FT_NONE, BASE_NONE, NULL, 0x0, 
    "Show only routing table maintenance protocol traffic", HFILL }},
    { &hf_freepastry_leafset_proto_v0,
	  { "Leafset maintenance protocol", "freepastry.leafset_proto.v0", 
    FT_NONE, BASE_NONE, NULL, 0x0, 
    "Show only leafset maintenance protocol traffic", HFILL }},
    { &hf_freepastry_direct_leafset_req,
	  { "Leafset request", "freepastry.direct.v0.leafset_req", 
    FT_NONE, BASE_NONE, NULL, 0x0, 
    "Show only leafset request messages", HFILL }},
    { &hf_freepastry_direct_leafset_resp,
	  { "Leafset response", "freepastry.direct.v0.leafset_resp", 
    FT_NONE, BASE_NONE, NULL, 0x0, 
    "Show only leafset response messages", HFILL }},
    { &hf_freepastry_direct_nodeid_req,
	  { "Node ID request", "freepastry.direct.v0.nodeid_req", 
    FT_NONE, BASE_NONE, NULL, 0x0, 
    "Show only node ID request messages", HFILL }},
    { &hf_freepastry_direct_nodeid_resp,
	  { "Node ID response", "freepastry.direct.v0.nodeid_resp", 
    FT_NONE, BASE_NONE, NULL, 0x0, 
    "Show only node ID response messages", HFILL }},
    { &hf_freepastry_direct_routerow_req,
	  { "RouteRow request", "freepastry.direct.v0.routerow_req", 
    FT_NONE, BASE_NONE, NULL, 0x0, 
    "Show only RouteRow request messages", HFILL }},
    { &hf_freepastry_direct_routerow_resp,
	  { "RouteRow response", "freepastry.direct.v0.routerow_resp", 
    FT_NONE, BASE_NONE, NULL, 0x0, 
    "Show only RouteRow response messages", HFILL }},
    { &hf_freepastry_direct_sourceroute_req,
	  { "SourceRoute request", "freepastry.direct.v0.sourceroute_req", 
    FT_NONE, BASE_NONE, NULL, 0x0, 
    "Show only SourceRoute request messages", HFILL }},
    { &hf_freepastry_direct_sourceroute_resp,
	  { "SourceRoute response", "freepastry.direct.v0.sourceroute_resp", 
    FT_NONE, BASE_NONE, NULL, 0x0, 
    "Show only SourceRoute response messages", HFILL }},
    { &hf_freepastry_direct_sourceroute,
	  { "SourceRoute", "freepastry.direct.v0.sourceroute", 
    FT_NONE, BASE_NONE, NULL, 0x0, 
    "Show only SourceRoute messages", HFILL }},
    { &hf_freepastry_router_route,
	  { "Route", "freepastry.router.v0.route", 
    FT_NONE, BASE_NONE, NULL, 0x0, 
    "Show only Route messages", HFILL }},
    { &hf_freepastry_commonapi_pastry_endpoint,
	  { "RouteRow response", "freepastry.commonapi.v0.pastry_endpoint", 
    FT_NONE, BASE_NONE, NULL, 0x0, 
    "Show only Pastry endpoint messages", HFILL }},
    { &hf_freepastry_join_join_req,
	  { "Join request", "freepastry.join.v0.join_req", 
    FT_NONE, BASE_NONE, NULL, 0x0, 
    "Show only Join request messages", HFILL }},
    { &hf_freepastry_join_consistent_join,
	  { "ConsistentJoin", "freepastry.join.v0.consistent_join", 
    FT_NONE, BASE_NONE, NULL, 0x0, 
    "Show only ConsistentJoin messages", HFILL }},
    { &hf_freepastry_routingtable_routerow_req,
	  { "RouteRow request", "freepastry.routingtable.v0.routerow_req", 
    FT_NONE, BASE_NONE, NULL, 0x0, 
    "Show only RouteRow request messages", HFILL }},
    { &hf_freepastry_routingtable_broadcast_routerow,
	  { "BroadcastRouteRow", "freepastry.routingtable.v0.broadcast_routerow", 
    FT_NONE, BASE_NONE, NULL, 0x0, 
    "Show only BroadcastRouteRow messages", HFILL }},
    { &hf_freepastry_leafset_proto_leafset_req,
	  { "Leafset request", "freepastry.leafset_proto.v0.leafset_req", 
    FT_NONE, BASE_NONE, NULL, 0x0, 
    "Show only Leafset request messages", HFILL }},
    { &hf_freepastry_leafset_proto_broadcast_leafset,
	  { "BroadcastLeafset", "freepastry.leafset_proto.v0.broadcast_leafset", 
    FT_NONE, BASE_NONE, NULL, 0x0, 
    "Show only BroadcastLeafset messages", HFILL }},
    { &hf_freepastry_router_sub_address,
    { "Sub message address",	"freepastry.router.route.v0.sub",
    FT_UINT32, BASE_HEX, VALS(freepastry_address), 0x0,
    "Address for messages encapsulated in a route message", HFILL }},
    { &hf_freepastry_router_target,
    { "Target", "freepastry.router.route.v0.target",
    FT_STRING, BASE_NONE, NULL, 0x0,
    "target for a route message", HFILL }},
    { &hf_freepastry_router_has_destination_handle,
    { "Has destination handle",	"freepastry.router.route.v0.has_destination_handle",
    FT_BOOLEAN, 8, NULL, 0x0,
    "True if Route message has a destination handle", HFILL }},
    { &hf_freepastry_direct_nodeid_resp_id_value,
    { "ID value", "freepastry.direct.nodeid_resp.v0.id",
    FT_STRING, BASE_NONE, NULL, 0x0,
    "ID value", HFILL }},
    { &hf_freepastry_direct_nodeid_resp_epoch,
    { "Epoch",	"freepastry.direct.nodeid_resp.v0.epoch",
    FT_UINT64, BASE_DEC, NULL, 0x0,
    "Epoch", HFILL }},
    { &hf_freepastry_direct_routerow_row,
    { "Requested row",	"freepastry.direct.route_row.v0.row",
    FT_UINT32, BASE_DEC, NULL, 0x0,
    "Index of the requested row", HFILL }},
    { &hf_freepastry_direct_routerow_numroutesets,
    { "Number of RouteSets",	"freepastry.direct.route_row.v0.num_route_sets",
    FT_UINT32, BASE_DEC, NULL, 0x0,
    "Number of RouteSets", HFILL }},
    { &hf_freepastry_direct_routerow_notnull,
    { "RouteSet is not null",	"freepastry.direct.route_row.v0.not_null_routeset",
    FT_BOOLEAN, 8, NULL, 0x0,
    "True if the Routeset is not null", HFILL }},
    { &hf_freepastry_direct_sourceroute_numhops,
    { "Number of hops",	"freepastry.direct.source_route.v0.num_hops",
    FT_UINT32, BASE_DEC, NULL, 0x0,
    "Number of hops in the SourceRoute", HFILL }},
    { &hf_freepastry_direct_sourceroute_resp_numsourceroutes,
    { "Number of SourceRoutes",	"freepastry.direct.source_route.v0.num_source_routes",
    FT_UINT32, BASE_DEC, NULL, 0x0,
    "Number of SourceRoutes", HFILL }},
    { &hf_freepastry_join_consistent_join_is_request,
    { "Is request",	"freepastry.direct.join_proto.consistent_join.v0.is_request",
    FT_BOOLEAN, 8, NULL, 0x0,
    "True if the ConsistentJoin message is a request", HFILL }},
    { &hf_freepastry_join_consistent_join_num_failed,
    { "Number of failed set",	"freepastry.join_proto.consistent_join.v0.num_failed_sets",
    FT_UINT32, BASE_DEC, NULL, 0x0,
    "Number of failed set", HFILL }},
    { &hf_freepastry_join_join_req_rtbasebitlength,
    { "RT base bit length",	"freepastry.join_proto.join.v0.rtbasebitlength",
    FT_UINT32, BASE_DEC, NULL, 0x0,
    "RT base bit length", HFILL }},
    { &hf_freepastry_join_join_req_has_join_handle,
    { "Has join handle",	"freepastry.join_proto.join.v0.has_join_handle",
    FT_BOOLEAN, 8, NULL, 0x0,
    "True if Join message has a join handle", HFILL }},
    { &hf_freepastry_join_join_req_last_row,
    { "Number of rows left",	"freepastry.join_proto.join.v0.last_row",
    FT_UINT16, BASE_DEC, NULL, 0x0,
    "The number of rows left to determine", HFILL }},
    { &hf_freepastry_join_join_req_has_row,
    { "Has row",	"freepastry.join_proto.join.v0.has_row",
    FT_BOOLEAN, 8, NULL, 0x0,
    "True if there is a row for the current column", HFILL }},
    { &hf_freepastry_join_join_req_has_col,
    { "Has column",	"freepastry.join_proto.join.v0.has_col",
    FT_BOOLEAN, 8, NULL, 0x0,
    "True if there is an entry", HFILL }},
    { &hf_freepastry_join_join_req_has_leafset,
    { "Has leafset",	"freepastry.join_proto.join.v0.has_leafset",
    FT_BOOLEAN, 8, NULL, 0x0,
    "True is the response contains a leafset", HFILL }},
    { &hf_freepastry_leafset_request_leafset_timestamp,
    { "Timestamp",	"freepastry.leafset_proto.request_leafset.v0.timestamp",
    FT_UINT64, BASE_DEC, NULL, 0x0,
    "Timestamp for LeafsetRequest message", HFILL }},
    { &hf_freepastry_leafset_broadcast_leafset_type,
    { "Type",	"freepastry.leafset_proto.broadcast_leafset.v0.type",
    FT_UINT8, BASE_DEC, NULL, 0x0,
    "Type", HFILL }},
    { &hf_freepastry_leafset_broadcast_leafset_timestamp,
    { "Timestamp",	"freepastry.leafset_proto.broadcast_leafset.v0.timestamp",
    FT_UINT64, BASE_DEC, NULL, 0x0,
    "Timestamp for LeafsetResponse message", HFILL }},
    { &hf_freepastry_routingtable_routerow_req_row,
    { "Row",	"freepastry.route_proto.request_routerow.row",
    FT_UINT8, BASE_DEC, NULL, 0x0,
    "Index of the requested row", HFILL }},
    { &hf_freepastry_routingtable_broadcast_routerow_num_row,
    { "Number of RouteSets",	"freepastry.route_proto.broadcast_routerow.v0.num_routesets",
    FT_UINT8, BASE_DEC, NULL, 0x0,
    "Number of RouteSets", HFILL }},
    { &hf_freepastry_routingtable_broadcast_routerow_notnull,
    { "RouteSet is not null",	"freepastry.route_proto.broadcast_routerow.v0.not_null_routeset",
    FT_BOOLEAN, 8, NULL, 0x0,
    "True if the row has a RouteSet at the current index", HFILL }},
    { &hf_freepastry_msg_pastry_endpoint_priority,
    { "Priority",	"freepastry.commonapi.pastry_endpoint.v0.priority",
    FT_INT8, BASE_DEC, VALS(freepastry_priority), 0x0,
    "Priority", HFILL }}
  };
  
  /* Setup protocol subtree array */
  static gint *ett[] = {
    &ett_freepastry_core_v0,
    &ett_freepastry_core_v0_sr
  };

  module_t *freepastry_core_v0_module;	

  if (proto_freepastry_core_v0 == -1) {
    proto_freepastry_core_v0 = proto_register_protocol (
      "FreePastry Binary Protocol Version 0",
      "FreePastry V0",
      "freepastry_v0");
  }

  freepastry_core_v0_module	= prefs_register_protocol(proto_freepastry_core_v0, NULL);
  proto_register_field_array(proto_freepastry_core_v0, hf, array_length(hf));
  proto_register_subtree_array(ett, array_length(ett));

  subdissector_commonapi_table = register_dissector_table("commonapi.app", "Common API Application",
    FT_UINT16, BASE_HEX);
}


void
proto_reg_handoff_freepastry_core_v0(void)
{
  static int Initialized=FALSE;

  if (!Initialized) {
    freepastry_core_v0_handle = create_dissector_handle(dissect_freepastry_core_v0, 
      proto_freepastry_core_v0);
    dissector_add("freepastry.msg", 0, freepastry_core_v0_handle);
  }
}
