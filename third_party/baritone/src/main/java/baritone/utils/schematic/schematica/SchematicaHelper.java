/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.utils.schematic.schematica;

import baritone.api.schematic.IStaticSchematic;
import net.minecraft.util.Tuple;
import net.minecraft.util.BlockPos;

import java.util.Optional;

public enum SchematicaHelper {
    ;

    public static boolean isSchematicaPresent() {
        try {
            Class.forName("com.github.lunatrius.schematica.Schematica");
            return true;
        } catch (ClassNotFoundException | NoClassDefFoundError ex) {
            return false;
        }
    }

    public static Optional<Tuple<IStaticSchematic, BlockPos>> getOpenSchematic() {
        try {
            Class<?> proxy = Class.forName("com.github.lunatrius.schematica.proxy.ClientProxy");
            Object world = proxy.getField("schematic").get(null);
            if (world == null) return Optional.empty();
            BlockPos position = (BlockPos) world.getClass().getField("position").get(world);
            return Optional.of(new Tuple<>(new SchematicAdapter(world), position));
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot read the open Schematica schematic", exception);
        }
    }

}
