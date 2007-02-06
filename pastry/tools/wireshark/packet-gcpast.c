/* packet-gcpast.c
* Routines for GCPAST 
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

#include "packet-gcpast.h"
#include "packet-past.h"
#include "packet-freepastry.h"

static int proto_gcpast = -1;

static int hf_gcpast_type = -1;
static int hf_gcpast_version = -1;
static int hf_gcpast_dest_type = -1;
static int hf_gcpast_dest = -1;
static int hf_gcpast_msg_id = -1;
static int hf_gcpast_is_response = -1;
static int hf_gcpast_has_content  = -1;
static int hf_gcpast_max = -1;
static int hf_gcpast_response_type = -1;
static int hf_gcpast_success = -1;
static int hf_gcpast_has_nodehandleset = -1;
static int hf_gcpast_nodehandleset_type = -1;
static int hf_gcpast_expiration = -1;
static int hf_gcpast_num_responses = -1;
static int hf_gcpast_num_keys = -1;

static gint ett_gcpast = -1;

static dissector_handle_t gcpast_handle; 

static const value_string gcpast_msg_type[] = {
  { GC_INSERT_MSG,          "GCInsert"},
  { GC_LOOKUP_HANDLES_MSG, 	"GCLookup Handles"},
  { GC_REFRESH_MSG, 	      "GCRefresh"},
  { 0, NULL }
};

static const value_string has_content_values[] = {
  { 0, 	"Empty" },
  { 1, 	"True" },
  { 2,  "Error" },
  { 3, "Platform Dependent" },
  { 0, NULL }
};

static const value_string response_type_values[] = {
  { 0, 	"Empty" },
  { 1, 	"Non-Empty" },
  { 2,  "Error" },
  { 0, NULL }
};

static const value_string has_nodehandleset_values[] = {
  { 0, 	"Empty" },
  { 1, 	"Non-Empty" },
  { 2,  "Error" },
  { 0, NULL }
};

static void
decode_gc_insert_msg(tvbuff_t *tvb, proto_tree *tree, gint offset)
{
  if (tree){
    guint8 response_type = tvb_get_guint8(tvb, offset);
    proto_tree_add_item(tree, hf_gcpast_response_type, tvb, offset, 1, FALSE);
    switch (response_type){
       case 0:
         offset++;
         break;
       case 1:
         proto_tree_add_item(tree, hf_gcpast_success, tvb, offset + 1, 1, FALSE);
         offset += 2;
         break;
       case 2:
         offset = decode_past_error(tvb, tree, offset + 1);
         break;
       default:
         proto_tree_add_text(tree, tvb, offset + 6, -1, "Not supported by dissector.");
         return;
    }
    if (offset != -1){
      guint8 has_content = tvb_get_guint8(tvb, offset);
      proto_tree_add_item(tree, hf_gcpast_has_content, tvb, offset, 1, FALSE);
      switch (has_content){
        case 0:
          offset++;
          break;
        case 1:
          offset = decode_past_content(tvb, tree, offset + 1);
          break;
        case 2:
          offset = decode_past_error(tvb, tree, offset + 1);
          break;
        case 3:
        default:
          proto_tree_add_text(tree, tvb, offset + 6, -1, "Not supported by dissector.");
          return;
      }/*end switch*/
    }/*end offset != -1*/
    proto_tree_add_item(tree, hf_gcpast_expiration, tvb, offset, 8, FALSE);
  }/*end if tree != NULL*/
}

static void
decode_gc_lookup_handles_message(tvbuff_t *tvb, proto_tree *tree, gint offset)
{
  if (tree){
    guint8 has_nodehandleset = tvb_get_guint8(tvb, offset);
    switch (has_nodehandleset){
       case 0:
         offset++;
         break;
       case 1:
         proto_tree_add_item(tree, hf_gcpast_nodehandleset_type, tvb, offset + 1, 2, FALSE);
         offset = decode_nodehandleset(tvb, tree, offset + 3, "Set");
         break;
       case 2:
         offset = decode_past_error(tvb, tree, offset + 1);
         break;
       default:
         proto_tree_add_text(tree, tvb, offset + 6, -1, "Not supported by dissector.");
         return;
    }
    if (offset != -1){
      gint remaining = tvb_reported_length_remaining(tvb, offset);
      if (remaining < 26){
        proto_tree_add_text(tree, tvb, offset, remaining, "Too short attributes!");
        return;
      }
      proto_tree_add_item(tree, hf_gcpast_max, tvb, offset, 4, FALSE);
      decode_gcid(tvb, tree, offset + 4);
    }
  }
}

