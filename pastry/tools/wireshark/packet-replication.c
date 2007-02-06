/* packet-replication.c
* Routines for Replication 
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

#include "packet-replication.h"
#include "packet-freepastry.h"

static int proto_replication = -1;

static int hf_replication_type = -1;
static int hf_replication_version = -1;
static int hf_replication_num_bloomfilters = -1;
static int hf_replication_num_ranges = -1;
static int hf_replication_num_rows = -1;
static int hf_replication_num_cols = -1;
static int hf_bloom_filter_length = -1;
static int hf_bloom_filter_num_params = -1;
static int hf_bloom_filter_param = -1;
static int hf_bloom_filter_num_bits = -1;
static int hf_bloom_filter_bits = -1;
static int hf_id_range_cw = -1;
static int hf_id_range_ccw = -1;
static int hf_id_range_empty = -1;

static gint ett_replication = -1;
static gint ett_replication_bloomfilter = -1;
static gint ett_replication_idrange = -1;

static dissector_handle_t replication_handle; 

static const value_string replication_msg_type[] = {
  { REP_REQUEST_MSG,  "Request"},
  { REP_RESPONSE_MSG, "Response"},
  { 0, NULL }
};


gint
decode_bloom_filter(tvbuff_t *tvb, proto_tree *parent_tree, gint offset, gchar *attribute_name)
{
  proto_item *ti = NULL;
  proto_tree *bloomfilter_tree = NULL;

  guint32 length;
  guint32 num_params; 
  guint32 num_bits;
  guint32 num_bytes;
  guint32 i;

  ti = proto_tree_add_text(parent_tree, tvb, offset, 1, attribute_name);
  bloomfilter_tree = proto_item_add_subtree(ti, ett_replication_bloomfilter);


  length = tvb_get_ntohl(tvb, offset);
  proto_tree_add_uint(bloomfilter_tree, hf_bloom_filter_length, tvb, offset, 4, length);
  offset += 4;
/*  if ((guint32) tvb_reported_length_remaining(tvb, offset) < length){
    proto_tree_add_text(bloomfilter_tree, tvb, offset, -1, "Malformed bloom filter!");
    return -1;
  }*/

  num_params = tvb_get_ntohl(tvb, offset);
  proto_tree_add_uint(bloomfilter_tree, hf_bloom_filter_num_params, tvb, offset, 4, num_params);
  offset += 4;
  for (i = 0; i < num_params; ++i){ 
    proto_tree_add_item(bloomfilter_tree, hf_bloom_filter_param, tvb, offset, 4, FALSE);
    offset += 4;
  }

  num_bits = tvb_get_ntohl(tvb, offset);
  proto_tree_add_uint(bloomfilter_tree, hf_bloom_filter_num_bits, tvb, offset, 4, num_bits);
  offset += 4;
  if (num_bits != 0) {
    num_bytes = num_bits >> 3;
    if ((num_bits % 8) != 0){
      num_bytes++;
    }

    proto_tree_add_item(bloomfilter_tree, hf_bloom_filter_bits, tvb, offset, num_bytes, FALSE);
    offset += num_bytes;
  } else {
    proto_tree_add_item(bloomfilter_tree, hf_bloom_filter_bits, tvb, offset, 1, FALSE);
    offset += 1;
  }
  proto_item_set_end(ti, tvb, offset);
  return offset;
}

gint
decode_id_range(tvbuff_t *tvb, proto_tree *parent_tree, gint offset, gchar *attribute_name)
{
  proto_item *ti = NULL;
  proto_tree *idrange_tree = NULL;

  ti = proto_tree_add_text(parent_tree, tvb, offset, 1, attribute_name);
  idrange_tree = proto_item_add_subtree(ti, ett_replication_idrange);
    
  if (tvb_reported_length_remaining(tvb, offset) < 41){
    proto_tree_add_text(idrange_tree, tvb, offset, -1, "Malformed ID range attribute!");
    return -1;
  }

  proto_tree_add_string(idrange_tree, hf_id_range_cw, tvb, offset, 20, get_id_full(tvb, offset));
  offset += 20;
  proto_tree_add_string(idrange_tree, hf_id_range_ccw, tvb, offset, 20, get_id_full(tvb, offset));
  offset += 20;
  proto_tree_add_item(idrange_tree, hf_id_range_empty, tvb, offset, 1, FALSE);
  offset++;
  proto_item_set_end(ti, tvb, offset);
  return offset;
}

