#ifndef __PACKET_PAST_H__
#define __PACKET_PAST_H__

#define PAST_SUB_ADDRESS 0x9e39

#define CACHE_MSG             1
#define FETCH_HANDLE_MSG      2
#define FETCH_MSG             3
#define INSERT_MSG            4
#define LOOKUP_HANDLES_MSG    5
#define LOOKUP_MSG            6

extern gint decode_past_content(tvbuff_t *tvb, proto_tree *tree, gint offset);
extern gint decode_past_content_handle(tvbuff_t *tvb, proto_tree *tree, gint offset);
extern gint decode_past_error(tvbuff_t *tvb, proto_tree *tree, gint offset);

#endif
