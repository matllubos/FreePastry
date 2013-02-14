/* packet-past.c
* Routines for PAST 
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

#include "packet-past.h"
#include "packet-freepastry.h"

static int proto_past = -1;

static int hf_past_type = -1;
static int hf_past_version = -1;
static int hf_past_msg_id = -1;
static int hf_past_is_response = -1;
static int hf_past_has_content_handle = -1;
static int hf_past_content_handle_type  = -1;
static int hf_past_content_handle_len = -1;
static int hf_past_content_handle  = -1;
static int hf_past_has_content  = -1;
static int hf_past_content_type  = -1;
static int hf_past_content_len = -1;
static int hf_past_content  = -1;
static int hf_past_error_len = -1;
static int hf_past_error_value = -1;
static int hf_past_cached = -1;
static int hf_past_max = -1;
static int hf_past_response_type = -1;
static int hf_past_success = -1;
static int hf_past_has_nodehandleset = -1;
static int hf_past_nodehandleset_type = -1;
static int hf_past_has_nodehandle = -1;

static gint ett_past = -1;


static dissector_handle_t past_handle; 

static const value_string past_msg_type[] = {
  { CACHE_MSG, 	        "Cache" },
  { FETCH_HANDLE_MSG, 	"Fetch Handle" },
  { FETCH_MSG,          "Fetch" },
  { INSERT_MSG, 	      "Insert" },
  { LOOKUP_HANDLES_MSG, "Lookup Handles" }, 
  { LOOKUP_MSG,         "Lookup" },
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


gint
decode_past_content(tvbuff_t *tvb, proto_tree *tree, gint offset)
{
  guint16 content_type;
  guint remaining = tvb_reported_length_remaining(tvb, offset);

  if (remaining < 2){
    proto_tree_add_text(tree, tvb, offset, remaining, "Too short attributes!");
    return -1;
  }

  content_type = tvb_get_ntohs(tvb, offset);
  proto_tree_add_uint(tree, hf_past_content_type, tvb, offset, 2, content_type);

    if (content_type == 0) {
      guint32 content_len;
      if (remaining < 6){/*6 = type + len*/
        proto_tree_add_text(tree, tvb, offset + 2, remaining, "Too short attributes!");
      return -1;
      }
      content_len = (guint32) tvb_get_ntohl(tvb, offset +2);
      proto_tree_add_uint(tree, hf_past_content_len, tvb, offset + 2, 4, content_len);
      if (remaining < (6 + content_len)){
        proto_tree_add_text(tree, tvb, offset + 6, remaining, "Too short attributes!");
        return -1;
      }
      proto_tree_add_text(tree, tvb, offset + 6, content_len,
				    "Past content (%u byte%s)", content_len,
				    plurality(content_len, "", "s"));
      return (offset + content_len + 6);
    } else {
      /*unknown type, cannot dissect more data*/
      proto_tree_add_text(tree, tvb, offset + 6, -1, "Not supported by dissector.");
      return -1;
    }
}

gint
decode_past_content_handle(tvbuff_t *tvb, proto_tree *tree, gint offset)
{
  guint16 content_handle_type;
  guint remaining = tvb_reported_length_remaining(tvb, offset);

  if (remaining < 2){
    proto_tree_add_text(tree, tvb, offset, remaining, "Too short attributes!");
    return -1;
  }

  content_handle_type = tvb_get_ntohs(tvb, offset);
  proto_tree_add_uint(tree, hf_past_content_handle_type, tvb, offset, 2, content_handle_type);

    if (content_handle_type == 0) {
      guint32 content_handle_len;
      if (remaining < 6){/*3 = type + len*/
        proto_tree_add_text(tree, tvb, offset + 2, remaining, "Too short attributes!");
      return -1;
      }
      content_handle_len = (guint32) tvb_get_ntohl(tvb, offset +2);
      proto_tree_add_uint(tree, hf_past_content_handle_len, tvb, offset + 2, 4, content_handle_len);
      if (remaining < (6 + content_handle_len)){
        proto_tree_add_text(tree, tvb, offset + 6, remaining, "Too short attributes!");
        return -1;
      }
      proto_tree_add_text(tree, tvb, offset + 6, content_handle_len,
				    "Past handle content (%u byte%s)", content_handle_len,
				    plurality(content_handle_len, "", "s"));
      return (offset + content_handle_len + 6);
    } else {
      /*unknown type, cannot dissect more data*/
      proto_tree_add_text(tree, tvb, offset + 6, -1, "Not supported by dissector.");
      return -1;
    }
}

