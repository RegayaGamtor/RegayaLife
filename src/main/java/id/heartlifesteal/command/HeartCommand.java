package id.heartlifesteal.command;

import id.heartlifesteal.HeartLifestealPlugin;
import id.heartlifesteal.config.Settings;
import id.heartlifesteal.data.EliminationRecord;
import id.heartlifesteal.data.PlayerData;
import id.heartlifesteal.item.ItemFactory.Kind;
import id.heartlifesteal.service.HeartService;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** Command /heart beserta seluruh subcommand dan tab-completion. */
public final class HeartCommand implements TabExecutor {

    private enum Op { SET, ADD, REMOVE }

    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault());

    private static final List<String> SUBCOMMANDS =
            List.of("help", "top", "info", "set", "add", "remove", "revive", "reset", "reload", "give", "history");

    private final HeartLifestealPlugin plugin;

    public HeartCommand(HeartLifestealPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean allowed(CommandSender sender, String sub) {
        return switch (sub) {
            case "top" -> sender.hasPermission("heartlifesteal.top");
            case "info" -> sender.hasPermission("heartlifesteal.info");
            case "set", "add", "remove", "revive", "reset", "reload", "give", "history" ->
                    sender.hasPermission("heartlifesteal.admin." + sub);
            default -> true;
        };
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            own(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (!SUBCOMMANDS.contains(sub)) {
            plugin.messages().send(sender, "unknown-subcommand");
            return true;
        }
        if (!allowed(sender, sub)) {
            plugin.messages().send(sender, "no-permission");
            return true;
        }
        switch (sub) {
            case "help" -> help(sender);
            case "top" -> top(sender);
            case "info" -> info(sender, args);
            case "set" -> modify(sender, args, Op.SET);
            case "add" -> modify(sender, args, Op.ADD);
            case "remove" -> modify(sender, args, Op.REMOVE);
            case "revive" -> revive(sender, args);
            case "reset" -> reset(sender, args);
            case "reload" -> reload(sender);
            case "give" -> give(sender, args);
            case "history" -> history(sender, args);
            default -> help(sender);
        }
        return true;
    }

    // ------------------------------------------------------------------ pemain

    private void own(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "player-only");
            return;
        }
        if (!player.hasPermission("heartlifesteal.use")) {
            plugin.messages().send(sender, "no-permission");
            return;
        }
        PlayerData d = plugin.hearts().profile(player);
        plugin.messages().send(sender, "own-hearts", "hearts", d.hearts(),
                "max", plugin.settings().limits.maxHearts(), "status", plugin.hearts().statusText(d));
    }

    private void help(CommandSender sender) {
        plugin.messages().sendPlain(sender, "help-header");
        for (String line : plugin.messages().list("help-lines")) {
            // baris bantuan dengan format "perintah|permission"
            String[] parts = line.split("\\|", 2);
            if (parts.length == 2 && !parts[1].isBlank() && !sender.hasPermission(parts[1].trim())) continue;
            sender.sendMessage(id.heartlifesteal.util.Text.color(parts[0]));
        }
    }

    private void top(CommandSender sender) {
        Settings s = plugin.settings();
        List<PlayerData> list = plugin.data().all().stream()
                .filter(d -> !(s.topHideEliminated && d.eliminated()))
                .sorted(Comparator.comparingInt(PlayerData::hearts).reversed()
                        .thenComparing(PlayerData::name, String.CASE_INSENSITIVE_ORDER))
                .limit(s.topSize)
                .collect(Collectors.toList());
        if (list.isEmpty()) {
            plugin.messages().send(sender, "top-empty");
            return;
        }
        plugin.messages().sendPlain(sender, "top-header", "size", list.size());
        int rank = 1;
        for (PlayerData d : list) {
            plugin.messages().sendPlain(sender, "top-line", "rank", rank++, "player", d.name(),
                    "hearts", d.hearts(), "max", s.limits.maxHearts());
        }
    }

    private void info(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.messages().send(sender, "usage-info");
            return;
        }
        PlayerData d = resolve(sender, args[1]);
        if (d == null) return;
        plugin.messages().send(sender, "info-hearts", "player", d.name(), "hearts", d.hearts(),
                "max", plugin.settings().limits.maxHearts(), "status", plugin.hearts().statusText(d));
    }

    // ------------------------------------------------------------------- admin

    private void modify(CommandSender sender, String[] args, Op op) {
        String usageKey = "usage-" + op.name().toLowerCase(Locale.ROOT);
        if (args.length < 3) {
            plugin.messages().send(sender, usageKey);
            return;
        }
        PlayerData d = resolve(sender, args[1]);
        if (d == null) return;
        Integer amount = parseInt(sender, args[2]);
        if (amount == null) return;
        if (amount < 0 || (op != Op.SET && amount == 0)) {
            plugin.messages().send(sender, "invalid-number", "value", args[2]);
            return;
        }

        HeartService hearts = plugin.hearts();
        int target = switch (op) {
            case SET -> amount;
            case ADD -> d.hearts() + amount;
            case REMOVE -> d.hearts() - amount;
        };
        HeartService.Change change = hearts.setHearts(d, target, "ADMIN", null);

        plugin.messages().send(sender, "admin-heart-changed", "player", d.name(),
                "before", change.before(), "hearts", change.after(), "max", plugin.settings().limits.maxHearts());
        if (change.eliminatedNow()) plugin.messages().send(sender, "admin-eliminated", "player", d.name());
        if (change.revivedNow()) plugin.messages().send(sender, "admin-lifted-elimination", "player", d.name());

        Player online = Bukkit.getPlayerExact(d.name());
        if (online != null && !change.eliminatedNow() && !sender.equals(online)) {
            plugin.messages().send(online, "heart-changed-by-admin", "hearts", change.after(),
                    "max", plugin.settings().limits.maxHearts());
        }
    }

    private void revive(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.messages().send(sender, "usage-revive");
            return;
        }
        if (!plugin.settings().reviveEnabled) {
            plugin.messages().send(sender, "revive-disabled");
            return;
        }
        PlayerData d = resolve(sender, args[1]);
        if (d == null) return;
        if (!plugin.hearts().revive(d)) {
            plugin.messages().send(sender, "not-eliminated", "player", d.name());
            return;
        }
        plugin.messages().send(sender, "admin-revived", "player", d.name(), "hearts", d.hearts());
    }

    private void reset(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.messages().send(sender, "usage-reset");
            return;
        }
        PlayerData d = resolve(sender, args[1]);
        if (d == null) return;
        HeartService.Change change = plugin.hearts().reset(d);
        plugin.messages().send(sender, "admin-reset", "player", d.name(), "hearts", change.after());
    }

    private void reload(CommandSender sender) {
        plugin.reloadAll();
        plugin.messages().send(sender, "reloaded");
    }

    private void give(CommandSender sender, String[] args) {
        if (args.length < 4) {
            plugin.messages().send(sender, "usage-give");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            plugin.messages().send(sender, "player-offline", "player", args[1]);
            return;
        }
        String type = args[2].toLowerCase(Locale.ROOT);
        Kind kind;
        switch (type) {
            case "blood" -> kind = Kind.BLOOD;
            case "relic" -> kind = Kind.RELIC;
            default -> {
                plugin.messages().send(sender, "usage-give");
                return;
            }
        }
        Integer amount = parseInt(sender, args[3]);
        if (amount == null) return;
        if (amount < 1 || amount > 2304) {
            plugin.messages().send(sender, "invalid-number", "value", args[3]);
            return;
        }
        Settings s = plugin.settings();
        boolean enabled = kind == Kind.BLOOD ? s.blood.enabled() : s.relic.enabled();
        if (!enabled) {
            plugin.messages().send(sender, "item-disabled");
            return;
        }
        plugin.items().give(target, kind, amount);
        String itemName = kind == Kind.BLOOD ? "Blood" : "Relic of Life";
        plugin.messages().send(sender, "admin-given", "player", target.getName(), "amount", amount, "item", itemName);
        plugin.messages().send(target, "item-received", "amount", amount, "item", itemName);
    }

    private void history(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.messages().send(sender, "usage-history");
            return;
        }
        PlayerData d = resolve(sender, args[1]);
        if (d == null) return;
        plugin.data().history(d.uuid(), 10).whenComplete((list, error) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (error != null) {
                        plugin.messages().send(sender, "history-error");
                        plugin.getLogger().warning("Gagal membaca riwayat: " + error.getMessage());
                        return;
                    }
                    if (list.isEmpty()) {
                        plugin.messages().send(sender, "history-empty", "player", d.name());
                        return;
                    }
                    plugin.messages().sendPlain(sender, "history-header", "player", d.name());
                    for (EliminationRecord r : list) {
                        plugin.messages().sendPlain(sender, "history-line",
                                "date", DATE.format(Instant.ofEpochMilli(r.createdAt())),
                                "cause", r.cause(),
                                "killer", r.killerName() == null ? "-" : r.killerName());
                    }
                }));
    }

    // ------------------------------------------------------------------ helper

    private PlayerData resolve(CommandSender sender, String name) {
        Player online = Bukkit.getPlayerExact(name);
        PlayerData d = online != null ? plugin.hearts().profile(online) : plugin.data().findByName(name);
        if (d == null) plugin.messages().send(sender, "player-not-found", "player", name);
        return d;
    }

    private Integer parseInt(CommandSender sender, String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException ex) {
            plugin.messages().send(sender, "invalid-number", "value", text);
            return null;
        }
    }

    // --------------------------------------------------------------- tab complete

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String sub : SUBCOMMANDS) if (allowed(sender, sub)) out.add(sub);
        } else {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (!SUBCOMMANDS.contains(sub) || !allowed(sender, sub)) return out;
            if (args.length == 2) {
                switch (sub) {
                    case "info", "set", "add", "remove", "reset", "history" ->
                            Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
                    case "give" -> Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
                    case "revive" -> plugin.data().all().stream()
                            .filter(PlayerData::eliminated).forEach(d -> out.add(d.name()));
                    default -> { }
                }
            } else if (args.length == 3) {
                switch (sub) {
                    case "set", "add", "remove" -> out.addAll(List.of("1", "5", "10"));
                    case "give" -> out.addAll(List.of("blood", "relic"));
                    default -> { }
                }
            } else if (args.length == 4 && sub.equals("give")) {
                out.addAll(List.of("1", "5", "16", "64"));
            }
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        return out.stream().filter(x -> x.toLowerCase(Locale.ROOT).startsWith(prefix)).collect(Collectors.toList());
    }
}
