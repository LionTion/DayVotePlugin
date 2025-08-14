import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.util.config.Configuration;
import org.bukkit.util.config.ConfigurationNode;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class Main extends JavaPlugin {

    private Logger logger;
    ConcurrentHashMap<String, Vote> votes = new ConcurrentHashMap<>();
    ConcurrentHashMap<String, LocalDateTime> voteTimeouts = new ConcurrentHashMap<>();

    public void checkoutConfig() {
        Configuration c = Config.get();
        HashMap<String, Object> init = new HashMap<>();
        ConfigurationNode baseOpt = c.getNode("*");
        boolean write = false;
        if (baseOpt == null) {
            init.put(Config.shouldRain, true);
            init.put(Config.votePerc, 100.0d);
            write = true;
        } else {
            Object shouldRain = baseOpt.getProperty(Config.shouldRain);
            Object votePerc = baseOpt.getProperty(Config.votePerc);
            if (shouldRain == null) {
                init.put(Config.shouldRain, true);
                write = true;
            } else {
                init.put(Config.shouldRain, shouldRain);
            }
            if (votePerc == null) {
                init.put(Config.votePerc, 100.0d);
                write = true;
            } else {
                init.put(Config.votePerc, votePerc);
            }
        }
        if (write) {
            c.setProperty("*", init);
            Config.save();
            Config.reload();
        }
    }

    @Override
    public void onDisable() {

    }

    @Override
    public void onEnable() {
        logger = Logger.getLogger("DayVote");

        logger.info("Preparing day-votes!");

        Config.init();
        checkoutConfig();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equals("reloaddayvoteconfig")) {
            Config.reload();
            checkoutConfig();
            sender.sendMessage(ChatColor.RED + "   ! " + ChatColor.GREEN + "Day-Vote Config reloaded!");
            return true;
        }
        if (sender instanceof Player) {
            Player caller = (Player) sender;
            World world = caller.getWorld();
            switch (command.getName()) {
                case "dayvote":
                    LocalDateTime cn = LocalDateTime.now();
                    Enumeration<String> worlds = voteTimeouts.keys();
                    while (worlds.hasMoreElements()) {
                        String k = worlds.nextElement();
                        LocalDateTime ct = voteTimeouts.getOrDefault(k, null);
                        if (ct == null) {
                            continue;
                        }
                        if (ct.isBefore(cn)) {
                            voteTimeouts.remove(k);
                        }
                    }

                    boolean playInitSound = true;
                    if (!votes.containsKey(world.getName())) {
                        LocalDateTime tn = voteTimeouts.getOrDefault(world.getName(), null);
                        if (tn != null) {
                            LocalDateTime n = LocalDateTime.now();
                            if (tn.isAfter(n)) {
                                long seconds = tn.toEpochSecond(ZoneOffset.UTC) - n.toEpochSecond(ZoneOffset.UTC);
                                caller.sendMessage(ChatColor.RED + "Wait " + seconds + "s before starting a Day-Vote on this world!");
                                caller.playEffect(caller.getLocation(), Effect.EXTINGUISH, 2);
                                break;
                            }
                        }
                        if (world.getEnvironment().getId() == -1) {
                            caller.sendMessage(ChatColor.RED + "Go sleep in a bed in the nether.");
                            caller.playEffect(caller.getLocation(), Effect.EXTINGUISH, 2);
                            break;
                        }
                        if (world.getTime() < 10000) {
                            caller.sendMessage(ChatColor.RED + "Kill the sun first. It's still up there!");
                            caller.playEffect(caller.getLocation(), Effect.EXTINGUISH, 2);
                            break;
                        }
                        playInitSound = false;
                        BukkitScheduler scheduler = getServer().getScheduler();
                        Player[] players = world.getPlayers().toArray(new Player[0]);
                        final String worldName = world.getName();
                        int st = scheduler.scheduleSyncDelayedTask(this, () -> {
                            if (votes.containsKey(worldName)) {
                                votes.remove(worldName);
                                World w = getServer().getWorld(worldName);
                                if (w != null) {
                                    String msg = ChatColor.AQUA + "   ! " + ChatColor.RED + "Day-Vote failed!";
                                    Player[] ps = w.getPlayers().toArray(new Player[0]);
                                    for (Player p : ps) {
                                        p.sendMessage(msg);
                                        p.playEffect(p.getLocation(), Effect.BOW_FIRE, 1);
                                    }
                                }
                            }
                        }, 20L * 60L * 2L);
                        votes.put(world.getName(), new Vote(players, Config.votePercForWorld(worldName), st));
                        voteTimeouts.put(world.getName(), LocalDateTime.now().plusMinutes(8));
                        String msg1 = ChatColor.AQUA + "   ! " + ChatColor.GOLD + "Day-Vote started!" + ChatColor.AQUA + " !";
                        String msg2 = ChatColor.AQUA + "   ! " + ChatColor.YELLOW + "Type " + ChatColor.GRAY + "/voteday" + ChatColor.DARK_GRAY + " (or /vd)" + ChatColor.YELLOW + " to cast a vote" + ChatColor.AQUA + " !";
                        for (Player player : players) {
                            player.sendMessage(msg1);
                            player.sendMessage(msg2);
                        }
                        int[] d = new int[2];
                        d[0] = 3;
                        d[1] = scheduler.scheduleSyncRepeatingTask(this, () -> {
                            for (Player player : players) {
                                player.playEffect(player.getLocation(), Effect.CLICK1, 2);
                            }
                            if (--d[0] < 1) {
                                scheduler.cancelTask(d[1]);
                            }
                        }, 0L, 3L);
                    }
                    Vote v = votes.getOrDefault(world.getName(), null);
                    if (v == null) {
                        caller.sendMessage(ChatColor.RED + "No Vote going on.");
                        break;
                    }
                    boolean fullVote = false;
                    synchronized (v) {
                        VoteStatus vs = v.hasVoted(caller);
                        switch (vs) {
                            case NOT_ALLOWED:
                                caller.sendMessage(ChatColor.RED + "Sorry!" + ChatColor.YELLOW + " You were not in this world when the vote started!");
                                caller.playEffect(caller.getLocation(), Effect.BOW_FIRE, 2);
                                break;
                            case NOT_VOTED:
                                int[] s = v.vote(caller);
                                int required = (int) Math.floor(s[1] * (v.votePerc / 100.0d));
                                if (s[0] >= required) {
                                    fullVote = true;
                                }
                                String requiredString = String.valueOf(required == 0 ? s[0] : required);
                                String countSnippet = ChatColor.GOLD + "( " + ChatColor.GREEN + s[0] + ChatColor.GOLD + " / " + ChatColor.AQUA + requiredString + ChatColor.GOLD + " )";
                                caller.sendMessage(ChatColor.GREEN + "Vote cast!" + ChatColor.YELLOW + " May the sun shine again! " + countSnippet);
                                if (playInitSound) caller.playEffect(caller.getLocation(), Effect.CLICK2, 2);
                                Player[] players = world.getPlayers().toArray(new Player[0]);
                                String message = ChatColor.AQUA + "   ! " + ChatColor.WHITE + caller.getDisplayName() + ChatColor.WHITE + " casted a day-vote! " + countSnippet + ChatColor.DARK_GRAY + " (/vd)";
                                for (Player player : players) {
                                    if (player.getUniqueId().equals(caller.getUniqueId())) {
                                        continue;
                                    }
                                    player.sendMessage(message);
                                    if (playInitSound) player.playEffect(player.getLocation(), Effect.CLICK1, 1);
                                }
                                break;
                            case VOTED:
                                caller.sendMessage(ChatColor.GREEN + "Already voted for day!");
                                caller.playEffect(caller.getLocation(), Effect.CLICK2, 1);
                                break;
                        }
                    }
                    if (fullVote) {
                        world.setTime(0);
                        if (Config.shouldClearRainForWorld(world.getName())) {
                            world.setStorm(false);
                            world.setThundering(false);
                            world.setThunderDuration(0);
                        }
                        votes.remove(world.getName());
                        getServer().getScheduler().cancelTask(v.scheduledTask);
                        Player[] players = world.getPlayers().toArray(new Player[0]);
                        String message = ChatColor.AQUA + "   ! " + ChatColor.GOLD + " Day-Vote successful!";
                        for (Player player : players) {
                            player.sendMessage(message);
                        }
                    }
                    break;
                case "cancelvote":
                    Vote cv = votes.getOrDefault(world.getName(), null);
                    if (cv == null) {
                        caller.sendMessage(ChatColor.RED + "   ! There's no active Day-Vote in this world!");
                        break;
                    }
                    votes.remove(world.getName());
                    getServer().getScheduler().cancelTask(cv.scheduledTask);
                    caller.sendMessage(ChatColor.RED + "   ! " + ChatColor.GREEN + "Day-Vote cancelled!");
                    caller.playEffect(caller.getLocation(), Effect.BOW_FIRE, 2);

                    Player[] players = world.getPlayers().toArray(new Player[0]);
                    String message = ChatColor.RED + "   ! " + ChatColor.WHITE + "Day-Vote was " + ChatColor.RED + "cancelled!";
                    for (Player player : players) {
                        if (player.getUniqueId().equals(caller.getUniqueId())) {
                            continue;
                        }
                        player.sendMessage(message);
                        player.playEffect(player.getLocation(), Effect.EXTINGUISH, 1);
                    }
                    break;
                case "cleardayvotetimeout":
                    if (!voteTimeouts.containsKey(world.getName())) {
                        caller.sendMessage(ChatColor.RED + "   ! There's no Day-Vote Timeout in this world!");
                        break;
                    }
                    voteTimeouts.remove(world.getName());
                    caller.sendMessage(ChatColor.RED + "   ! " + ChatColor.GREEN + "Day-Vote Timeout removed!");
                    caller.playEffect(caller.getLocation(), Effect.BOW_FIRE, 2);
                    break;
            }
        }
        return true;
    }
}