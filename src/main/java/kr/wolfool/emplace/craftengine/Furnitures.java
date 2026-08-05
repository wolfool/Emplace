package kr.wolfool.emplace.craftengine;

import net.momirealms.craftengine.bukkit.api.CraftEngineFurniture;
import net.momirealms.craftengine.bukkit.api.CraftEngineItems;
import net.momirealms.craftengine.bukkit.entity.furniture.BukkitFurniture;
import net.momirealms.craftengine.core.entity.furniture.FurnitureDefinition;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

/**
 * CraftEngine 을 부르는 자리를 한 곳에 모은다.
 *
 * <p>나머지 코드가 CraftEngine 클래스를 직접 건드리지 않게 해서, API 가 바뀌어도
 * 여기만 고치면 되게 한다. 실패하면 조용히 null 을 주고 원래 동작으로 돌아간다.
 */
public final class Furnitures {

    private final Plugin plugin;

    public Furnitures(Plugin plugin) {
        this.plugin = plugin;
    }

    /** 이 아이템의 CraftEngine ID. 커스텀 아이템이 아니면 null. */
    public @Nullable String itemId(@Nullable ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return null;
        try {
            Key key = CraftEngineItems.getCustomItemId(stack);
            return key == null ? null : key.toString();
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 가구를 놓는다.
     *
     * @param variant 놓을 모습(ground / wall / ceiling 등)
     * @param yaw     바라볼 방향
     */
    public @Nullable BukkitFurniture place(FurnitureDefinition definition, String variant,
                                           Location where, float yaw) {
        Location at = where.clone();
        at.setYaw(yaw);
        at.setPitch(0);
        try {
            return CraftEngineFurniture.place(at, definition, variant, true);
        } catch (Throwable t) {
            plugin.getLogger().warning("가구를 놓지 못했다 (" + definition.id() + "): " + t.getMessage());
            return null;
        }
    }

    /** 서버에 올라온 가구 개수. 명령어에서 보여줄 때 쓴다. */
    public int loadedCount() {
        try {
            return CraftEngineFurniture.loadedFurniture().size();
        } catch (Throwable t) {
            return 0;
        }
    }
}
