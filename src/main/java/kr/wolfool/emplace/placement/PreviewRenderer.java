package kr.wolfool.emplace.placement;

import net.momirealms.craftengine.bukkit.api.CraftEngineItems;
import net.momirealms.craftengine.bukkit.entity.furniture.element.ItemDisplayFurnitureElementConfig;
import net.momirealms.craftengine.core.entity.furniture.FurnitureVariant;
import net.momirealms.craftengine.core.entity.furniture.element.FurnitureElementConfig;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 가구 정의를 그대로 읽어 미리보기를 만든다.
 *
 * <p>여기가 이 플러그인의 핵심이다. 가구가 화면에 어떻게 그려지는지는
 * {@link ItemDisplayFurnitureElementConfig} 가 이미 전부 들고 있다 — 아이템, 크기,
 * 위치, 회전, 조명까지. 그 값을 그대로 베껴 쓰면 <b>설정을 따로 적을 필요 없이</b>
 * 어떤 가구든 실물과 똑같은 미리보기가 나온다.
 *
 * <p>손으로 베껴 적는 방식은 값이 어긋나면 미리보기와 실물의 크기·높이가 달라지는데,
 * 그걸 눈으로 보기 전까지는 알 수가 없다.
 */
public final class PreviewRenderer {

    private final Plugin plugin;

    public PreviewRenderer(Plugin plugin) {
        this.plugin = plugin;
    }

    /** 띄운 조각 하나. 나중에 옮기려면 어떤 설정으로 만들었는지 같이 알아야 한다. */
    public record Piece(ItemDisplayFurnitureElementConfig element, ItemDisplay display) {
    }

    /**
     * 미리보기 엔티티들을 띄운다.
     *
     * <p>가구 하나가 여러 조각으로 되어 있을 수 있어서 목록으로 돌려준다.
     * 못 띄운 조각은 아예 빼서, 남은 것끼리 설정과 짝이 어긋나지 않게 한다.
     *
     * @param viewer 이 사람에게만 보인다. 남의 화면에 유령 가구가 떠다니지 않게
     */
    public List<Piece> spawn(Player viewer, FurnitureVariant variant, Location base, float yaw) {
        List<Piece> spawned = new ArrayList<>();

        for (FurnitureElementConfig<?> config : variant.elementConfigs()) {
            if (!(config instanceof ItemDisplayFurnitureElementConfig element)) {
                // 글자나 블록으로 된 조각은 미리보기에서 뺀다. 자리 잡는 데는
                // 생김새의 대부분을 차지하는 아이템 조각만 있어도 충분하다.
                continue;
            }
            ItemDisplay display = spawnOne(viewer, element, base, yaw);
            if (display != null) spawned.add(new Piece(element, display));
        }
        return spawned;
    }

    private ItemDisplay spawnOne(Player viewer, ItemDisplayFurnitureElementConfig element,
                                 Location base, float yaw) {
        ItemStack item = itemOf(element, viewer);
        if (item == null) return null;

        Location at = positionOf(element, base, yaw);
        try {
            ItemDisplay display = viewer.getWorld().spawn(at, ItemDisplay.class, e -> {
                e.setItemStack(item);
                apply(e, element);
                e.setPersistent(false);   // 서버가 꺼져도 유령으로 남지 않게
                e.setInvulnerable(true);
                e.setGravity(false);
                e.setVisibleByDefault(false);
            });
            viewer.showEntity(plugin, display);
            return display;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** 조각 하나를 옮긴다. 새로 띄우는 것보다 싸고 깜빡이지 않는다. */
    public void move(Piece piece, Location base, float yaw) {
        if (piece.display().isDead()) return;
        piece.display().teleport(positionOf(piece.element(), base, yaw));
    }

    /**
     * 조각이 놓일 자리.
     *
     * <p>{@code position} 은 가구 중심에서 떨어진 거리라 <b>가구가 도는 만큼 같이 돌아야</b>
     * 한다. 안 돌리면 여러 조각짜리 가구를 돌렸을 때 조각들이 제자리에 흩어진다.
     */
    private Location positionOf(ItemDisplayFurnitureElementConfig element, Location base, float yaw) {
        Vector3f offset = element.position;
        double radians = Math.toRadians(yaw);
        double sin = Math.sin(radians);
        double cos = Math.cos(radians);

        // 마인크래프트의 yaw 는 남쪽이 0도이고 시계 방향으로 는다.
        double x = offset.x * cos - offset.z * sin;
        double z = offset.x * sin + offset.z * cos;

        Location at = base.clone().add(x, offset.y, z);
        at.setYaw(yaw);
        at.setPitch(0);
        return at;
    }

    /** 크기·회전·조명을 가구 정의에서 그대로 가져온다. */
    private void apply(ItemDisplay display, ItemDisplayFurnitureElementConfig element) {
        Transformation transformation = display.getTransformation();
        transformation.getScale().set(element.scale);
        transformation.getTranslation().set(element.translation);
        Quaternionf rotation = element.rotation;
        if (rotation != null) transformation.getLeftRotation().set(rotation);
        display.setTransformation(transformation);

        display.setItemDisplayTransform(transformOf(element));
        display.setBillboard(billboardOf(element));
        display.setShadowRadius(element.shadowRadius);
        display.setShadowStrength(element.shadowStrength);

        // 가구가 밝기를 못박아 뒀으면 미리보기도 같게 둔다.
        // 안 그러면 어두운 곳에서 미리보기만 밝거나 어둡게 보인다.
        if (element.blockLight >= 0 && element.skyLight >= 0) {
            display.setBrightness(new Display.Brightness(element.blockLight, element.skyLight));
        }
    }

    private ItemStack itemOf(ItemDisplayFurnitureElementConfig element, Player viewer) {
        if (element.itemId == null) return null;
        try {
            var definition = CraftEngineItems.byId(element.itemId.toString());
            if (definition == null) return null;
            ItemStack made = definition.buildBukkitItem(viewer);
            if (made != null) made.setAmount(1);
            return made;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * CraftEngine 의 이름을 Bukkit 쪽 이름으로 옮긴다.
     *
     * <p>두 enum 의 이름이 같아서 이름으로 찾는다. 모르는 이름이면 가구에서 가장 흔한
     * {@code FIXED} 로 둔다.
     */
    private ItemDisplay.ItemDisplayTransform transformOf(ItemDisplayFurnitureElementConfig element) {
        if (element.displayContext == null) return ItemDisplay.ItemDisplayTransform.FIXED;
        try {
            return ItemDisplay.ItemDisplayTransform.valueOf(
                    element.displayContext.name().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return ItemDisplay.ItemDisplayTransform.FIXED;
        }
    }

    private Display.Billboard billboardOf(ItemDisplayFurnitureElementConfig element) {
        if (element.billboard == null) return Display.Billboard.FIXED;
        try {
            return Display.Billboard.valueOf(element.billboard.name().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Display.Billboard.FIXED;
        }
    }
}
