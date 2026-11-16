package com.prismora.render.runtime;

import java.util.List;

/** Prismora geometry owned by the same compile result and mesh as vanilla terrain. */
public interface TerrainSectionGeometry {
    List<TerrainVertexData> prismora$faces();

    void prismora$setFaces(List<TerrainVertexData> faces);
}