gint
decode_past_error(tvbuff_t *tvb, proto_tree *tree, gint offset)
{
  guint32 error_len;
  guint32 remaining = (guint32) tvb_reported_length_remaining(tvb, offset);

  if (remaining < 4){
    proto_tree_add_text(tree, tvb, offset, remaining, "Too short attributes!");
    return -1;
  }
  error_len = tvb_get_ntohl(tvb, offset);
  proto_tree_add_uint(tree, hf_past_error_len, tvb, offset, 4, error_len);

  if (error_len > 0){
    if (remaining < (4 + error_len)){
      proto_tree_add_text(tree, tvb, offset + 4, remaining, "Too short attributes!");
      return -1;
    }
    proto_tree_add_text(tree, tvb, offset + 4, error_len,
				    "Error (%u byte%s)", error_len,
				    plurality(error_len, "", "s"));
  }
  return (offset + error_len + 4);
}

static void
decode_cache_msg(tvbuff_t *tvb, proto_tree *tree, gint offset)
{
  if (tree){
    decode_past_content(tvb, tree, offset);
  }
}

static void
decode_fetch_handle_msg(tvbuff_t *tvb, proto_tree *tree, gint offset)
{
  if (tree){
    guint8 has_content_handle = tvb_get_guint8(tvb, offset);
    proto_tree_add_item(tree, hf_past_has_content_handle, tvb, offset, 1, FALSE);
    switch (has_content_handle){
      case 0:
        offset++;
        break;
      case 1:
        offset = decode_past_content_handle(tvb, tree, offset + 1);
        break;
      case 2:
        offset = decode_past_error(tvb, tree, offset + 1);
        break;
      case 3:
      default:
        proto_tree_add_text(tree, tvb, offset + 6, -1, "Not supported by dissector.");
        return;
    }
    if (offset != -1){
      decode_type_and_id(tvb, tree, offset);
    }
  }
}

static void
decode_fetch_msg(tvbuff_t *tvb, proto_tree *tree, gint offset)
{
  if (tree){
    guint8 has_content = tvb_get_guint8(tvb, offset);
    proto_tree_add_item(tree, hf_past_has_content, tvb, offset, 1, FALSE);
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
    }
    if (offset != -1){
      gint remaining = tvb_reported_length_remaining(tvb, offset);
      if (remaining < 1){
        proto_tree_add_text(tree, tvb, offset, remaining, "Too short attributes!");
        return;
      }
      proto_tree_add_item(tree, hf_past_cached, tvb, offset, 1, FALSE);
      offset = decode_past_content_handle(tvb, tree, offset + 1);
    }
  }
}

static void
decode_insert_msg(tvbuff_t *tvb, proto_tree *tree, gint offset)
{
  if (tree){
    guint8 response_type = tvb_get_guint8(tvb, offset);
    proto_tree_add_item(tree, hf_past_response_type, tvb, offset, 1, FALSE);
    switch (response_type){
       case 0:
         offset++;
         break;
       case 1:
         proto_tree_add_item(tree, hf_past_success, tvb, offset + 1, 1, FALSE);
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
      proto_tree_add_item(tree, hf_past_has_content, tvb, offset, 1, FALSE);
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
    }/*end offset == -1*/
  }/*end if tree != NULL*/
}

static void
decode_lookup_handle_message(tvbuff_t *tvb, proto_tree *tree, gint offset)
{
  if (tree){
    guint8 has_nodehandleset = tvb_get_guint8(tvb, offset);
    switch (has_nodehandleset){
      guint16 set_type;
       case 0:
         offset++;
         break;
       case 1:
         set_type = tvb_get_ntohs(tvb, offset + 1);
         proto_tree_add_uint(tree, hf_past_nodehandleset_type, tvb, offset + 1, 2, set_type);
         /*Decode the NodeHandleSet*/
         switch (set_type) {
          case 1:/*Normal NodeHandleSet*/
            offset += decode_nodehandleset(tvb, tree, offset + 3, "NodeHandleSet");
            break;
          case 10:/*Multiring NodeHandleSet*/
            offset += decode_multiring_nodehandleset(tvb, tree, offset + 3, "MultiringNodeHandleSet");
            break;
          default:
            proto_tree_add_text(tree, tvb, offset + 6, -1, "NodeHandleSet not supported by dissector.");
          return;
         }
         break;
       case 2:
         offset = decode_past_error(tvb, tree, offset + 1);
         break;
       default:
         proto_tree_add_text(tree, tvb, offset + 6, -1, "Not supported by dissector.");
         return;
    }
    if (offset != -1){
      if (tvb_reported_length_remaining(tvb, offset) < 4){
        proto_tree_add_text(tree, tvb, offset, -1, "Too short attributes!");
        return;
      }
      proto_tree_add_item(tree, hf_past_max, tvb, offset, 4, FALSE);
      decode_type_and_id(tvb, tree, offset + 4);
      
    }
  }
}

