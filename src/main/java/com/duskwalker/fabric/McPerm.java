package com.duskwalker.fabric;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.duskwalker.bedrock.BedrockException;
import com.duskwalker.bedrock.Perm;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * Bedrock BlockPermutation = Java BlockState. Durum adlari cevrilir:
 * open_bit->open, powered_bit->powered, upper_block_bit->half(DoubleBlockHalf), upside_down_bit->half(Half),
 * door_hinge_bit->hinge, direction / facing_direction->facing, ground_sign_direction->rotation; digerleri ayni ad.
 */
public final class McPerm implements Perm {
    final BlockState state;

    public McPerm(BlockState state) { this.state = state; }

    public BlockState state() { return state; }

    @Override public String typeId() { return IdMap.blockToBedrock(state); }

    // Bedrock kapi yonu: 0 dogu, 1 guney, 2 bati, 3 kuzey. Cit kapisi: 0 guney, 1 bati, 2 kuzey, 3 dogu.
    private static final Direction[] DOOR_DIRS = { Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.NORTH };
    private static final Direction[] GATE_DIRS = { Direction.SOUTH, Direction.WEST, Direction.NORTH, Direction.EAST };
    // facing_direction: 0 asagi, 1 yukari, 2 kuzey, 3 guney, 4 bati, 5 dogu
    private static final Direction[] FACING_DIRS = { Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST };

    private static Direction[] horizontalTable(BlockState s) {
        return s.getBlock() instanceof FenceGateBlock ? GATE_DIRS : DOOR_DIRS;
    }

    private static int indexOf(Direction[] table, Direction d) {
        for (int i = 0; i < table.length; i++) if (table[i] == d) return i;
        return 0;
    }

    /** Java ozelligi -> Bedrock durum adi. */
    private static String bedrockName(BlockState s, Property<?> p) {
        String n = p.getName();
        Class<?> vc = p.getValueClass();
        switch (n) {
            case "open": return "open_bit";
            case "powered": return "powered_bit";
            case "hinge": return "door_hinge_bit";
            case "rotation": return "ground_sign_direction";
            case "half":
                return vc == DoubleBlockHalf.class ? "upper_block_bit" : (vc == Half.class ? "upside_down_bit" : n);
            case "facing":
                if (s.getBlock() instanceof DoorBlock || s.getBlock() instanceof FenceGateBlock) return "direction";
                return "facing_direction";
            default: return n;
        }
    }

    /** Java ozellik degeri -> Bedrock degeri. */
    private static Object bedrockValue(BlockState s, Property<?> p) {
        Comparable<?> v = s.getValue(p);
        String n = p.getName();
        if (n.equals("half") && v instanceof DoubleBlockHalf h) return h == DoubleBlockHalf.UPPER;
        if (n.equals("half") && v instanceof Half h) return h == Half.TOP;
        if (n.equals("hinge")) return v.toString().equals("right");
        if (n.equals("facing") && v instanceof Direction d) {
            if (s.getBlock() instanceof DoorBlock || s.getBlock() instanceof FenceGateBlock) return indexOf(horizontalTable(s), d);
            return indexOf(FACING_DIRS, d);
        }
        if (v instanceof Boolean || v instanceof Integer) return v;
        return propName(p, v);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static String propName(Property p, Comparable v) { return p.getName(v); }

    @Override
    public Map<String, Object> getAllStates() {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Property<?> p : state.getProperties()) out.put(bedrockName(state, p), bedrockValue(state, p));
        return out;
    }

    @Override
    public Object getState(String name) {
        for (Property<?> p : state.getProperties()) {
            if (bedrockName(state, p).equals(name) || p.getName().equals(name)) return bedrockValue(state, p);
        }
        return null;
    }

    @Override
    public Perm withState(String name, Object value) {
        for (Property<?> p : state.getProperties()) {
            if (bedrockName(state, p).equals(name) || p.getName().equals(name)) {
                String javaValue = toJavaValue(state, p, value);
                return new McPerm(apply(state, p, javaValue, name));
            }
        }
        throw new BedrockException("block " + typeId() + " has no state '" + name + "'");
    }

    private static boolean truthy(Object v) {
        if (v instanceof Boolean b) return b;
        if (v instanceof Number n) return n.doubleValue() != 0;
        return v != null && Boolean.parseBoolean(String.valueOf(v));
    }

    private static String toJavaValue(BlockState s, Property<?> p, Object value) {
        String n = p.getName();
        Class<?> vc = p.getValueClass();
        if (n.equals("half") && vc == DoubleBlockHalf.class) return truthy(value) ? "upper" : "lower";
        if (n.equals("half") && vc == Half.class) return truthy(value) ? "top" : "bottom";
        if (n.equals("hinge")) return truthy(value) ? "right" : "left";
        if (n.equals("facing") && value instanceof Number num) {
            Direction[] t = (s.getBlock() instanceof DoorBlock || s.getBlock() instanceof FenceGateBlock) ? horizontalTable(s) : FACING_DIRS;
            int i = Math.floorMod(num.intValue(), t.length);
            return t[i].getSerializedName();
        }
        if (vc == Boolean.class) return String.valueOf(truthy(value));
        if (vc == Integer.class && value instanceof Number num) return String.valueOf(num.intValue());
        if (value instanceof Boolean b) return b.toString();
        if (value instanceof Number num) return (num.doubleValue() == Math.rint(num.doubleValue())) ? String.valueOf(num.longValue()) : num.toString();
        return String.valueOf(value);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static BlockState apply(BlockState s, Property<?> p, String javaValue, String requested) {
        Optional<?> parsed = ((Property) p).getValue(javaValue);
        if (parsed.isEmpty()) throw new BedrockException("invalid value for state '" + requested + "': " + javaValue);
        return (BlockState) s.setValue((Property) p, (Comparable) parsed.get());
    }

    @Override public boolean equals(Object o) { return o instanceof McPerm m && m.state.equals(state); }
    @Override public int hashCode() { return Objects.hash(state); }
    @Override public String toString() { return state.toString(); }
}
