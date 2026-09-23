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

import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;

/**
 * Serializes native block states the same way CraftBukkit {@code BlockData#getAsString()} does.
 */
final class CoreMcBlockStateStrings {

    private CoreMcBlockStateStrings() {
    }

    static String toDefaultStateString(Block block, Identifier id) {
        net.minecraft.world.level.block.state.BlockState state = block.defaultBlockState();
        StringBuilder sb = new StringBuilder(id.toString());
        if (!state.getProperties().isEmpty()) {
            sb.append('[');
            boolean first = true;
            for (net.minecraft.world.level.block.state.properties.Property<?> property : state.getProperties()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                sb.append(property.getName()).append('=').append(formatPropertyValue(property, state));
            }
            sb.append(']');
        }
        return sb.toString();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String formatPropertyValue(
        net.minecraft.world.level.block.state.properties.Property<?> property,
        net.minecraft.world.level.block.state.BlockState state
    ) {
        net.minecraft.world.level.block.state.properties.Property rawProperty =
            (net.minecraft.world.level.block.state.properties.Property) property;
        return rawProperty.getName(state.getValue(rawProperty));
    }
}
