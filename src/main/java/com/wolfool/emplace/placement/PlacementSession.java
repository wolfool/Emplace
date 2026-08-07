package com.wolfool.emplace.placement;

import net.momirealms.craftengine.bukkit.item.behavior.FurnitureItemBehavior;
import net.momirealms.craftengine.core.entity.furniture.AlignmentRule;
import net.momirealms.craftengine.core.entity.furniture.FurnitureDefinition;
import net.momirealms.craftengine.core.entity.furniture.FurnitureVariant;
import net.momirealms.craftengine.core.entity.furniture.RotationRule;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 한 사람이 가구를 놓는 중인 상태.
 *
 * <p>미리보기 엔티티들과 지금 잡은 방향·모습을 들고 있다.
 */
public final class PlacementSession {

    private static final Color OK = Color.fromRGB(0x55FF55);
    private static final Color BLOCKED = Color.fromRGB(0xFF5555);

    private final Player player;
    private final FurnitureDefinition definition;
    private final ItemStack item;
    /** 붙임 자리(ground/wall/ceiling) 별 회전·정렬 규칙. */
    private final Map<String, FurnitureItemBehavior.Rule> rules;
    private final PreviewRenderer renderer;
    private final boolean glow;

    private final List<PreviewRenderer.Piece> pieces = new ArrayList<>();

    private String variantName;
    private float yaw;
    private @Nullable Location spot;
    private boolean blocked;
    /** 지금 쓰는 격자. 놓는 중에 바꿀 수 있습니다. */
    private Grid grid;

    PlacementSession(Player player, FurnitureDefinition definition, ItemStack item,
                     Map<String, FurnitureItemBehavior.Rule> rules,
                     PreviewRenderer renderer, boolean glow, String variantName, float yaw,
                     Grid grid) {
        this.player = player;
        this.definition = definition;
        this.item = item;
        this.rules = rules;
        this.renderer = renderer;
        this.glow = glow;
        this.variantName = variantName;
        this.grid = grid;
        this.yaw = Anchoring.snap(yaw, rotationRule());
    }

    /** 지금 붙이려는 자리의 규칙. 없으면 CraftEngine 의 기본값. */
    private FurnitureItemBehavior.Rule rule() {
        FurnitureItemBehavior.Rule found = rules.get(variantName);
        return found != null ? found : FurnitureItemBehavior.Rule.DEFAULT;
    }

    private RotationRule rotationRule() {
        return rule().rotationRule();
    }

    public Player player() {
        return player;
    }

    public FurnitureDefinition definition() {
        return definition;
    }

    public ItemStack item() {
        return item;
    }

    public AlignmentRule alignmentRule() {
        return rule().alignmentRule();
    }

    public Grid grid() {
        return grid;
    }

    /** 다음 격자로 넘어갑니다. 미리보기는 다음 틱에 저절로 따라옵니다. */
    public Grid cycleGrid() {
        grid = grid.next();
        return grid;
    }

    public void grid(Grid value) {
        if (value != null) this.grid = value;
    }

    public String variantName() {
        return variantName;
    }

    public float yaw() {
        return yaw;
    }

    /** 지금 놓을 수 있는 자리. 허공을 보고 있으면 null. */
    public @Nullable Location spot() {
        return spot;
    }

    public boolean isBlocked() {
        return blocked;
    }

    /** 지금 떠 있는 미리보기 조각 수. 0 이면 아무것도 안 보인다는 뜻이다. */
    public int pieceCount() {
        return pieces.size();
    }

    /** 이 자리·이 모습으로 미리보기를 옮긴다. */
    void showAt(Location where, String variant) {
        boolean variantChanged = !variant.equals(variantName);
        this.variantName = variant;
        this.spot = where;
        // 붙임 자리가 바뀌면 회전 규칙도 바뀐다. 바닥에서 잡아 둔 각도가 벽에서는
        // 허용되지 않을 수 있어서 새 규칙으로 다시 맞춘다.
        if (variantChanged) this.yaw = Anchoring.snap(yaw, rotationRule());

        if (variantChanged || pieces.isEmpty()) {
            // 모습이 바뀌면 조각 구성 자체가 달라진다. 다시 만든다.
            rebuild();
        } else {
            for (PreviewRenderer.Piece piece : pieces) renderer.move(piece, where, yaw);
        }
        setBlocked(false);
    }

    /** 놓을 수 없는 곳을 보고 있다. 자리는 그대로 두고 색만 바꾼다. */
    void markBlocked() {
        setBlocked(true);
    }

    void rotate(boolean clockwise) {
        yaw = Anchoring.rotate(yaw, rotationRule(), clockwise);
        if (spot != null) {
            for (PreviewRenderer.Piece piece : pieces) renderer.move(piece, spot, yaw);
        }
    }

    private void rebuild() {
        clearDisplays();
        FurnitureVariant variant = definition.getVariant(variantName);
        if (variant == null || spot == null) return;

        pieces.addAll(renderer.spawn(player, variant, spot, yaw));

        // 가구 정의로 아무것도 못 그렸다. 블록이나 외부 모델(BetterModel)로 된 가구거나
        // 조각의 아이템을 못 만든 경우다. 손에 든 아이템으로라도 띄운다 —
        // 생김새는 달라도 자리와 방향은 정확하고, 아무것도 안 보이는 것보다 낫다.
        if (pieces.isEmpty()) {
            PreviewRenderer.Piece fallback = renderer.spawnFallback(player, item, spot, yaw);
            if (fallback != null) pieces.add(fallback);
        }
        applyGlow();
    }

    private void setBlocked(boolean value) {
        if (blocked == value) return;
        blocked = value;
        applyGlow();
    }

    private void applyGlow() {
        if (!glow) return;
        for (PreviewRenderer.Piece piece : pieces) {
            ItemDisplay display = piece.display();
            if (display.isDead()) continue;
            display.setGlowing(true);
            display.setGlowColorOverride(blocked ? BLOCKED : OK);
        }
    }

    private void clearDisplays() {
        for (PreviewRenderer.Piece piece : pieces) {
            if (!piece.display().isDead()) piece.display().remove();
        }
        pieces.clear();
    }

    void cleanup() {
        clearDisplays();
    }
}
