/* packet-scribe.c
* Routines for Scribe 
* Copyright 2007, David Dugoujon <david.dugoujon@yahoo.fr>
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

#include "packet-scribe.h"
#include "packet-freepastry.h"

static int proto_scribe = -1;

static int hf_scribe_type = -1;
static int hf_scribe_version = -1;
static int hf_scribe_has_source = -1;
static int hf_scribe_has_content = -1;
static int hf_scribe_content_type = -1;
static int hf_scribe_content = -1;
static int hf_scribe_to_visit_len = -1;
static int hf_scribe_visited_len = -1;
static int hf_scribe_id = -1;
static int hf_scribe_has_previous_parent = -1;
static int hf_scribe_previous_parent_type = -1;
static int hf_scribe_previous_parent = -1;
static int hf_scribe_path_len = -1;
static int hf_scribe_path_type = -1;
static int hf_scribe_path = -1;

static gint ett_scribe = -1;

static dissector_handle_t scribe_handle; 

static const value_string scribe_msg_type[] = {
  { SCRIBE_ANYCAST_MSG,  "Anycast"},
  { SCRIBE_SUBSCRIBE_MSG, "Subscribe"},
  { SCRIBE_SUBSCRIBE_ACK_MSG, "Subscribe Ack"},
  { SCRIBE_SUBSCRIBE_FAILED_MSG, "Subscribe Failed"},
  { SCRIBE_DROP_MSG, "Drop"},
  { SCRIBE_PUBLISH_MSG, "Publish"},
  { SCRIBE_PUBLISH_REQUEST_MSG, "Publish Request"},
  { SCRIBE_UNSUBSCRIBE_MSG, "Unsubscribe"},
  { 0, NULL }
};

void
decode_content(tvbuff_t *tvb, proto_tree *tree, gint offset)
{
    gint payload_len;
    proto_tree_add_item(tree, hf_scribe_content_type, tvb, offset, 2, FALSE);
    offset += 2;
    payload_len = tvb_reported_length_remaining(tvb, offset);
    proto_tree_add_text(tree, tvb, offset, payload_len,
				    "Payload (%u byte%s)", payload_len,
				    plurality(payload_len, "", "s"));
}

gint
decode_visit(tvbuff_t *tvb, proto_tree *tree, gint offset)
{
    guint32 to_visit_len;
    guint32 visited_len;
    guint32 i;

    to_visit_len = tvb_get_ntohl(tvb, offset);
    proto_tree_add_uint(tree, hf_scribe_to_visit_len, tvb, offset, 4, to_visit_len);
    offset += 4;
    for (i = 0; i < to_visit_len; ++i){
      offset = decode_nodehandle(tvb, tree, offset, ep_strdup_printf("NodeHandle to visit #%d", i+1));
      if (offset == -1){
        return -1;
      }
    }/*end for each to_visit*/

    visited_len = tvb_get_ntohl(tvb, offset);
    proto_tree_add_uint(tree, hf_scribe_visited_len, tvb, offset, 4, visited_len);
    offset += 4;
    for (i = 0; i < visited_len; ++i){
      offset = decode_nodehandle(tvb, tree, offset, ep_strdup_printf("Visited NodeHandle #%d", i+1));
      if (offset == -1){
        return -1;
      }
    }/*end for each visited*/
    return offset;
}

void
decode_anycast(tvbuff_t *tvb, proto_tree *tree, gint offset)
{
  if (tree){
    gboolean has_content = FALSE;

    /*visit*/
    offset = decode_visit(tvb, tree, offset);
    if (offset == -1){
        return;
    }

    /*has content?*/
    if (tvb_get_guint8(tvb, offset) != 0){
      has_content = TRUE;
    }
    proto_tree_add_boolean(tree, hf_scribe_has_content, tvb, offset, 1, has_content);
    offset++;

    if (has_content){
      decode_content(tvb, tree, offset);
    }
  }
}

