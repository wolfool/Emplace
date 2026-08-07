package com.wolfool.emplace;

import com.wolfool.emplace.placement.Grid;
import com.wolfool.emplace.placement.PlacementManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

/** {@code /emplace} — 설정 다시 읽기와 상태 보기. */
public final class EmplaceCommand implements CommandExecutor, TabCompleter {

    private final Plugin plugin;
    private final EmplaceConfig config;
    private final PlacementManager placement;

    public EmplaceCommand(Plugin plugin, EmplaceConfig config, PlacementManager placement) {
        this.plugin = plugin;
        this.config = config;
        this.placement = placement;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            sender.sendMessage(Component.text("=== Emplace ===").color(NamedTextColor.GOLD));
            sender.sendMessage(Component.text("서버에 올라온 가구: ")
                    .color(NamedTextColor.GRAY)
                    .append(Component.text(placement.furnitures().loadedCount() + "종")
                            .color(NamedTextColor.WHITE)));
            sender.sendMessage(Component.text("제외한 가구: ")
                    .color(NamedTextColor.GRAY)
                    .append(Component.text(config.excluded().isEmpty()
                                    ? "없음 (전부 미리보기로 놓음)"
                                    : String.join(", ", config.excluded()))
                            .color(NamedTextColor.WHITE)));
            sender.sendMessage(Component.text("/emplace reload").color(NamedTextColor.YELLOW)
                    .append(Component.text(" - config.yml 다시 읽기").color(NamedTextColor.GRAY)));
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("emplace.admin")) {
                sender.sendMessage(Component.text("권한이 없습니다.").color(NamedTextColor.RED));
                return true;
            }
            config.reload();
            // 설정이 바뀌면 놓는 중인 미리보기가 옛 값으로 돌고 있을 수 있다. 정리한다.
            placement.cancelAll();
            sender.sendMessage(Component.text("config.yml 을 다시 읽었습니다.").color(NamedTextColor.GREEN));
            return true;
        }

        if (args[0].equalsIgnoreCase("cancel") && sender instanceof Player player) {
            placement.cancel(player, true);
            sender.sendMessage(Component.text("놓기를 그만두었습니다.").color(NamedTextColor.YELLOW));
            return true;
        }

        if ((args[0].equalsIgnoreCase("grid") || args[0].equals("격자"))
                && sender instanceof Player player) {
            return grid(player, args);
        }

        sender.sendMessage(Component.text("/emplace [status|reload|cancel|격자]")
                .color(NamedTextColor.GRAY));
        return true;
    }

    /** {@code /emplace 격자 [이름]} — 안 적으면 다음 격자로 넘깁니다. */
    private boolean grid(Player player, String[] args) {
        Grid now;
        if (args.length >= 2) {
            Grid picked = Grid.byName(args[1]);
            if (picked == null) {
                player.sendMessage(Component.text("'" + args[1] + "' 은(는) 없는 격자입니다.")
                        .color(NamedTextColor.RED));
                player.sendMessage(Component.text(names()).color(NamedTextColor.GRAY));
                return true;
            }
            now = picked;
        } else {
            now = placement.gridOf(player).next();
        }
        placement.gridOf(player, now);

        player.sendMessage(Component.text("격자 ").color(NamedTextColor.GRAY)
                .append(Component.text(now.korean()).color(NamedTextColor.YELLOW))
                .append(Component.text(" — " + describe(now)).color(NamedTextColor.DARK_GRAY)));
        return true;
    }

    /** 그 격자가 무엇에 좋은지. 이름만으로는 언제 쓸지 감이 안 옵니다. */
    private static String describe(Grid grid) {
        return switch (grid) {
            case FURNITURE -> "가구가 원래 정해 둔 자리";
            case CENTER -> "블록 한가운데에 딱 맞춥니다";
            case HALF -> "둘씩 붙여 놓을 때";
            case QUARTER -> "의자를 탁자 네 귀퉁이에 붙일 때";
            case EIGHTH -> "잘게 맞출 때";
            case FREE -> "본 자리에 그대로. 벽에 딱 붙일 때";
        };
    }

    private static String names() {
        List<String> out = new ArrayList<>();
        for (Grid grid : Grid.values()) out.add(grid.korean());
        return String.join(" · ", out);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String sub : new String[]{"status", "reload", "cancel", "격자"}) {
                if (sub.startsWith(args[0].toLowerCase())) out.add(sub);
            }
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("grid") || args[0].equals("격자"))) {
            for (Grid grid : Grid.values()) {
                if (grid.korean().startsWith(args[1])) out.add(grid.korean());
            }
        }
        return out;
    }
}
