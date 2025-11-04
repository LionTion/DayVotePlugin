import org.bukkit.Bukkit;
import org.bukkit.util.config.Configuration;
import org.bukkit.util.config.ConfigurationNode;

import java.io.File;

public class Config {

    final public static String shouldRain = "shouldAlsoClearRain";
    final public static String votePerc = "votePercentage";

    private static Configuration config;

    public static void init() {
        config = new Configuration(new File(Bukkit.getServer().getPluginManager().getPlugin("DayVote").getDataFolder(), "config.yml"));
        config.load();
    }

    public static void reload() {
        config.load();
    }

    public static void save() {
        config.save();
    }

    public static Configuration get() {
        return config;
    }

    public static boolean shouldClearRainForWorld(String worldName) {
        ConfigurationNode worldSettings = config.getNode(worldName);
        if (worldSettings != null) {
            Object shouldRain = worldSettings.getProperty(Config.shouldRain);
            if (shouldRain instanceof Boolean) {
                return (Boolean) shouldRain;
            }
        }
        return config.getBoolean("*." + Config.shouldRain, true);
    }

    public static double votePercForWorld(String worldName) {
        ConfigurationNode worldSettings = config.getNode(worldName);
        if (worldSettings != null) {
            Object vp = worldSettings.getProperty(Config.votePerc);
            if (vp instanceof Double) {
                return (Double) vp;
            }
        }
        return config.getDouble("*." + Config.votePerc, 100.0d);
    }

}