void
decode_subscribe(tvbuff_t *tvb, proto_tree *tree, gint offset)
{
  if (tree){
    gboolean has_content = FALSE;
    gboolean has_previous_parent = FALSE;

    /*visit*/
    offset = decode_visit(tvb, tree, offset);
    if (offset == -1){
        return;
    }

    /*has content?*/
    if (tvb_get_guint8(tvb, offset) != 0){
      has_content = TRUE;
    }
    proto_tree_add_boolean(tree, hf_scribe_has_content, tvb, offset, 1, has_content);
    offset++;

    if (has_content){
      decode_content(tvb, tree, offset);
    } else {
      proto_tree_add_item(tree, hf_scribe_id, tvb, offset, 4, FALSE);
      offset += 4;
      /*has previous parent?*/
      if (tvb_get_guint8(tvb, offset) != 0){
        has_previous_parent = TRUE;
      }
      proto_tree_add_boolean(tree, hf_scribe_has_previous_parent, tvb, offset, 1, has_previous_parent);
      offset++;

      if (has_previous_parent){
        proto_tree_add_item(tree, hf_scribe_previous_parent_type, tvb, offset, 2, FALSE);
        offset += 2;
        proto_tree_add_string(tree, hf_scribe_previous_parent, tvb, offset, 20, get_id_full(tvb, offset));
        offset +=20;
      }

      decode_nodehandle(tvb, tree, offset, "Subscriber");
    }
  }
}


void
decode_scribe_subscribe_ack(tvbuff_t *tvb, proto_tree *tree, gint offset)
{
  if (tree){
    guint32 path_len;
    guint32 i;

    proto_tree_add_item(tree, hf_scribe_id, tvb, offset, 4, FALSE);
    offset += 4;

    path_len = tvb_get_ntohl(tvb, offset);
    proto_tree_add_item(tree, hf_scribe_path_len, tvb, offset, 4, FALSE);
    offset += 4;

    for (i = 0; i < path_len; ++i){
      if (tvb_reported_length_remaining(tvb, offset) < 20){
        proto_tree_add_text(tree, tvb, offset, -1, "Malformed Scribe message!");
        return;
      }
      proto_tree_add_item(tree, hf_scribe_path_type, tvb, offset, 2, FALSE);
      offset += 2;
      proto_tree_add_string(tree, hf_scribe_path, tvb, offset, 20, get_id_full(tvb, offset));
      offset += 20;
    }
  }
}

void
decode_scribe_failed(tvbuff_t *tvb, proto_tree *tree, gint offset)
{
  if (tree){
    proto_tree_add_item(tree, hf_scribe_id, tvb, offset, 4, FALSE);
    offset += 4;
  }
}

static void
dissect_scribe(tvbuff_t *tvb, packet_info *pinfo, proto_tree *tree)
{
  proto_item *ti = NULL;
	proto_tree *scribe_tree = NULL;
  const gchar *type_string = NULL;
  guint16 type;
  gboolean has_source = FALSE;
  gint offset = 0;

  if (check_col(pinfo->cinfo, COL_PROTOCOL)) 
			col_set_str(pinfo->cinfo, COL_PROTOCOL, "Scribe");

  type = tvb_get_ntohs(tvb, offset);
  type_string = val_to_str(type, scribe_msg_type, "<Unknown type %d>");

  if (check_col(pinfo->cinfo, COL_INFO)){
    col_clear (pinfo->cinfo, COL_INFO);
    col_add_fstr(pinfo->cinfo, COL_INFO, "%d > %d %s",
      pinfo->srcport, pinfo->destport, type_string);
  }
  
  /*has source?*/
  if (tvb_get_guint8(tvb, offset+3) != 0){
    has_source = TRUE;
  }

  if (tree){
    ti = proto_tree_add_item(tree, proto_scribe, tvb, 0, -1, FALSE);
    scribe_tree = proto_item_add_subtree(ti, ett_scribe);
    proto_tree_add_item(scribe_tree, hf_scribe_type, tvb, offset, 2, FALSE);
    proto_tree_add_item(scribe_tree, hf_scribe_version, tvb, offset + 2, 1, FALSE);
    proto_tree_add_boolean(scribe_tree, hf_scribe_has_source, tvb, offset + 3, 1, has_source);
    if (has_source){
      offset = decode_nodehandle(tvb, scribe_tree, offset + 4 , "Source");
    }
  } else {
    if (has_source){
      offset = get_node_handle_len(tvb, offset + 3);
    }
  }
  if (offset == -1){
    return;
  }

  if(check_col(pinfo->cinfo,COL_INFO)){
    print_id_into_col_info(tvb, pinfo, offset, "Topic");
  }

  if (tree){
    offset = decode_type_and_id(tvb, scribe_tree, offset);
    if (offset == -1){
      return;
    }

    switch (type){
      case SCRIBE_ANYCAST_MSG:
        decode_anycast(tvb, scribe_tree, offset);
        break;
      case SCRIBE_SUBSCRIBE_MSG:
        decode_subscribe(tvb, scribe_tree, offset);
        break;
      case SCRIBE_SUBSCRIBE_ACK_MSG:
        decode_scribe_subscribe_ack(tvb, scribe_tree, offset);
        break;
      case SCRIBE_SUBSCRIBE_FAILED_MSG:
        decode_scribe_failed(tvb, scribe_tree, offset);
        break;
      case SCRIBE_PUBLISH_MSG:
      case SCRIBE_PUBLISH_REQUEST_MSG:
        decode_content(tvb, scribe_tree, offset);
        break;
      case SCRIBE_DROP_MSG:
      case SCRIBE_UNSUBSCRIBE_MSG:
      default:
        return;/*stop dissection*/
    }
  }
}

