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
    private final com.wolfool.emplace.cleanup.GhostSweeper sweeper;

    public EmplaceCommand(Plugin plugin, EmplaceConfig config, PlacementManager placement,
                          com.wolfool.emplace.cleanup.GhostSweeper sweeper) {
        this.plugin = plugin;
        this.config = config;
        this.placement = placement;
        this.sweeper = sweeper;
    }

    /**
     * {@code /emplace 청소 [반경]} — 주변에 남은 안 보이는 판정을 치웁니다.
     *
     * <p>가구를 부술 때마다 자동으로도 돌지만, 예전에 남겨 둔 것과 회전 중에
     * 생긴 것은 손으로 한 번 훑어야 없어집니다.
     */
    /**
     * {@code /emplace 진단 [반경]} — 둘레의 엔티티를 있는 그대로 보여 줍니다.
     *
     * <p>치우는 그물에 안 걸리는 판정이 계속 남아서 만들었습니다. <b>무엇인지
     * 모르는 채로 그물만 넓히면 멀쩡한 것을 지우게 됩니다.</b> 종류·CraftEngine
     * 이 뭐라고 하는지·표식·저장 여부를 그대로 찍어, 남은 것이 정확히 무엇인지
     * 보고 나서 손봅니다.
     */
    private boolean scan(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("플레이어만 쓸 수 있습니다.").color(NamedTextColor.RED));
            return true;
        }
        double radius = 8;
        if (args.length > 1) {
            try {
                radius = Math.max(1, Math.min(64, Double.parseDouble(args[1])));
            } catch (NumberFormatException e) {
                radius = 8;
            }
        }
        var here = player.getLocation();
        int n = 0;
        boolean whole = args.length > 1
                && (args[1].equals("전체") || args[1].equalsIgnoreCase("all"));
        sender.sendMessage(Component.text(whole ? "── 이 월드 전체 ──"
                        : "── 반경 " + (int) radius + "칸 엔티티 ──")
                .color(NamedTextColor.GOLD));
        for (var e : whole ? here.getWorld().getEntities()
                : here.getWorld().getNearbyEntities(here, radius, radius, radius)) {
            if (e instanceof Player) continue;
            n++;
            String ce;
            try {
                ce = (net.momirealms.craftengine.bukkit.api.CraftEngineFurniture.isCollisionEntity(e)
                        ? "충돌" : net.momirealms.craftengine.bukkit.api.CraftEngineFurniture.isSeat(e)
                        ? "좌석" : net.momirealms.craftengine.bukkit.api.CraftEngineFurniture.isFurniture(e)
                        ? "몸통" : "CE아님");
            } catch (Throwable t) {
                ce = "CE?";
            }
            var pdc = e.getPersistentDataContainer();
            String keys = pdc.isEmpty() ? "-" : String.valueOf(pdc.getKeys());
            double d = e.getLocation().distance(here);
            sender.sendMessage(Component.text(String.format(
                            "%-16s %-5s 저장%s 표식%s 탈것%d %.1f칸",
                            e.getType().name(), ce, e.isPersistent() ? "O" : "X",
                            keys.length() > 28 ? keys.substring(0, 28) : keys,
                            e.getPassengers().size(), d))
                    .color(com.wolfool.emplace.cleanup.GhostSweeper.isGhost(e)
                            || com.wolfool.emplace.cleanup.GhostSweeper.looksLikeLeftover(e)
                            ? NamedTextColor.RED : NamedTextColor.GRAY));
        }
        sender.sendMessage(Component.text("모두 " + n + "개 · 빨간 줄이 치울 대상입니다")
                .color(NamedTextColor.GOLD));
        return true;
    }

    private boolean sweep(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("플레이어만 쓸 수 있습니다.").color(NamedTextColor.RED));
            return true;
        }
        if (!sender.hasPermission("emplace.admin")) {
            sender.sendMessage(Component.text("권한이 없습니다.").color(NamedTextColor.RED));
            return true;
        }
        // "전체" 를 주면 월드에 올라온 것을 통째로 훑습니다.
        // 화면에 보이는 자리와 서버가 아는 자리가 다른 엔티티는 반경으로 못 잡습니다
        if (args.length > 1 && (args[1].equals("전체") || args[1].equalsIgnoreCase("all"))) {
            int all = sweeper.sweepWorld(player.getWorld());
            sender.sendMessage(Component.text(all == 0
                            ? "이 월드에 주인 없는 판정이 없습니다."
                            : "월드 전체에서 주인 없는 판정 " + all + "개를 치웠습니다.")
                    .color(NamedTextColor.GREEN));
            return true;
        }
        double radius = 8;
        if (args.length > 1) {
            try {
                radius = Math.max(1, Math.min(64, Double.parseDouble(args[1])));
            } catch (NumberFormatException e) {
                sender.sendMessage(Component.text("반경은 숫자여야 합니다.").color(NamedTextColor.RED));
                return true;
            }
        }
        int removed = sweeper.sweep(player.getLocation(), radius);
        if (removed == 0) {
            sender.sendMessage(Component.text("반경 " + (int) radius + "칸 안에 남은 판정이 없습니다.")
                    .color(NamedTextColor.GREEN));
            sender.sendMessage(Component.text("보이는데 안 잡히면 /emplace 청소 전체 를 써 보세요.")
                    .color(NamedTextColor.DARK_GRAY));
        } else {
            sender.sendMessage(Component.text("주인 없는 판정 " + removed + "개를 치웠습니다.")
                    .color(NamedTextColor.GREEN));
            sender.sendMessage(Component.text("살아 있는 가구의 판정은 건드리지 않았습니다.")
                    .color(NamedTextColor.DARK_GRAY));
        }
        return true;
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

        if (args[0].equalsIgnoreCase("sweep") || args[0].equals("청소")) {
            return sweep(sender, args);
        }

        if (args[0].equalsIgnoreCase("scan") || args[0].equals("진단")) {
            return scan(sender, args);
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

        sender.sendMessage(Component.text("/emplace [status|reload|cancel|격자|청소|진단]")
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
            for (String sub : new String[]{"status", "reload", "cancel", "격자", "청소", "진단"}) {
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
