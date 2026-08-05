package kr.wolfool.emplace.placement;

import kr.wolfool.emplace.EmplaceConfig;
import kr.wolfool.emplace.craftengine.Furnitures;
import net.kyori.adventure.text.Component;
import net.momirealms.craftengine.bukkit.item.behavior.FurnitureItemBehavior;
import net.momirealms.craftengine.core.entity.furniture.AlignmentRule;
import net.momirealms.craftengine.core.entity.furniture.FurnitureDefinition;
import net.momirealms.craftengine.core.entity.furniture.RotationRule;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 누가 지금 가구를 놓는 중인지 관리한다. */
public final class PlacementManager {

    private final Plugin plugin;
    private final EmplaceConfig config;
    private final Furnitures furnitures;
    private final PreviewRenderer renderer;

    private final Map<UUID, PlacementSession> sessions = new HashMap<>();

    public PlacementManager(Plugin plugin, EmplaceConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.furnitures = new Furnitures(plugin);
        this.renderer = new PreviewRenderer(plugin);
    }

    public boolean isPlacing(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    public @Nullable PlacementSession session(Player player) {
        return sessions.get(player.getUniqueId());
    }

    public Furnitures furnitures() {
        return furnitures;
    }

    // ---------------- 시작 ----------------

    /**
     * 미리보기를 띄우고 자리 잡기를 시작한다.
     *
     * @param rules CraftEngine 이 이 가구에 정해 둔 붙임 자리별 회전·정렬 규칙.
     *              바닥과 벽의 회전 방식이 다른 가구가 있어서 자리마다 따로 들고 있다
     */
    public void start(Player player, FurnitureDefinition definition, ItemStack item,
                      Map<String, FurnitureItemBehavior.Rule> rules) {
        if (isPlacing(player)) return;

        PlacementSession session = new PlacementSession(
                player, definition, item.clone(), rules, renderer, config.glow(),
                definition.anyVariantName(), player.getLocation().getYaw() + 180f);

        sessions.put(player.getUniqueId(), session);
        // 첫 프레임을 바로 그려서, 시작하자마자 미리보기가 보이게 한다.
        follow(session);

        if (config.debug()) {
            plugin.getLogger().info("가로챔: " + definition.id()
                    + " | 모습 " + session.variantName()
                    + " | 미리보기 조각 " + session.pieceCount() + "개"
                    + (session.spot() == null ? " | 놓을 자리 못 찾음" : ""));
        }
    }

    // ---------------- 매 틱 ----------------

    /** 시선을 따라 미리보기를 옮긴다. */
    public void followAll() {
        for (PlacementSession session : Map.copyOf(sessions).values()) {
            Player player = session.player();
            if (!player.isOnline()) {
                cancel(player, false);
                continue;
            }
            // 손에서 놓으면 놓을 게 없다.
            if (!isHolding(player, session)) {
                cancel(player, true);
                continue;
            }
            follow(session);
            showHint(session);
        }
    }

    private void follow(PlacementSession session) {
        Anchoring.Spot spot = Anchoring.aim(
                session.player(), session.definition(), session.alignmentRule(), config.reach());
        if (spot == null) {
            session.markBlocked();
            return;
        }
        session.showAt(spot.location(), spot.variant());
    }

    private void showHint(PlacementSession session) {
        if (!config.actionBar()) return;
        String key = session.isBlocked() ? "hint-blocked" : "hint";
        Component hint = config.message(key, session.isBlocked()
                ? "<red>여기에는 놓을 수 없다"
                : "<gray>좌클릭 <white>회전<gray> · 우클릭 <white>설치<gray> · 웅크리기 <white>취소");
        session.player().sendActionBar(hint);
    }

    // ---------------- 끝내기 ----------------

    /** 놓는다. 성공하면 아이템 하나를 쓴다. */
    public boolean confirm(Player player) {
        PlacementSession session = session(player);
        if (session == null) return false;

        Location spot = session.spot();
        if (spot == null || session.isBlocked()) {
            player.sendMessage(config.message("cannot-place", "<red>여기에는 놓을 수 없다."));
            return false;
        }

        var placed = furnitures.place(session.definition(), session.variantName(), spot, session.yaw());
        if (placed == null) {
            player.sendMessage(config.message("place-failed", "<red>가구를 놓지 못했다."));
            return false;
        }

        // 아이템은 놓는 데 성공한 순간에만 쓴다. 실패했는데 사라지면 안 된다.
        if (player.getGameMode() != GameMode.CREATIVE) {
            consumeOne(player);
        }
        player.playSound(spot, Sound.BLOCK_WOOD_PLACE, 0.9f, 1.0f);
        cancel(player, false);
        return true;
    }

    public void cancel(Player player, boolean notify) {
        PlacementSession session = sessions.remove(player.getUniqueId());
        if (session == null) return;
        session.cleanup();
        if (notify && player.isOnline() && config.actionBar()) {
            player.sendActionBar(Component.empty());
        }
    }

    public void cancelAll() {
        for (UUID id : Map.copyOf(sessions).keySet()) {
            PlacementSession session = sessions.remove(id);
            if (session != null) session.cleanup();
        }
    }

    // ---------------- 도구 ----------------

    /** 아직 그 가구 아이템을 들고 있는지. */
    private boolean isHolding(Player player, PlacementSession session) {
        String want = furnitures.itemId(session.item());
        if (want == null) return false;
        return want.equals(furnitures.itemId(player.getInventory().getItemInMainHand()));
    }

    private void consumeOne(Player player) {
        ItemStack inHand = player.getInventory().getItemInMainHand();
        if (inHand.getType().isAir()) return;
        inHand.setAmount(inHand.getAmount() - 1);
        player.getInventory().setItem(EquipmentSlot.HAND, inHand.getAmount() <= 0 ? null : inHand);
    }
}
