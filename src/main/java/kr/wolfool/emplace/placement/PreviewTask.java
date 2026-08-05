package kr.wolfool.emplace.placement;

import org.bukkit.scheduler.BukkitRunnable;

/**
 * 미리보기를 시선에 맞춰 옮긴다.
 *
 * <p>매 틱 돈다. 그보다 뜸하면 고개를 돌릴 때 미리보기가 끊겨 보인다.
 * 놓는 중인 사람이 없으면 하는 일이 없다.
 */
public final class PreviewTask extends BukkitRunnable {

    private final PlacementManager placement;

    public PreviewTask(PlacementManager placement) {
        this.placement = placement;
    }

    @Override
    public void run() {
        placement.followAll();
    }
}
