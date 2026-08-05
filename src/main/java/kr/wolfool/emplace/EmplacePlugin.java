package kr.wolfool.emplace;

import kr.wolfool.emplace.placement.PlacementListener;
import kr.wolfool.emplace.placement.PlacementManager;
import kr.wolfool.emplace.placement.PreviewTask;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * CraftEngine 가구를 미리보기로 놓게 만든다.
 *
 * <p>CraftEngine 은 가구 아이템을 우클릭하면 그 자리에 즉시 놓는다. 이 플러그인은 그
 * 설치를 가로채서, 놓기 전에 <b>반투명 미리보기</b>로 자리와 방향을 잡게 한다.
 *
 * <p>미리보기는 가구 정의에 적힌 실제 렌더 값(크기·위치·회전·조명)을 그대로 읽어서
 * 만든다. 그래서 설정을 따로 베껴 적을 필요가 없고, 어떤 가구든 실물과 같게 보인다.
 */
public final class EmplacePlugin extends JavaPlugin {

    private EmplaceConfig config;
    private PlacementManager placement;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.config = new EmplaceConfig(this);
        this.placement = new PlacementManager(this, config);

        getServer().getPluginManager().registerEvents(new PlacementListener(this, placement, config), this);
        new PreviewTask(placement).runTaskTimer(this, 1L, 1L);

        EmplaceCommand command = new EmplaceCommand(this, config, placement);
        getCommand("emplace").setExecutor(command);
        getCommand("emplace").setTabCompleter(command);

        getLogger().info("가구 설치를 미리보기 방식으로 바꿨다.");
    }

    @Override
    public void onDisable() {
        // 미리보기는 persistent=false 라 서버가 꺼져도 남지 않지만,
        // /reload 처럼 월드가 살아 있는 채로 플러그인만 내려가는 경우가 있다.
        if (placement != null) placement.cancelAll();
    }

    public EmplaceConfig settings() {
        return config;
    }

    public PlacementManager placement() {
        return placement;
    }
}
