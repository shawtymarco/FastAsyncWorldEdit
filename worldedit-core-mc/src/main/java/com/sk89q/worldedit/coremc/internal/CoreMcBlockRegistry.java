/*
 * WorldEdit, a Minecraft world manipulation toolkit
 * Copyright (C) sk89q <http://www.sk89q.com>
 * Copyright (C) WorldEdit team and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.sk89q.worldedit.coremc.internal;

import com.sk89q.worldedit.registry.state.Property;
import com.sk89q.worldedit.util.formatting.text.Component;
import com.sk89q.worldedit.util.formatting.text.TranslatableComponent;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockType;
import com.sk89q.worldedit.world.registry.BlockMaterial;
import com.sk89q.worldedit.world.registry.BlockRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.TreeMap;

/**
 * Block registry implementation for platforms sharing native code.
 */
public final class CoreMcBlockRegistry implements BlockRegistry {

    private final CoreMcPlatform platform;
    private final Map<net.minecraft.world.level.block.state.BlockState, CoreMcBlockMaterial> materialMap = new HashMap<>();
    private List<String> cachedDefaultStates;

    public CoreMcBlockRegistry(CoreMcPlatform platform) {
        this.platform = platform;
    }

    @Override
    public Component getRichName(BlockType blockType) {
        return TranslatableComponent.of(platform.getAdapter().toNativeBlock(blockType).getDescriptionId());
    }

    @Override
    public BlockMaterial getMaterial(BlockType blockType) {
        Block block = platform.getAdapter().toNativeBlock(blockType);
        return materialMap.computeIfAbsent(
            block.defaultBlockState(),
            CoreMcBlockMaterial::new
        );
    }

    @Override
    public Map<String, ? extends Property<?>> getProperties(BlockType blockType) {
        Block block = platform.getAdapter().toNativeBlock(blockType);
        Map<String, Property<?>> map = new TreeMap<>();
        Collection<net.minecraft.world.level.block.state.properties.Property<?>> propertyKeys = block
            .defaultBlockState()
            .getProperties();
        for (net.minecraft.world.level.block.state.properties.Property<?> key : propertyKeys) {
            map.put(key.getName(), platform.getTransmogrifier().transmogToWorldEditProperty(key));
        }
        return map;
    }

    @Override
    public OptionalInt getInternalBlockStateId(BlockState state) {
        net.minecraft.world.level.block.state.BlockState equivalent = platform.getAdapter().toNativeBlockState(state);
        return OptionalInt.of(Block.getId(equivalent));
    }

    @Override
    public Collection<String> values() {
        if (cachedDefaultStates != null) {
            return cachedDefaultStates;
        }
        ArrayList<String> states = new ArrayList<>();
        Registry<Block> blockRegistry = platform.serverRegistryAccess().lookupOrThrow(Registries.BLOCK);
        for (Block block : blockRegistry) {
            Identifier id = blockRegistry.getKey(block);
            if (id != null) {
                states.add(CoreMcBlockStateStrings.toDefaultStateString(block, id));
            }
        }
        cachedDefaultStates = List.copyOf(states);
        return cachedDefaultStates;
    }

    @Override
    public Map<String, ? extends List<Property<?>>> getAllProperties() {
        Map<String, List<Property<?>>> properties = new HashMap<>();
        try {
            for (Field field : BlockStateProperties.class.getDeclaredFields()) {
                Object obj = field.get(null);
                if (!(obj instanceof net.minecraft.world.level.block.state.properties.Property<?> state)) {
                    continue;
                }
                Property<?> property = platform.getTransmogrifier().transmogToWorldEditProperty(state);
                properties.compute(property.getName().toLowerCase(Locale.ROOT), (k, v) -> {
                    if (v == null) {
                        return new ArrayList<>(Collections.singletonList(property));
                    }
                    v.add(property);
                    return v;
                });
            }
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to initialize block properties", e);
        }
        return properties;
    }
}