static void
decode_gc_refresh_msg(tvbuff_t *tvb, proto_tree *tree, gint offset)
{
  if (tree){
    guint32 num_keys;
    guint32 j;
    guint8 response_type = tvb_get_guint8(tvb, offset);
    proto_tree_add_item(tree, hf_gcpast_response_type, tvb, offset, 1, FALSE);
    if (response_type  == 0){
      offset++;
    } else if (response_type == 1){
      guint32 i;
      guint32 num_response;

      offset++;
      num_response = tvb_get_ntohl(tvb, offset);
      proto_tree_add_uint(tree, hf_gcpast_num_responses, tvb, offset , 4, num_response);
      offset += 4;

      for (i = 0; i < num_response; ++i){ 
        proto_tree_add_item(tree, hf_gcpast_success, tvb, offset, 1, FALSE);
        offset++;
      }
    } else if (response_type == 2){
      offset = decode_past_error(tvb, tree, offset + 1);
    } else {
      proto_tree_add_text(tree, tvb, offset + 6, -1, "Not supported by dissector.");
      return;
    }

    num_keys = tvb_get_ntohl(tvb, offset);
    proto_tree_add_uint(tree, hf_gcpast_num_keys, tvb, offset , 4, num_keys);
    offset += 4;

    for (j = 0; j < num_keys; ++j){
      offset = decode_gcid(tvb, tree, offset);
      if (offset == -1){
        return;
      }
    }
  }
}

static void
dissect_gcpast(tvbuff_t *tvb, packet_info *pinfo, proto_tree *tree)
{
  proto_item *ti = NULL;
	proto_tree *gcpast_tree = NULL;
  guint16 type;
  const gchar *type_string = NULL;
  gint offset = 0;
  gint offset_dest = 0;

  if (check_col(pinfo->cinfo, COL_PROTOCOL)) 
			col_set_str(pinfo->cinfo, COL_PROTOCOL, "GCPAST");

  type = tvb_get_ntohs(tvb, offset);
  type_string = val_to_str(type, gcpast_msg_type, "<Unknown type %d>");

  if (check_col(pinfo->cinfo, COL_INFO)){
    col_clear (pinfo->cinfo, COL_INFO);
    col_add_fstr(pinfo->cinfo, COL_INFO, "%d > %d %s",
      pinfo->srcport, pinfo->destport, type_string);
  }
  
  /*7 = 2 + 1 + 4 (args before ID)*/
  offset_dest = offset + 7;

  if (tree){
    ti = proto_tree_add_item(tree, proto_gcpast, tvb, 0, -1, FALSE);
    gcpast_tree = proto_item_add_subtree(ti, ett_gcpast);
    proto_tree_add_item(gcpast_tree, hf_gcpast_type, tvb, offset, 2, FALSE);
    proto_tree_add_item(gcpast_tree, hf_gcpast_version, tvb, offset + 2, 1, FALSE);
    proto_tree_add_item(gcpast_tree, hf_gcpast_msg_id, tvb, offset + 3, 4, FALSE);
    offset = decode_type_and_id(tvb, gcpast_tree, offset_dest);
    if (offset == -1){
      return;
    }
    offset = decode_nodehandle(tvb, gcpast_tree, offset , "Source");
    if (offset == -1){
      return;
    }
    proto_tree_add_item(gcpast_tree, hf_gcpast_is_response, tvb, offset, 1, FALSE);
  } else {
    offset = get_node_handle_len(tvb, offset + 29);
    if (offset == -1){
      return;
    }
  }

  if(check_col(pinfo->cinfo,COL_INFO)){
    if (tvb_get_guint8(tvb,offset) == 0){
      print_id_into_col_info(tvb, pinfo, offset_dest, "Request");
    } else {
      print_id_into_col_info(tvb, pinfo, offset_dest, "Response");
    }
  }

  offset++;

  switch (type){
    case GC_INSERT_MSG:
      decode_gc_insert_msg(tvb, gcpast_tree, offset);
      break;
    case GC_LOOKUP_HANDLES_MSG:
      decode_gc_lookup_handles_message(tvb, gcpast_tree, offset);
      break;
    case GC_REFRESH_MSG:
      decode_gc_refresh_msg(tvb, gcpast_tree, offset);
      break;
    default:
      return;/*stop dissection*/
  }
}