void
decode_replication_request(tvbuff_t *tvb, proto_tree *tree, gint offset)
{
  if (tree){
    guint32 num_filters;
    guint32 num_ranges;
    guint32 i, j;

    num_filters = tvb_get_ntohl(tvb, offset);
    proto_tree_add_uint(tree, hf_replication_num_bloomfilters, tvb, offset, 4, num_filters);
    offset += 4;
    for (i = 0; i < num_filters; ++i){
      offset = decode_bloom_filter(tvb, tree, offset, ep_strdup_printf("Bloom filter #%d", i+1));
      if (offset == -1){
        return;
      }
    }

    num_ranges = tvb_get_ntohl(tvb, offset);
    proto_tree_add_uint(tree, hf_replication_num_ranges, tvb, offset, 4, num_ranges);
    offset += 4;
    for (j = 0; j < num_ranges; ++j){
      offset = decode_id_range(tvb, tree, offset, ep_strdup_printf("ID range #%d", j+1));
      if (offset == -1){
        return;
      }
    }/*end for each range*/
  }
}

void
decode_replication_response(tvbuff_t *tvb, proto_tree *tree, gint offset)
{
  if (tree){
    guint32 num_rows;
    guint32 i, k;
    guint32 num_ranges;

    num_rows = tvb_get_ntohl(tvb, offset);
    proto_tree_add_uint(tree, hf_replication_num_rows, tvb, offset, 4, num_rows);
    offset += 4;
    for (i = 0; i < num_rows; ++i){
      guint32 num_cols;
      guint32 j;
      num_cols = tvb_get_ntohl(tvb, offset);
      proto_tree_add_uint(tree, hf_replication_num_cols, tvb, offset, 4, num_cols);
      offset += 4;
      for (j = 0; j < num_cols; ++j){
        offset = decode_type_and_id(tvb, tree, offset);
        if (offset == -1){
          return;
        }
      }
    }/*end for each rows*/

    num_ranges = tvb_get_ntohl(tvb, offset);
    proto_tree_add_uint(tree, hf_replication_num_ranges, tvb, offset, 4, num_ranges);
    offset += 4;
    for (k = 0; k < num_ranges; ++k){
      offset = decode_id_range(tvb, tree, offset, ep_strdup_printf("ID range #%d", k+1));
      if (offset == -1){
        return;
      }
    }/*end for each range*/
  }
}

static void
dissect_replication(tvbuff_t *tvb, packet_info *pinfo, proto_tree *tree)
{
  proto_item *ti = NULL;
	proto_tree *replication_tree = NULL;
  const gchar *type_string = NULL;
  guint16 type;
  gint offset = 0;

  if (check_col(pinfo->cinfo, COL_PROTOCOL)) 
			col_set_str(pinfo->cinfo, COL_PROTOCOL, "Replication");

  type = tvb_get_ntohs(tvb, offset);
  type_string = val_to_str(type, replication_msg_type, "<Unknown type %d>");

  if (check_col(pinfo->cinfo, COL_INFO)){
    col_clear (pinfo->cinfo, COL_INFO);
    col_add_fstr(pinfo->cinfo, COL_INFO, "%d > %d %s",
      pinfo->srcport, pinfo->destport, type_string);
  }
  
  if (tree){
    ti = proto_tree_add_item(tree, proto_replication, tvb, 0, -1, FALSE);
    replication_tree = proto_item_add_subtree(ti, ett_replication);
    proto_tree_add_item(replication_tree, hf_replication_type, tvb, offset, 2, FALSE);
    proto_tree_add_item(replication_tree, hf_replication_version, tvb, offset + 2, 1, FALSE);
    offset = decode_nodehandle(tvb, replication_tree, offset + 3 , "Source");
  } else {
    offset = get_node_handle_len(tvb, offset + 3);
  }
  if (offset == -1){
    return;
  }

  switch (type){
    case REP_REQUEST_MSG:
      decode_replication_request(tvb, replication_tree, offset);
      break;
    case REP_RESPONSE_MSG:
      decode_replication_response(tvb, replication_tree, offset);
      break;
    default:
      return;/*stop dissection*/
  }
}

