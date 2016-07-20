package me.Hiztree.PancakePort;


import com.google.common.collect.Maps;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Logger;

public class PancakePort extends JavaPlugin {

    private Logger logger = Logger.getLogger("PancakePort");

    private File configFile;
    private FileConfiguration config;

    private String prefix;
    private int radius, commandCooldown, timer;
    private int maxX, maxZ;
    private List<String> locations;
    private HashMap<String, Long> cooldowns = Maps.newHashMap();

    int task;

    //Loads up everything.
    public void onEnable() {
        File directory = new File("plugins/PancakePort");
        configFile = new File(directory, "config.yml");

        if (!directory.exists())
            directory.mkdirs();


        if (!configFile.exists()) {
            try {
                configFile.createNewFile();
                saveResource("config.yml", true);
            } catch (IOException e) {
                logger.severe("Could not create config.yml!");
            }
        }

        config = YamlConfiguration.loadConfiguration(configFile);

        prefix = config.getString("Prefix", "&8[&bPancakePort&8] ");

        radius = config.getInt("Radius", 30);
        commandCooldown = config.getInt("CommandCooldown", 60);
        timer = config.getInt("Timer", 3);

        maxX = config.getInt("Max.X", 5000);
        maxZ = config.getInt("Max.Z", 5000);

        locations = config.getStringList("Locations");

        getCommand("rp").setExecutor(this);
        getCommand("randomport").setExecutor(this);
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (label.equalsIgnoreCase("rp") || label.equalsIgnoreCase("randomport")) {
            if (args.length < 1) {
                if (sender instanceof Player) {
                    randomTeleport((Player) sender, false);
                } else {
                    logger.severe("You must be a player to perform this command!");
                }
            } else if (args.length == 1) {
                if (sender.hasPermission("pancakeport.others")) {
                    Player target = Bukkit.getPlayer(args[0]);

                    if (target != null) {
                        randomTeleport(target, sender.hasPermission("pancakeport.others.notimer"));
                        sendMessage(sender, config.getString("Messages.SenderForceTeleport").replace("{0}", args[0]));
                    } else {
                        sendMessage(sender, config.getString("Messages.PlayerOffline"));
                    }
                } else {
                    sendMessage(sender, config.getString("Messages.NoPermission"));
                }
            } else {
                sendMessage(sender, config.getString("Messages.UsageError").replace("{0}", "/randomport <name>"));
            }
        }

        return true;
    }

    private void sendMessage(CommandSender sender, String msg) {
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', prefix + msg));
    }

    private void randomTeleport(Player player, boolean ignoreCooldown) {
        try {
            if (!ignoreCooldown && cooldowns.containsKey(player.getName()) && !player.hasPermission("pancakeport.nocooldown")) {
                long oldTime = cooldowns.get(player.getName());
                long timeDiff = oldTime - System.currentTimeMillis();

                if (timeDiff > 0 && (commandCooldown * 1000) > timeDiff) {
                    sendMessage(player, config.getString("Messages.CooldownError").replace("{0}", String.valueOf(timeDiff / 1000)));
                    return;
                } else {
                    cooldowns.remove(player.getName());
                }
            }

            Location loc = findRandomLocation(player.getWorld());

            int counter = 0;

            if (!locationSafe(loc) && !locationOutsideRadius(loc)) {
                loc = findRandomLocation(player.getWorld());
                counter++;

                if (counter > 3) {
                    sendMessage(player, config.getString("Messages.TryAgain"));
                    return;
                }
            }

            if (!player.hasPermission("pancakeport.notimer")) {
                Location finalLoc = loc;

                task = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
                    int countdownTimer = timer;

                    public void run() {
                        if (countdownTimer <= 0) {
                            doTeleport(player, finalLoc);
                            Bukkit.getScheduler().cancelTask(task);
                            return;
                        }

                        sendMessage(player, config.getString("Messages.Timer").replace("{0}", String.valueOf(countdownTimer)));

                        countdownTimer--;
                    }
                }, 0L, 20L);
            } else {
                doTeleport(player, loc);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void doTeleport(Player player, Location location) {
        player.teleport(location);
        sendMessage(player, config.getString("Messages.Teleport"));

        if (!player.hasPermission("pancakeport.nocooldown")) {
            cooldowns.put(player.getName(), System.currentTimeMillis() + (1000 * commandCooldown));
        }

        locations.add(location.getWorld().getName() + " " + location.getBlockX() + " " + location.getBlockY() + " " + location.getBlockZ());
        config.set("Locations", locations);

        try {
            config.save(configFile);
        } catch (IOException e) {
            logger.severe("Could not save the config.yml!");
        }
    }

    private Location findRandomLocation(World world) throws Exception {
        int x = (int) (Math.random() * maxX);
        int z = (int) (Math.random() * maxZ);
        int y = world.getHighestBlockYAt(x, z);

        return new Location(world, x, y, z);
    }

    private boolean locationSafe(Location location) throws Exception {
        World world = location.getWorld();
        int x = location.getBlockX();
        int y = location.getBlockZ();
        int z = location.getBlockZ();

        Location topBlock = new Location(world, x, (y + 1), z);
        Location bottomBlock = new Location(world, x, (y - 1), z);

        if (location.getBlock().isEmpty()) {
            if (topBlock.getBlock().isEmpty()) {
                if (!bottomBlock.getBlock().isLiquid()) {
                    if (!bottomBlock.getBlock().isEmpty()) {
                        if (!bottomBlock.getBlock().getType().equals(Material.BEDROCK)) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    private boolean locationOutsideRadius(Location location) throws Exception {
        for (String loc : locations) {
            String[] split = loc.split(" ");

            World world = Bukkit.getServer().getWorld(split[0]);
            int x = Integer.parseInt(split[1]);
            int y = Integer.parseInt(split[2]);
            int z = Integer.parseInt(split[3]);

            if (world.equals(location.getWorld())) {
                if (new Location(world, x, y, z).distance(location) <= radius) {
                    return false;
                }
            }
        }

        return true;
    }
}