void
proto_register_scribe(void)
{

 static hf_register_info hf[] = {
    { &hf_scribe_type,
    { "Type",	"scribe.type",
    FT_UINT16, BASE_DEC, VALS(scribe_msg_type), 0x0,
    "", HFILL }},
    { &hf_scribe_version,
    { "Version",		"scribe.version",
    FT_UINT8, BASE_DEC, NULL, 0x0,
    "", HFILL }},
    { &hf_scribe_has_source,
    { "Has source",	"scribe.has_source",
    FT_BOOLEAN, 8, NULL, 0x0,
    "", HFILL }},
    { &hf_scribe_has_content,
    { "Has content",	"scribe.has_content",
    FT_BOOLEAN, 8, NULL, 0x0,
    "", HFILL }},
    { &hf_scribe_content_type,
    { "Content type",	"scribe.content.type",
    FT_UINT16, BASE_DEC, NULL, 0x0,
    "", HFILL }},
    { &hf_scribe_content,
    { "Payload", "scribe.content.value",
    FT_BYTES, BASE_HEX, NULL, 0x0,
    "", HFILL }},
    { &hf_scribe_to_visit_len,
    { "Number of nodes to visit",	"scribe.tovisit.len",
    FT_UINT32, BASE_DEC, NULL, 0x0,
    "", HFILL }},
    { &hf_scribe_visited_len,
    { "Number of visited nodes",	"scribe.visited.len",
    FT_UINT32, BASE_DEC, NULL, 0x0,
    "", HFILL }},
    { &hf_scribe_id,
    { "Request ID",	"scribe.id",
    FT_UINT32, BASE_DEC, NULL, 0x0,
    "", HFILL }},
    { &hf_scribe_has_previous_parent,
    { "Has previous parent",	"scribe.has_previous_parent",
    FT_BOOLEAN, 8, NULL, 0x0,
    "", HFILL }},
    { &hf_scribe_previous_parent_type,
    { "Path type",	"scribe.previous_parent.type",
    FT_UINT16, BASE_DEC, NULL, 0x0,
    "", HFILL }},
    { &hf_scribe_previous_parent,
    { "Previous parent", "scribe.previous_parent.value",
    FT_STRING, BASE_NONE, NULL, 0x0,
    "", HFILL }},
    { &hf_scribe_path_len,
    { "Lengh of path",	"scribe.length_of_path",
    FT_UINT32, BASE_DEC, NULL, 0x0,
    "", HFILL }},
    { &hf_scribe_path_type,
    { "Path type",	"scribe.path.type",
    FT_UINT16, BASE_DEC, NULL, 0x0,
    "", HFILL }},
    { &hf_scribe_path,
    { "Path", "scribe.path.value",
    FT_STRING, BASE_NONE, NULL, 0x0,
    "", HFILL }},
    
  };

  /* Setup protocol subtree array */
  static gint *ett[] = {
    &ett_scribe
  };

  module_t *scribe_module;	

  if (proto_scribe == -1) {
    proto_scribe = proto_register_protocol (
      "Scribe (Common API Application)",	/* name */
      "Scribe",                           /* short name */
      "scribe"	                          /* abbrev */
      );
  }
  scribe_module	= prefs_register_protocol(proto_scribe, NULL);
  proto_register_field_array(proto_scribe, hf, array_length(hf));
  proto_register_subtree_array(ett, array_length(ett));
}


void
proto_reg_handoff_scribe(void)
{
  static int Initialized=FALSE;
  if (!Initialized) {
    scribe_handle = create_dissector_handle(dissect_scribe, proto_scribe);
    dissector_add("commonapi.app", SCRIBE_SUB_ADDRESS, scribe_handle);
  }
}