void
proto_register_gcpast(void)
{
  
 static hf_register_info hf[] = {
    { &hf_gcpast_type,
    { "Type",	"gcpast.type",
    FT_UINT16, BASE_DEC, VALS(gcpast_msg_type), 0x0,
    "", HFILL }},
    { &hf_gcpast_version,
    { "Version",		"gcpast.version",
    FT_UINT8, BASE_DEC, NULL, 0x0,
    "", HFILL }},
    { &hf_gcpast_msg_id,
    { "Message ID",	"gcpast.msg_id",
    FT_UINT32, BASE_DEC, NULL, 0x0,
    "", HFILL }},
    { &hf_gcpast_dest_type,
    { "Destination Type",	"gcpast.dest_type",
    FT_UINT16, BASE_DEC, NULL, 0x0,
    "", HFILL }},
    { &hf_gcpast_dest,
    { "Destination ID", "gcpast.dest_id",
    FT_STRING, BASE_NONE, NULL, 0x0,
    "", HFILL }},
    { &hf_gcpast_is_response,
    { "Is Response",	"gcpast.is_response",
    FT_BOOLEAN, 8, NULL, 0x0,
    "", HFILL }},
    { &hf_gcpast_has_content,
    { "Has Content",		"gcpast.content",
    FT_UINT8, BASE_DEC, VALS(has_content_values), 0x0,
    "", HFILL }},
    { &hf_gcpast_max,
    { "Max. Replicas", "gcpast.cached",
    FT_UINT32, BASE_DEC, NULL, 0x0,
    "", HFILL }},
    { &hf_gcpast_response_type,
    { "Response Type", "gcpast.response_type",
    FT_UINT8, BASE_DEC, VALS(response_type_values), 0x0,
    "", HFILL }},
    { &hf_gcpast_success,
    { "Success", "gcpast.success",
    FT_BOOLEAN, 8, NULL, 0x0,
    "", HFILL }},
    { &hf_gcpast_has_nodehandleset,
    { "Has Node Handle Set",		"gcpast.nodehandleset",
    FT_UINT8, BASE_DEC, VALS(has_nodehandleset_values), 0x0,
    "", HFILL }},
    { &hf_gcpast_nodehandleset_type,
    { "Node Handle Set Type",		"gcpast.nodehandleset.type",
    FT_UINT16, BASE_DEC, NULL, 0x0,
    "", HFILL }},
    { &hf_gcpast_expiration,
    { "Expire",	"gcpast.expiration",
    FT_UINT64, BASE_DEC, NULL, 0x0,
    "", HFILL }},
    { &hf_gcpast_num_responses,
    { "Number of responses", "gcpast.numresponse",
    FT_UINT32, BASE_DEC, NULL, 0x0,
    "", HFILL }},
    { &hf_gcpast_num_keys,
    { "Number of keys", "gcpast.numkey",
    FT_UINT32, BASE_DEC, NULL, 0x0,
    "", HFILL }}
  };

  /* Setup protocol subtree array */
  static gint *ett[] = {
    &ett_gcpast
  };

  module_t *gcpast_module;	

  if (proto_gcpast == -1) {
    proto_gcpast = proto_register_protocol (
      "GCPast Common API Application",	/* name */
      "GCPast", /* short name */
      "gcpast"	/* abbrev */
      );
  }
  gcpast_module	= prefs_register_protocol(proto_gcpast, NULL);
  proto_register_field_array(proto_gcpast, hf, array_length(hf));
  proto_register_subtree_array(ett, array_length(ett));
}


void
proto_reg_handoff_gcpast(void)
{
  static int Initialized=FALSE;
  if (!Initialized) {
    gcpast_handle = create_dissector_handle(dissect_gcpast, proto_gcpast);
    dissector_add("commonapi.app", GCPAST_SUB_ADDRESS, gcpast_handle);
  }
}
