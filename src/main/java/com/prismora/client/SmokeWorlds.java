package com.prismora.client;
/** Only used by explicitly enabled integration probes, never by normal gameplay. */
final class SmokeWorlds {
 private static final String ID=Boolean.getBoolean("prismora.smokeFreshWorld")?"PrismoraSmoke-"+java.util.UUID.randomUUID():"PrismoraSmoke";
 static String id(){return ID;}
 private SmokeWorlds(){}
}
