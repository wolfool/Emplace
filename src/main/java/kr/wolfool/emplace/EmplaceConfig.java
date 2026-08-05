package kr.wolfool.emplace;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.plugin.Plugin;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** config.yml 을 읽어 들고 있는다. {@code /emplace reload} 로 다시 읽는다. */
public final class EmplaceConfig {

    private final Plugin plugin;

    private Set<String> excluded = Set.of();
    private double reach = 5.0;
    private boolean glow = true;
    private boolean actionBar = true;
    private boolean sneakToCancel = true;
    private boolean debug = false;

    public EmplaceConfig(Plugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.reloadConfig();
        var cfg = plugin.getConfig();

        // 소문자로 눕혀 둔다. 설정에 대문자로 적어도 걸리게.
        Set<String> ids = new HashSet<>();
        for (String id : cfg.getStringList("excluded-furniture")) {
            if (id != null && !id.isBlank()) ids.add(id.trim().toLowerCase(Locale.ROOT));
        }
        this.excluded = Set.copyOf(ids);

        this.reach = cfg.getDouble("reach", 5.0);
        this.glow = cfg.getBoolean("preview.glow", true);
        this.actionBar = cfg.getBoolean("preview.action-bar", true);
        this.sneakToCancel = cfg.getBoolean("controls.sneak-to-cancel", true);
        this.debug = cfg.getBoolean("debug", false);
    }

    /** 켜면 가로챈 가구와 미리보기 조각 수를 로그에 남긴다. 안 보일 때 원인을 찾는 용도. */
    public boolean debug() {
        return debug;
    }

    /**
     * 이 가구를 미리보기로 놓을지.
     *
     * <p>제외 목록에 있으면 CraftEngine 이 원래 하던 대로 즉시 놓인다.
     */
    public boolean handles(String furnitureId) {
        return furnitureId != null && !excluded.contains(furnitureId.toLowerCase(Locale.ROOT));
    }

    public Set<String> excluded() {
        return excluded;
    }

    public double reach() {
        return reach;
    }

    public boolean glow() {
        return glow;
    }

    public boolean actionBar() {
        return actionBar;
    }

    public boolean sneakToCancel() {
        return sneakToCancel;
    }

    /** config 의 문구 하나. MiniMessage 로 읽는다. */
    public Component message(String key, String fallback) {
        String raw = plugin.getConfig().getString("messages." + key, fallback);
        return MiniMessage.miniMessage().deserialize(raw == null ? fallback : raw);
    }

    /** 자리 표시자 하나를 끼워 넣는 문구. */
    public Component message(String key, String fallback, String placeholder, String value) {
        String raw = plugin.getConfig().getString("messages." + key, fallback);
        if (raw == null) raw = fallback;
        return MiniMessage.miniMessage().deserialize(raw.replace("<" + placeholder + ">", value));
    }

    /** 조작 안내. 미리보기를 띄우는 동안 액션바에 계속 보여준다. */
    public List<String> controlHints() {
        return List.of(
                plugin.getConfig().getString("messages.hint", ""));
    }
}
