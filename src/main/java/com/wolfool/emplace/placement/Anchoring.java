package com.wolfool.emplace.placement;

import net.momirealms.craftengine.core.entity.furniture.AlignmentRule;
import net.momirealms.craftengine.core.entity.furniture.AnchorType;
import net.momirealms.craftengine.core.entity.furniture.FurnitureDefinition;
import net.momirealms.craftengine.core.entity.furniture.RotationRule;
import net.momirealms.craftengine.core.util.Pair;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.jetbrains.annotations.Nullable;

/**
 * 어디를 보고 있는지에서 '놓을 자리' 를 뽑는다.
 *
 * <p>부딪힌 면으로 바닥·벽·천장을 가르고, 가구가 그 모습을 갖고 있으면 거기에 붙인다.
 * 없으면 바닥 모습으로 떨어진다.
 */
public final class Anchoring {

    /** 정해진 자리 하나. */
    public record Spot(Location location, String variant, BlockFace face) {
    }

    private Anchoring() {
    }

    /**
     * 지금 보고 있는 곳에 놓는다면 어디인지.
     *
     * @return 놓을 수 없는 곳(허공 등)을 보고 있으면 null
     */
    public static @Nullable Spot aim(Player player, FurnitureDefinition definition,
                                     AlignmentRule alignment, double reach) {
        RayTraceResult hit = player.rayTraceBlocks(reach);
        if (hit == null || hit.getHitBlock() == null) return null;

        BlockFace face = hit.getHitBlockFace();
        if (face == null) face = BlockFace.UP;

        String variant = variantFor(definition, face);
        if (variant == null) return null;   // 이 가구는 그 면에 붙일 모습이 없다

        // 부딪힌 면 바깥쪽 블록. 거기가 가구가 들어갈 칸이다.
        Location block = hit.getHitBlock().getLocation()
                .add(face.getModX(), face.getModY(), face.getModZ());

        // 블록 안 어디쯤을 찍었는지(0~1)를 가구의 정렬 규칙으로 스냅한다.
        // CENTER 면 한가운데로, QUARTER 면 0.25/0.75 로 붙는다.
        var precise = hit.getHitPosition();
        double fx = precise.getX() - Math.floor(precise.getX());
        double fz = precise.getZ() - Math.floor(precise.getZ());
        Pair<Double, Double> snapped = alignment.apply(Pair.of(fx, fz));

        Location at = block.clone().add(snapped.left(), 0, snapped.right());
        return new Spot(at, variant, face);
    }

    /**
     * 그 면에 붙일 때 쓸 모습의 이름.
     *
     * <p>가구마다 갖고 있는 모습이 다르다. 벽에 붙는 모습이 없는 가구를 벽에 대고 있으면
     * 바닥 모습으로 떨어뜨린다 — 위를 보고 다시 놓게 하는 것보다 낫다.
     */
    private static @Nullable String variantFor(FurnitureDefinition definition, BlockFace face) {
        AnchorType wanted = switch (face) {
            case UP -> AnchorType.GROUND;
            case DOWN -> AnchorType.CEILING;
            default -> AnchorType.WALL;
        };

        String name = wanted.variantName();
        if (definition.getVariant(name) != null) return name;

        String ground = AnchorType.GROUND.variantName();
        if (definition.getVariant(ground) != null) return ground;

        return definition.anyVariantName();
    }

    /**
     * 처음 잡을 방향.
     *
     * <p>플레이어를 마주 보게 둔다. 대부분 그대로 놓기 때문에 매번 돌리게 하는 것보다 낫다.
     */
    public static float initialYaw(Player player, RotationRule rule) {
        return snap(player.getLocation().getYaw() + 180f, rule);
    }

    /** 가구가 허용하는 각도로 맞춘다. 규칙은 CraftEngine 이 갖고 있다. */
    public static float snap(float yaw, RotationRule rule) {
        float wrapped = wrap(yaw);
        try {
            return wrap(rule.apply(wrapped));
        } catch (Throwable t) {
            return wrapped;
        }
    }

    /**
     * 한 칸 돌린 각도.
     *
     * <p>규칙이 허용하는 각도가 몇 개인지 몰라도 되게, 조금씩 돌려 보면서 값이 실제로
     * 바뀌는 지점을 찾는다. FOUR 면 90도씩, SIXTEEN 이면 22.5도씩 걸린다.
     * 한 방향으로만 놓이는 가구(NORTH 등)는 아무리 돌려도 안 바뀌므로 그대로 둔다.
     */
    public static float rotate(float yaw, RotationRule rule, boolean clockwise) {
        float from = snap(yaw, rule);
        float step = clockwise ? 1f : -1f;
        for (int i = 1; i <= 360; i++) {
            float candidate = snap(from + step * i, rule);
            if (Math.abs(angleBetween(candidate, from)) > 0.01f) return candidate;
        }
        return from;
    }

    private static float wrap(float yaw) {
        float v = yaw % 360f;
        return v < 0 ? v + 360f : v;
    }

    private static float angleBetween(float a, float b) {
        float diff = (a - b) % 360f;
        if (diff > 180f) diff -= 360f;
        if (diff < -180f) diff += 360f;
        return diff;
    }
}