static void
decode_lookup_msg(tvbuff_t *tvb, proto_tree *tree, gint offset)
{
  if (tree){
    guint8 has_content = tvb_get_guint8(tvb, offset);
    proto_tree_add_item(tree, hf_past_has_content, tvb, offset, 1, FALSE);
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
    }
    if (offset != -1){
      guint8 has_node_handle;
      if (tvb_reported_length_remaining(tvb, offset) < 1){
        proto_tree_add_text(tree, tvb, offset, -1, "Too short attributes!");
        return;
      }
      has_node_handle = tvb_get_guint8(tvb, offset);
      proto_tree_add_item(tree, hf_past_has_nodehandle, tvb, offset, 1, FALSE);
      offset++;
      if (has_node_handle != 0){
        offset = decode_nodehandle(tvb, tree, offset, "Handle");
        if (offset == -1){
          return;
        }
      }

      offset = decode_type_and_id(tvb, tree, offset);
      if (offset != -1) {
        if (tvb_reported_length_remaining(tvb, offset) < 1){
          proto_tree_add_text(tree, tvb, offset, -1, "Missing attribute!");
        } else {
          proto_tree_add_item(tree, hf_past_cached, tvb, offset, 1, FALSE);
        }
      }
    }
  }
}

static void
dissect_past(tvbuff_t *tvb, packet_info *pinfo, proto_tree *tree)
{
  proto_item *ti = NULL;
	proto_tree *past_tree = NULL;
  guint16 type;
  const gchar *type_string = NULL;
  gint offset = 0;
  gint offset_dest = 0;

  if (check_col(pinfo->cinfo, COL_PROTOCOL)) 
			col_set_str(pinfo->cinfo, COL_PROTOCOL, "PAST");

  type = tvb_get_ntohs(tvb, offset);
  type_string = val_to_str(type, past_msg_type, "<Unknown type %d>");

  if (check_col(pinfo->cinfo, COL_INFO)){
    col_clear (pinfo->cinfo, COL_INFO);
    col_add_fstr(pinfo->cinfo, COL_INFO, "%d > %d %s",
      pinfo->srcport, pinfo->destport, type_string);
  }
  
  /*7 = 2 + 1 + 4 (args before ID)*/
  offset_dest = offset + 7;

  if (tree){
    ti = proto_tree_add_item(tree, proto_past, tvb, 0, -1, FALSE);
    past_tree = proto_item_add_subtree(ti, ett_past);
    proto_tree_add_item(past_tree, hf_past_type, tvb, offset, 2, FALSE);
    offset += 2;
    proto_tree_add_item(past_tree, hf_past_version, tvb, offset, 1, FALSE);
    offset++;
    proto_tree_add_item(past_tree, hf_past_msg_id, tvb, offset, 4, FALSE);
    offset = decode_type_and_id(tvb, past_tree, offset_dest);
    if (offset == -1){
      return;
    }
    offset = decode_nodehandle(tvb, past_tree, offset, "Source");
    if (offset == -1){
      return;
    }
    proto_tree_add_item(past_tree, hf_past_is_response, tvb, offset, 1, FALSE);
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
    case CACHE_MSG:
      decode_cache_msg(tvb, past_tree, offset);
      break;
    case FETCH_HANDLE_MSG:
      decode_fetch_handle_msg(tvb, past_tree, offset);
      break;
    case FETCH_MSG:
      decode_fetch_msg(tvb, past_tree, offset);
      break;
    case INSERT_MSG:
      decode_insert_msg(tvb, past_tree, offset);
      break;
    case LOOKUP_HANDLES_MSG:
      decode_lookup_handle_message(tvb, past_tree, offset);
      break;
    case LOOKUP_MSG:
      decode_lookup_msg(tvb, past_tree, offset);
      break;
    default:
      return;/*stop dissection*/
  }

}


