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
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.BlockPos;

import java.util.List;

public final class SchematicAdapter implements IStaticSchematic {

    private final Object schematic;

    public SchematicAdapter(Object schematicWorld) {
        this.schematic = invoke(schematicWorld, "getSchematic", new Class<?>[0]);
    }

    private static Object invoke(Object target, String name, Class<?>[] types, Object... arguments) {
        try { return target.getClass().getMethod(name, types).invoke(target, arguments); }
        catch (ReflectiveOperationException exception) { throw new IllegalStateException("Incompatible Schematica API: " + name, exception); }
    }

    @Override
    public IBlockState desiredState(int x, int y, int z, IBlockState current, List<IBlockState> approxPlaceable) {
        return this.getDirect(x, y, z);
    }

    @Override
    public IBlockState getDirect(int x, int y, int z) {
        return (IBlockState) invoke(schematic, "getBlockState", new Class<?>[]{BlockPos.class}, new BlockPos(x, y, z));
    }

    @Override
    public int widthX() {
        return (Integer) invoke(schematic, "getWidth", new Class<?>[0]);
    }

    @Override
    public int heightY() {
        return (Integer) invoke(schematic, "getHeight", new Class<?>[0]);
    }

    @Override
    public int lengthZ() {
        return (Integer) invoke(schematic, "getLength", new Class<?>[0]);
    }
}
