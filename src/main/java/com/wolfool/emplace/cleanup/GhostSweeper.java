package com.wolfool.emplace.cleanup;

import net.momirealms.craftengine.bukkit.api.CraftEngineFurniture;
import net.momirealms.craftengine.bukkit.api.event.FurnitureBreakEvent;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

/**
 * 부순 자리에 남는 <b>안 보이는 벽</b>을 치웁니다.
 *
 * <p>CraftEngine 가구는 블록이 아니라 엔티티 뭉치입니다 — 보이는 몸통
 * (item_display), 상태를 들고 있는 base 엔티티, 그리고 실제로 몸을 막는
 * 히트박스(shulker 또는 interaction + 충돌 엔티티)입니다. 가구를 회전시키면
 * 히트박스를 새 각도로 다시 놓는데, 이때 옛 히트박스가 안 지워진 채 남는 일이
 * 있습니다. 그 뒤 가구를 부수면 몸통만 사라지고 <b>보이지 않는 판정만 남습니다.</b>
 * {@code /setblock ~ ~ ~ air} 로 안 지워지는 이유가 이것입니다.
 *
 * <h2>어떻게 유령인지 가려내는가</h2>
 *
 * <p>추측하지 않습니다. CraftEngine 에게 직접 묻습니다.
 *
 * <ul>
 *   <li>{@code isCollisionEntity(e)} 가 참인데
 *       {@code getLoadedFurnitureByCollider(e)} 가 null → <b>주인 없는 충돌 판정</b>
 *   <li>{@code isSeat(e)} 가 참인데 {@code getLoadedFurnitureBySeat(e)} 가 null →
 *       주인 없는 좌석
 *   <li>{@code isFurniture(e)} 가 참인데 {@code getLoadedFurnitureByMetaEntity(e)}
 *       가 null → 주인 없는 몸통
 * </ul>
 *
 * <p>세 조건 모두 "CraftEngine 이 자기 것이라고는 하는데, 어느 가구에도 속하지
 * 않는다" 는 뜻입니다. <b>살아 있는 가구의 판정은 이 그물에 걸리지 않습니다</b> —
 * 반경 안을 통째로 {@code /kill} 하던 방법과 결정적으로 다른 점입니다.
 */
public final class GhostSweeper implements Listener {

    private final Plugin plugin;
    /** 가구를 부순 뒤 이 반경만 살핍니다 (블록). */
    private final double breakRadius;
    /** 부순 다음 몇 틱 뒤에 볼지. CraftEngine 이 자기 몫을 치울 틈을 줍니다. */
    private final int delayTicks;
    private final boolean sweepOnBreak;

    /** CraftEngine 이 아예 자기 것이라고도 안 하는 찌꺼기까지 치울지. */
    private final boolean deep;

    public GhostSweeper(Plugin plugin, boolean sweepOnBreak, double breakRadius,
                        int delayTicks, boolean deep) {
        this.plugin = plugin;
        this.sweepOnBreak = sweepOnBreak;
        this.breakRadius = Math.max(1.0, breakRadius);
        this.delayTicks = Math.max(1, delayTicks);
        this.deep = deep;
    }

    /**
     * 가구를 부수면 그 언저리를 살핍니다.
     *
     * <p><b>{@code MONITOR} 에서 미룬 뒤에 봅니다.</b> 부수기가 취소될 수도 있고,
     * 취소되지 않았더라도 CraftEngine 이 자기 엔티티를 지우는 것은 이벤트가 다
     * 지나간 뒤입니다. 그 전에 세면 멀쩡한 판정이 전부 "주인 없음" 으로 보입니다.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFurnitureBreak(FurnitureBreakEvent event) {
        if (!sweepOnBreak) return;
        Location where = event.location();
        if (where == null || where.getWorld() == null) return;
        Location at = where.clone();
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> sweep(at, breakRadius), delayTicks);
        // 한 번 더 봅니다. CraftEngine 이 자기 엔티티를 늦게 지우는 경우가 있어서
        // 2틱에는 아직 "주인 있는 것" 으로 보이던 게 1초 뒤에 유령이 됩니다.
        // 회전시켰다 부순 자리에 판정이 남던 게 이 경우였습니다.
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> sweep(at, breakRadius), delayTicks + 20L);
    }

    /**
     * 그 언저리의 유령을 치웁니다.
     *
     * <p>청크가 안 열려 있으면 {@code getNearbyEntities} 가 열어 버립니다. 부순
     * 직후이거나 사람이 서 있는 자리라 이미 열려 있는 경우에만 부르세요.
     *
     * @return 치운 개수
     */
    public int sweep(Location center, double radius) {
        World world = center.getWorld();
        if (world == null) return 0;

        List<Entity> doomed = new ArrayList<>();
        for (Entity e : world.getNearbyEntities(center, radius, radius, radius)) {
            if (isGhost(e) || (deep && looksLikeLeftover(e))) doomed.add(e);
        }
        for (Entity e : doomed) {
            e.remove();
        }
        return doomed.size();
    }