void
proto_register_replication(void)
{

 static hf_register_info hf[] = {
    { &hf_replication_type,
    { "Type",	"replication.type",
    FT_UINT16, BASE_DEC, VALS(replication_msg_type), 0x0,
    "", HFILL }},
    { &hf_replication_version,
    { "Version",		"replication.version",
    FT_UINT8, BASE_DEC, NULL, 0x0,
    "", HFILL }},
    { &hf_replication_num_bloomfilters,
    { "Number of Bloom filters",		"replication.num_bloomfilters",
    FT_UINT32, BASE_DEC, NULL, 0x0,
    "", HFILL }},
    { &hf_replication_num_ranges,
    { "Number of ID ranges",		"replication.num_ranges",
    FT_UINT32, BASE_DEC, NULL, 0x0,
    "", HFILL }},
    { &hf_replication_num_rows,
    { "Number of rows",		"replication.num_rows",
    FT_UINT32, BASE_DEC, NULL, 0x0,
    "", HFILL }},
    { &hf_replication_num_cols,
    { "Number of columns",		"replication.num_cols",
    FT_UINT32, BASE_DEC, NULL, 0x0,
    "", HFILL }},
    { &hf_bloom_filter_length,
    { "Set lenght",		"replication.bloomfilter.length",
    FT_UINT32, BASE_DEC, NULL, 0x0,
    "", HFILL }},
    { &hf_bloom_filter_num_params,
    { "Number of parameter",		"replication.bloomfilter.num_params",
    FT_UINT32, BASE_DEC, NULL, 0x0,
    "", HFILL }},
    { &hf_bloom_filter_param,
    { "Parameter",		"replication.bloomfilter.param",
    FT_UINT32, BASE_DEC, NULL, 0x0,
    "", HFILL }},
    { &hf_bloom_filter_num_bits,
    { "Number of bits",		"replication.bloomfilter.num_bits",
    FT_UINT32, BASE_DEC, NULL, 0x0,
    "", HFILL }},
    { &hf_bloom_filter_bits,
    { "Bits field",		"replication.bloomfilter.bits",
    FT_BYTES, BASE_HEX, NULL, 0x0,
    "", HFILL }},
    { &hf_id_range_cw,
    { "Clockwise ID",		"replication.idrange.cw",
    FT_STRING, BASE_NONE, NULL, 0x0,
    "", HFILL }},
    { &hf_id_range_ccw,
    { "Counter clockwise ID",		"replication.idrange.ccw",
    FT_STRING, BASE_NONE, NULL, 0x0,
    "", HFILL }},
    { &hf_id_range_empty,
    { "Is empty",		"replication.idrange.empty",
    FT_BOOLEAN, 8, NULL, 0x0,
    "", HFILL }},
  };

  /* Setup protocol subtree array */
  static gint *ett[] = {
    &ett_replication,
    &ett_replication_bloomfilter,
    &ett_replication_idrange
  };

  module_t *replication_module;	

  if (proto_replication == -1) {
    proto_replication = proto_register_protocol (
      "Replication Manager (Common API Application)",	/* name */
      "Replication",                                  /* short name */
      "replication"	                                  /* abbrev */
      );
  }
  replication_module	= prefs_register_protocol(proto_replication, NULL);
  proto_register_field_array(proto_replication, hf, array_length(hf));
  proto_register_subtree_array(ett, array_length(ett));
}


void
proto_reg_handoff_replication(void)
{
  static int Initialized=FALSE;
  if (!Initialized) {
    replication_handle = create_dissector_handle(dissect_replication, proto_replication);
    dissector_add("commonapi.app", REPLICATION_SUB_ADDRESS, replication_handle);
  }
}