void
proto_register_past(void)
{
  
 static hf_register_info hf[] = {
    { &hf_past_type,
    { "Type",	"past.type",
    FT_UINT16, BASE_DEC, VALS(past_msg_type), 0x0,
    "", HFILL }},
    { &hf_past_version,
    { "Version",		"past.version",
    FT_UINT8, BASE_DEC, NULL, 0x0,
    "", HFILL }},
    { &hf_past_msg_id,
    { "Message ID",	"past.msg_id",
    FT_UINT32, BASE_DEC, NULL, 0x0,
    "", HFILL }},
    { &hf_past_is_response,
    { "Is response",	"past.is_response",
    FT_BOOLEAN, 8, NULL, 0x0,
    "", HFILL }},
    { &hf_past_has_content_handle,
    { "Has content handle",		"past.contenthandle",
    FT_UINT8, BASE_DEC, VALS(has_content_values), 0x0,
    "", HFILL }},
    { &hf_past_content_handle_type,
    { "Content handle type",		"past.contenthandle.type",
    FT_UINT16, BASE_DEC, NULL, 0x0,
    "", HFILL }},
    { &hf_past_content_handle_len,
    { "Content handle length",		"past.contenthandle.len",
    FT_UINT32, BASE_DEC, NULL, 0x0,
    "", HFILL }},
    { &hf_past_content_handle,
    { "Content handle",		"past.contenthandle.value",
    FT_BYTES, BASE_HEX, NULL, 0x0,
    "", HFILL }},
    { &hf_past_has_content,
    { "Has content",		"past.content",
    FT_UINT8, BASE_DEC, VALS(has_content_values), 0x0,
    "", HFILL }},
    { &hf_past_content_type,
    { "Content type",		"past.content.type",
    FT_UINT16, BASE_DEC, NULL, 0x0,
    "", HFILL }},
    { &hf_past_content_len,
    { "Content length",		"past.content.len",
    FT_UINT32, BASE_DEC, NULL, 0x0,
    "", HFILL }},
    { &hf_past_content,
    { "Content",		"past.content.value",
    FT_BYTES, BASE_HEX, NULL, 0x0,
    "", HFILL }},
    { &hf_past_error_len,
    { "Message ID",	"past.error.len",
    FT_UINT32, BASE_DEC, NULL, 0x0,
    "", HFILL }},
    { &hf_past_error_value,
    { "Error value",	"past.error.value",
    FT_BYTES, BASE_HEX, NULL, 0x0,
    "", HFILL }},
    { &hf_past_cached,
    { "Cached", "past.cached",
    FT_BOOLEAN, 8, NULL, 0x0,
    "", HFILL }},
    { &hf_past_max,
    { "Max. replicas", "past.cached",
    FT_UINT32, BASE_DEC, NULL, 0x0,
    "", HFILL }},
    { &hf_past_response_type,
    { "Response type", "past.response_type",
    FT_UINT8, BASE_DEC, VALS(response_type_values), 0x0,
    "", HFILL }},
    { &hf_past_success,
    { "Success", "past.success",
    FT_BOOLEAN, 8, NULL, 0x0,
    "", HFILL }},
    { &hf_past_has_nodehandleset,
    { "Has NodeHandleSet",		"past.nodehandleset",
    FT_UINT8, BASE_DEC, VALS(has_nodehandleset_values), 0x0,
    "", HFILL }},
    { &hf_past_nodehandleset_type,
    { "NodeHandleSet type",		"past.nodehandleset.type",
    FT_UINT16, BASE_DEC, NULL, 0x0,
    "", HFILL }},
    { &hf_past_has_nodehandle,
    { "Has NodeHandle", "past.hasnodehandle",
    FT_BOOLEAN, 8, NULL, 0x0,
    "", HFILL }},
  };

  /* Setup protocol subtree array */
  static gint *ett[] = {
    &ett_past
  };

  module_t *past_module;	

  if (proto_past == -1) {
    proto_past = proto_register_protocol (
      "Past Common API Application",	/* name */
      "Past", /* short name */
      "past"	/* abbrev */
      );
  }
  past_module	= prefs_register_protocol(proto_past, NULL);
  proto_register_field_array(proto_past, hf, array_length(hf));
  proto_register_subtree_array(ett, array_length(ett));
}


void
proto_reg_handoff_past(void)
{
  static int Initialized=FALSE;
  if (!Initialized) {
    past_handle = create_dissector_handle(dissect_past, proto_past);
    dissector_add("commonapi.app", PAST_SUB_ADDRESS, past_handle);
  }
}