    /**
     * 그 월드에 올라와 있는 것을 <b>통째로</b> 훑습니다.
     *
     * <p>반경으로는 못 잡는 것이 있습니다. 화면에 보이는 자리와 <b>서버가 아는
     * 자리가 다른</b> 엔티티가 그렇습니다 — 사용자가 F3 으로 UUID 를 읽어
     * {@code /kill} 하면 죽는데, 같은 자리를 반경으로 훑으면 아무것도 안
     * 나왔습니다. 서버 쪽 좌표가 딴 데 있었던 것입니다.
     *
     * <p>월드에 올라온 엔티티 전부를 보므로 반경보다 느리지만, 사람이 명령을
     * 칠 때만 도는 길이라 괜찮습니다.
     */
    public int sweepWorld(World world) {
        if (world == null) return 0;
        List<Entity> doomed = new ArrayList<>();
        for (Entity e : world.getEntities()) {
            if (isGhost(e) || (deep && looksLikeLeftover(e))) doomed.add(e);
        }
        for (Entity e : doomed) {
            e.remove();
        }
        return doomed.size();
    }

    /**
     * 주인 없는 가구 부속인지.
     *
     * <p>CraftEngine 이 아직 안 올라왔거나 API 가 바뀌면 예외가 납니다. 그때는
     * <b>유령이 아니라고 답합니다</b> — 판단이 안 서는 것을 지우면 멀쩡한 가구를
     * 부수게 됩니다.
     */
    public static boolean isGhost(Entity e) {
        try {
            if (CraftEngineFurniture.isCollisionEntity(e)) {
                return CraftEngineFurniture.getLoadedFurnitureByCollider(e) == null;
            }
            if (CraftEngineFurniture.isSeat(e)) {
                return CraftEngineFurniture.getLoadedFurnitureBySeat(e) == null;
            }
            if (CraftEngineFurniture.isFurniture(e)) {
                return CraftEngineFurniture.getLoadedFurnitureByMetaEntity(e) == null;
            }
            return false;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * CraftEngine 이 <b>자기 것이라고도 안 하는</b> 찌꺼기인지.
     *
     * <p>{@link #isGhost} 만으로는 못 걸리는 경우가 있습니다. 회전시킬 때 새
     * 히트박스를 놓으면서 옛것의 등록이 끊기면, CraftEngine 은 그 엔티티를 아예
     * 모르는 것으로 취급합니다. 그러면 "주인 없는 CE 것" 그물에 안 걸립니다.
     *
     * <p>그래서 생김새로 거릅니다. 아래를 <b>모두</b> 만족해야 지웁니다.
     *
     * <ul>
     *   <li>interaction 이거나 <b>안 보이는</b> 셜커 — 가구 판정이 쓰는 두 가지
     *   <li>태운 것도 탄 것도 없고, 이름도 안 붙어 있음 — 남의 플러그인 것 제외
     *   <li><b>PDC 가 비어 있음</b> — 표식을 남기는 플러그인(Simmer 의 조리대
     *       판정 등)의 것은 건드리지 않습니다
     *   <li>CraftEngine 이 아는 것이 아님 (살아 있는 가구의 판정 제외)
     * </ul>
     *
     * <p><b>저장 여부는 안 봅니다.</b> 처음엔 "저장되는 것" 만 봤는데, 실제로
     * 남던 판정은 <b>서버를 껐다 켜면 사라지는</b> 것이었습니다 — 저장되지 않는
     * 엔티티라 그 조건에서 통째로 빠져 있었습니다.
     */
    public static boolean looksLikeLeftover(Entity e) {
        try {
            boolean shape = e instanceof org.bukkit.entity.Interaction
                    || (e instanceof org.bukkit.entity.Shulker s && s.isInvisible());
            if (!shape) return false;
            if (!e.getPassengers().isEmpty() || e.getVehicle() != null) return false;
            if (e.customName() != null) return false;
            // 표식이 있으면 주인이 있는 것입니다. 남의 판정을 지우지 않습니다
            if (!e.getPersistentDataContainer().isEmpty()) return false;
            return !CraftEngineFurniture.isCollisionEntity(e)
                    && !CraftEngineFurniture.isSeat(e)
                    && !CraftEngineFurniture.isFurniture(e);
        } catch (Throwable t) {
            return false;
        }
    }
}
