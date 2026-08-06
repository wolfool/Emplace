package com.wolfool.emplace.placement;

import com.wolfool.emplace.EmplaceConfig;
import net.momirealms.craftengine.bukkit.api.event.FurnitureAttemptPlaceEvent;
import net.momirealms.craftengine.bukkit.item.behavior.FurnitureItemBehavior;
import net.momirealms.craftengine.core.entity.furniture.FurnitureDefinition;
import net.momirealms.craftengine.core.entity.player.InteractionHand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.Map;

/**
 * 가구 설치를 가로채고, 미리보기 중의 조작을 받는다.
 *
 * <pre>
 *   가구 아이템 우클릭 : 미리보기 시작 (즉시 설치를 가로챈다)
 *   좌클릭            : 회전
 *   우클릭            : 설치
 *   웅크리기          : 취소
 * </pre>
 */
public final class PlacementListener implements Listener {

    private final Plugin plugin;
    private final PlacementManager placement;
    private final EmplaceConfig config;

    public PlacementListener(Plugin plugin, PlacementManager placement, EmplaceConfig config) {
        this.plugin = plugin;
        this.placement = placement;
        this.config = config;
    }

    /**
     * CraftEngine 이 가구를 놓으려는 순간.
     *
     * <p>여기를 막는 것이 이 플러그인의 전부다. 가구 쪽 yml 을 하나도 안 건드려도 되고,
     * 남의 리소스팩에서 온 가구도 그대로 걸린다.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAttemptPlace(FurnitureAttemptPlaceEvent event) {
        Player player = event.player();

        // 이미 미리보기 중이면 우리가 직접 놓는다. CraftEngine 이 또 놓으면 두 개가 된다.
        if (placement.isPlacing(player)) {
            event.setCancelled(true);
            return;
        }
        if (event.hand() != InteractionHand.MAIN_HAND) return;

        FurnitureDefinition definition = event.furniture();
        if (definition == null) return;

        String id = definition.id().toString();
        if (!config.handles(id)) return;   // 제외 목록. CraftEngine 이 하던 대로 둔다

        ItemStack inHand = player.getInventory().getItemInMainHand();
        if (inHand.getType().isAir()) return;

        event.setCancelled(true);
        placement.start(player, definition, inHand, rules(inHand));
    }

    /**
     * 그 아이템에 적힌 붙임 자리별 회전·정렬 규칙.
     *
     * <p>CraftEngine 이 가구마다 정해 둔 값이라 그대로 따른다. 바닥은 45도씩, 벽은
     * 벽 방향으로만 도는 식으로 가구마다 다르다. 못 읽으면 빈 지도를 주고
     * 기본 규칙으로 떨어진다 — 미리보기가 아예 안 되는 것보다 낫다.
     */
    private Map<String, FurnitureItemBehavior.Rule> rules(ItemStack stack) {
        try {
            String id = placement.furnitures().itemId(stack);
            if (id == null) return Map.of();
            var definition = net.momirealms.craftengine.bukkit.api.CraftEngineItems.byId(id);
            if (definition == null) return Map.of();
            if (definition.behavior() instanceof FurnitureItemBehavior furniture) {
                return furniture.rules();
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("가구 배치 규칙을 못 읽었다: " + t.getMessage());
        }
        return Map.of();
    }

    /** 좌클릭 = 회전, 우클릭 = 설치. 미리보기 중에는 원래 동작을 전부 막는다. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!placement.isPlacing(player)) return;

        // 오프핸드로도 한 번 더 들어온다. 메인핸드만 본다.
        if (event.getHand() != null && event.getHand() != EquipmentSlot.HAND) {
            event.setCancelled(true);
            return;
        }
        event.setCancelled(true);

        if (event.getAction().isLeftClick()) {
            PlacementSession session = placement.session(player);
            if (session != null) session.rotate(!player.isSneaking());
        } else if (event.getAction().isRightClick()) {
            placement.confirm(player);
        }
    }

    /** 웅크리면 취소. 손이 비면 놓을 게 없다. */
    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        if (!config.sneakToCancel()) return;
        if (event.isSneaking() && placement.isPlacing(event.getPlayer())) {
            placement.cancel(event.getPlayer(), true);
        }
    }

    @EventHandler
    public void onItemHeld(PlayerItemHeldEvent event) {
        placement.cancel(event.getPlayer(), true);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        placement.cancel(event.getPlayer(), true);
    }

    /** 미리보기 중에 블록이 놓이면 미리보기와 지형이 어긋난다. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (placement.isPlacing(event.getPlayer())) event.setCancelled(true);
    }

    /** 나갈 때 미리보기를 치운다. 안 그러면 엔티티가 남는다. */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        placement.cancel(event.getPlayer(), false);
    }
}
