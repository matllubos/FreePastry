/* Do not modify this file.  */
/* It is created automatically by the Makefile.  */

#ifdef HAVE_CONFIG_H
# include "config.h"
#endif

#include <gmodule.h>

#include "moduleinfo.h"

#ifndef ENABLE_STATIC
G_MODULE_EXPORT const gchar version[] = VERSION;

/* Start the functions we need for the plugin stuff */

G_MODULE_EXPORT void
plugin_register (void)
{
  {extern void proto_register_freepastry (void); proto_register_freepastry ();}
  {extern void proto_register_freepastry_core_v0 (void); proto_register_freepastry_core_v0 ();}
  {extern void proto_register_gcpast (void); proto_register_gcpast ();}
  {extern void proto_register_past (void); proto_register_past ();}
  {extern void proto_register_replication (void); proto_register_replication ();}
  {extern void proto_register_scribe (void); proto_register_scribe ();}
}

G_MODULE_EXPORT void
plugin_reg_handoff(void)
{
  {extern void proto_reg_handoff_freepastry (void); proto_reg_handoff_freepastry ();}
  {extern void proto_reg_handoff_freepastry_core_v0 (void); proto_reg_handoff_freepastry_core_v0 ();}
  {extern void proto_reg_handoff_gcpast (void); proto_reg_handoff_gcpast ();}
  {extern void proto_reg_handoff_past (void); proto_reg_handoff_past ();}
  {extern void proto_reg_handoff_replication (void); proto_reg_handoff_replication ();}
  {extern void proto_reg_handoff_scribe (void); proto_reg_handoff_scribe ();}
}
#endif
