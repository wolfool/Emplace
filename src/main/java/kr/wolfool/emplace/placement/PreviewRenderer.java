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

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
    /** 같은 경고를 매 틱 쏟아내지 않으려고 이미 알린 아이템을 기억해 둔다. */
    private final Set<String> warned = ConcurrentHashMap.newKeySet();

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
                // 글자나 블록으로 된 조각, BetterModel 같은 외부 모델은 여기서 못 그린다.
                // 하나도 못 그리면 부르는 쪽이 손에 든 아이템으로 대신 띄운다.
                continue;
            }
            ItemDisplay display = spawnOne(viewer, element, base, yaw);
            if (display != null) spawned.add(new Piece(element, display));
        }
        return spawned;
    }

    /**
     * 가구 정의로는 못 그릴 때 손에 든 아이템으로 대신 띄운다.
     *
     * <p>블록이나 외부 모델로 된 가구, 또는 조각의 아이템을 못 만든 경우다. 생김새가
     * 실물과 다를 수 있지만 <b>자리와 방향은 정확하다.</b> 아무것도 안 보이는 것보다 낫다.
     */
    public @Nullable Piece spawnFallback(Player viewer, ItemStack held, Location base, float yaw) {
        ItemStack ghost = held.clone();
        ghost.setAmount(1);
        try {
            ItemDisplay display = viewer.getWorld().spawn(centered(base, yaw), ItemDisplay.class, e -> {
                e.setItemStack(ghost);
                e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                e.setBillboard(Display.Billboard.FIXED);
                e.setPersistent(false);
                e.setInvulnerable(true);
                e.setGravity(false);
                e.setVisibleByDefault(false);

                // 아이템 모델을 그대로 쓰면 한 블록을 꽉 채운다. 반 블록으로 줄이고
                // 그만큼 띄워서 바닥에 놓인 것처럼 보이게 한다.
                Transformation t = e.getTransformation();
                t.getScale().set(0.5f, 0.5f, 0.5f);
                t.getTranslation().set(0f, 0.25f, 0f);
                e.setTransformation(t);
            });
            viewer.showEntity(plugin, display);
            return new Piece(null, display);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private Location centered(Location base, float yaw) {
        Location at = base.clone();
        at.setYaw(yaw);
        at.setPitch(0);
        return at;
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
        Location at = piece.element() == null
                ? centered(base, yaw)                       // 대신 띄운 것은 자리 보정이 없다
                : positionOf(piece.element(), base, yaw);
        piece.display().teleport(at);
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

    /**
     * 조각이 쓰는 아이템.
     *
     * <p>못 만들면 <b>왜 못 만들었는지 로그를 남긴다.</b> 조용히 넘기면 미리보기가 안
     * 뜨는데 이유를 알 방법이 없다. 같은 아이템으로 매 틱 같은 경고가 쏟아지지 않게
     * 한 번 본 것은 기억해 둔다.
     */
    private ItemStack itemOf(ItemDisplayFurnitureElementConfig element, Player viewer) {
        if (element.itemId == null) {
            warnOnce("(이름 없음)", "조각에 item 이 안 적혀 있다");
            return null;
        }
        try {
            var definition = CraftEngineItems.byId(element.itemId);
            if (definition == null) {
                warnOnce(element.itemId.toString(), "CraftEngine 에 그 아이템이 없다");
                return null;
            }
            ItemStack made = definition.buildBukkitItem(viewer);
            if (made == null) {
                warnOnce(element.itemId.toString(), "아이템을 만들지 못했다");
                return null;
            }
            made.setAmount(1);
            return made;
        } catch (Throwable t) {
            warnOnce(element.itemId.toString(), t.toString());
            return null;
        }
    }

    private void warnOnce(String itemId, String reason) {
        if (!warned.add(itemId)) return;
        plugin.getLogger().warning("미리보기를 못 그렸다 (" + itemId + "): " + reason
                + " — 손에 든 아이템으로 대신 띄운다.");
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
