package com.prismora.compat;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class BlockMaterialsTest {
    @Test
    void preservesOrderWithinExplicitBlocksAndTagsAndReadsPredicates() throws Exception {
        var properties = PropertiesReader.parse("""
                block.9 = %mod:cluster:waterlogged=false
                block.200 = mod:leaves:waterlogged=true:distance=2
                block.5 = oak_leaves
                block.2 = %leaves
                """);
        var rules = BlockMaterials.rules(properties);
        assertEquals(List.of(200, 5, 9, 2), rules.stream().map(BlockMaterials.Rule::id).toList());
        assertEquals("mod:leaves", rules.getFirst().name().toString());
        assertEquals(Map.of("waterlogged", "true", "distance", "2"), rules.getFirst().predicates());
        assertEquals("minecraft:oak_leaves", rules.get(1).name().toString());
        assertTrue(rules.getLast().tag());
    }

    @Test
    void reportsMalformedStateSelectors() {
        assertThrows(IOException.class, () -> BlockMaterials.rules(Map.of("block.10", "oak_leaves:waterlogged=")));
    }
}
