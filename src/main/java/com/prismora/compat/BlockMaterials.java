package com.prismora.compat;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.io.IOException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Resolves shaderpack material IDs once for the currently loaded block registry and tags. */
public final class BlockMaterials {
    private final Map<BlockState, Integer> ids;

    private BlockMaterials(Map<BlockState, Integer> ids) {
        this.ids = java.util.Collections.unmodifiableMap(ids);
    }

    public static BlockMaterials load(LegacyPack pack, Map<String, String> options, Map<String, String> environment) throws IOException {
        if (!pack.files().containsKey("shaders/block.properties")) {
            return new BlockMaterials(Map.of());
        }
        var properties = new ShaderPreprocessor(pack, options, environment).properties("shaders/block.properties");
        var states = new IdentityHashMap<BlockState, Integer>();
        for (var rule : rules(properties)) {
            if (rule.tag()) {
                var tag = TagKey.create(Registries.BLOCK, rule.name());
                for (var holder : BuiltInRegistries.BLOCK.getTagOrEmpty(tag)) {
                    apply(states, rule, holder.value());
                }
            } else {
                BuiltInRegistries.BLOCK.get(rule.name()).map(Holder::value)
                        .ifPresent(block -> apply(states, rule, block));
            }
        }
        return new BlockMaterials(states);
    }

    public int id(BlockState state) {
        return ids.getOrDefault(state, -1);
    }

    public int size() {
        return ids.size();
    }

    record Rule(int id, Identifier name, boolean tag, Map<String, String> predicates) {}

    static List<Rule> rules(Map<String, String> properties) throws IOException {
        var blocks = new ArrayList<Rule>();
        var tags = new ArrayList<Rule>();
        for (var entry : properties.entrySet()) {
            if (!entry.getKey().matches("block\\.-?\\d+")) {
                continue;
            }
            try {
                int id = Integer.parseInt(entry.getKey().substring("block.".length()));
                for (var token : entry.getValue().trim().split("\\s+")) {
                    if (!token.isEmpty()) {
                        var rule = rule(id, token);
                        (rule.tag() ? tags : blocks).add(rule);
                    }
                }
            } catch (IllegalArgumentException error) {
                throw new IOException("Invalid material mapping for " + entry.getKey(), error);
            }
        }
        blocks.addAll(tags);
        return List.copyOf(blocks);
    }

    private static Rule rule(int id, String token) {
        boolean tag = token.startsWith("%");
        var parts = (tag ? token.substring(1) : token).split(":", -1);
        boolean namespaced = parts.length > 1 && !parts[1].contains("=");
        var name = namespaced ? Identifier.fromNamespaceAndPath(parts[0], parts[1]) : Identifier.withDefaultNamespace(parts[0]);
        if (name.getPath().isEmpty()) {
            throw new IllegalArgumentException("Missing block name: " + token);
        }
        var predicates = new LinkedHashMap<String, String>();
        for (int i = namespaced ? 2 : 1; i < parts.length; i++) {
            var pair = parts[i].split("=", -1);
            if (pair.length != 2 || pair[0].isEmpty() || pair[1].isEmpty()) {
                throw new IllegalArgumentException("Invalid block state predicate: " + token);
            }
            predicates.put(pair[0], pair[1]);
        }
        return new Rule(id, name, tag, Map.copyOf(predicates));
    }

    private static void apply(Map<BlockState, Integer> states, Rule rule, Block block) {
        var definition = block.getStateDefinition();
        var required = new LinkedHashMap<Property<?>, Comparable<?>>();
        for (var entry : rule.predicates().entrySet()) {
            var property = definition.getProperty(entry.getKey());
            if (property == null) {
                return;
            }
            var value = property.getValue(entry.getValue());
            if (value.isEmpty()) {
                return;
            }
            required.put(property, value.get());
        }
        for (var state : definition.getPossibleStates()) {
            if (required.entrySet().stream().allMatch(entry -> state.getValue(entry.getKey()).equals(entry.getValue()))) {
                states.putIfAbsent(state, rule.id());
            }
        }
    }
}
