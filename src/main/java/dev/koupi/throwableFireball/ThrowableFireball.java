package dev.koupi.throwableFireball;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.*;
import org.bukkit.persistence.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.List;

public final class ThrowableFireball extends JavaPlugin implements Listener, TabCompleter {

    private final HashMap<UUID, Long> cooldowns = new HashMap<>();

    private NamespacedKey keyFireball;
    private NamespacedKey keyBridgeEgg;
    private NamespacedKey keyTNT;

    private int delay;
    private double speed;
    private double yield;
    private double damagePercent;
    private double knockbackStrength;
    private String msgCooldown;

    private int bridgeDelay;
    private double bridgeWidth;
    private boolean bridgeBlockEnabled;
    private boolean bridgeEntityEnabled;
    private int bridgeBlockDelay;
    private int bridgeEntityDelay;
    private List<Material> bridgeBlockTypes = new ArrayList<>();
    private List<String> bridgeEntityTypes = new ArrayList<>();

    private int tntDuration;
    private double tntDamagePercent;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfigValues();
        getServer().getPluginManager().registerEvents(this, this);

        keyFireball = new NamespacedKey(this, "special_fireball");
        keyBridgeEgg = new NamespacedKey(this, "special_bridgeegg");
        keyTNT = new NamespacedKey(this, "special_tnt");

        Objects.requireNonNull(getCommand("utilz")).setTabCompleter(this);
        getLogger().info("✅ ThrowableFireball v3.0 Loaded");
    }

    private void loadConfigValues() {
        delay = getConfig().getInt("cooldown", 1);
        speed = getConfig().getDouble("fireball-speed", 2.0);
        yield = getConfig().getDouble("fireball-yield", 0.8);
        damagePercent = getConfig().getDouble("fireball-damage-percent", 50);
        knockbackStrength = getConfig().getDouble("fireball-knockback", 1.5);
        msgCooldown = getConfig().getString("messages.cooldown", "クールダウン中です");

        bridgeDelay = getConfig().getInt("bridge-delay", 10);
        bridgeWidth = getConfig().getDouble("bridge-width", 2.0);
        bridgeBlockEnabled = getConfig().getBoolean("bridge-block", true);
        bridgeEntityEnabled = getConfig().getBoolean("bridge-entity", false);
        bridgeBlockDelay = getConfig().getInt("bridge-block-delay", 1);
        bridgeEntityDelay = getConfig().getInt("bridge-entity-delay", 1);

        bridgeBlockTypes.clear();
        for (String matName : getConfig().getStringList("bridge-block-types")) {
            try { bridgeBlockTypes.add(Material.valueOf(matName.toUpperCase())); }
            catch (Exception ignored) {}
        }

        bridgeEntityTypes.clear();
        bridgeEntityTypes.addAll(getConfig().getStringList("bridge-entity-types"));

        tntDuration = getConfig().getInt("tnt-duration", 1);
        tntDamagePercent = getConfig().getDouble("tnt-damage-percent", 50);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§7/utilz give <fireball|bridgeegg|tnt>");
            sender.sendMessage("§7/utilz reload");
            return true;
        }
        if (args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            loadConfigValues();
            sender.sendMessage("§a[Utilz] Config reloaded!");
            return true;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cOnly players can use this command.");
                return true;
            }
            giveSpecialItem(player, args[1]);
            return true;
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> list = new ArrayList<>();
        if (args.length == 1) { list.add("give"); list.add("reload"); }
        else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            list.add("fireball"); list.add("bridgeegg"); list.add("tnt");
        }
        return list;
    }

    private void giveSpecialItem(Player player, String type) {
        ItemStack item;
        ItemMeta meta;
        switch (type.toLowerCase()) {
            case "fireball" -> {
                item = new ItemStack(Material.FIRE_CHARGE);
                meta = item.getItemMeta();
                meta.setDisplayName("§cThrowable Fireball");
                meta.getPersistentDataContainer().set(keyFireball, PersistentDataType.BYTE, (byte) 1);
            }
            case "bridgeegg" -> {
                item = new ItemStack(Material.EGG);
                meta = item.getItemMeta();
                meta.setDisplayName("§aBridge Egg");
                meta.getPersistentDataContainer().set(keyBridgeEgg, PersistentDataType.BYTE, (byte) 1);
            }
            case "tnt" -> {
                item = new ItemStack(Material.TNT);
                meta = item.getItemMeta();
                meta.setDisplayName("§4Auto TNT");
                meta.getPersistentDataContainer().set(keyTNT, PersistentDataType.BYTE, (byte) 1);
            }
            default -> { player.sendMessage("§cInvalid type."); return; }
        }
        item.setItemMeta(meta);
        player.getInventory().addItem(item);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        ItemStack i = p.getInventory().getItemInMainHand();
        if (i.getType() == Material.FIRE_CHARGE && isSpecial(i, keyFireball)) {
            if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;

            long now = System.currentTimeMillis();
            if (cooldowns.containsKey(p.getUniqueId()) && now - cooldowns.get(p.getUniqueId()) < delay * 1000) {
                p.sendMessage(msgCooldown);
                e.setCancelled(true);
                return;
            }

            Fireball fb = p.launchProjectile(Fireball.class);
            fb.setYield((float) yield);
            fb.setIsIncendiary(false);
            fb.setVelocity(p.getLocation().getDirection().multiply(speed));
            cooldowns.put(p.getUniqueId(), now);
            i.setAmount(i.getAmount() - 1);
            p.getInventory().setItemInMainHand(i);
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onFireballDamage(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Fireball fb) {
            double original = e.getDamage();
            e.setDamage(original * (damagePercent / 100.0));
            if (e.getEntity() instanceof Player p) {
                Vector dir = p.getLocation().toVector().subtract(fb.getLocation().toVector()).normalize();
                p.setVelocity(dir.multiply(knockbackStrength).setY(knockbackStrength/2));
            }
        }
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        ItemStack item = e.getItemInHand();
        Block block = e.getBlockPlaced();

        if (item.getType() == Material.TNT && isSpecial(item, keyTNT)) {
            e.setCancelled(true);
            block.setType(Material.AIR);

            TNTPrimed tnt = block.getWorld().spawn(block.getLocation().add(0.5, 0, 0.5), TNTPrimed.class);
            tnt.setFuseTicks(tntDuration * 20);
            item.setAmount(item.getAmount() - 1);
            p.getInventory().setItemInMainHand(item);
            p.playSound(p.getLocation(), Sound.ENTITY_TNT_PRIMED, 1f, 1f);
        }
    }

    @EventHandler
    public void onBridgeEgg(ProjectileLaunchEvent e) {
        if (!(e.getEntity() instanceof Egg egg)) return;
        if (!(egg.getShooter() instanceof Player p)) return;
        if (!isSpecial(p.getInventory().getItemInMainHand(), keyBridgeEgg)) return;

        new BukkitRunnable() {
            final List<Location> past = new ArrayList<>();
            int tick = 0;
            int blockIndex = 0;
            int entityIndex = 0;
            @Override
            public void run() {
                if (!egg.isValid() || egg.isDead()) { cancel(); return; }
                past.add(egg.getLocation().clone());
                if (tick > bridgeDelay && past.size() > bridgeDelay) {
                    Location loc = past.get(past.size() - bridgeDelay);

                    // ブロック生成
                    if (bridgeBlockEnabled && tick % bridgeBlockDelay == 0 && !bridgeBlockTypes.isEmpty()) {
                        double halfWidth = bridgeWidth/2.0;
                        Vector right = loc.getDirection().crossProduct(new Vector(0,1,0)).normalize();
                        for (double offset=-halfWidth; offset<=halfWidth; offset+=0.5) {
                            Location bLoc = loc.clone().add(right.clone().multiply(offset));
                            Block b = bLoc.getBlock();
                            Material mat = bridgeBlockTypes.get(blockIndex++ % bridgeBlockTypes.size());
                            if (b.getType().isAir()) b.setType(mat);
                        }
                    }

                    // エンティティ生成
                    if (bridgeEntityEnabled && tick % bridgeEntityDelay == 0 && !bridgeEntityTypes.isEmpty()) {
                        double halfWidth = bridgeWidth/2.0;
                        Vector right = loc.getDirection().crossProduct(new Vector(0,1,0)).normalize();
                        for (double offset=-halfWidth; offset<=halfWidth; offset+=0.5) {
                            Location entLoc = loc.clone().add(right.clone().multiply(offset)).add(0,1,0);
                            String entry = bridgeEntityTypes.get(entityIndex++ % bridgeEntityTypes.size());
                            spawnCustomEntity(entLoc, entry);
                        }
                    }
                }
                tick++;
            }
        }.runTaskTimer(this,0L,1L);
    }

    private void spawnCustomEntity(Location loc, String entry) {
        try {
            String typeName = entry.split("\\[")[0];
            Map<String,String> options = new HashMap<>();
            if (entry.contains("[")) {
                String inside = entry.substring(entry.indexOf("[")+1, entry.lastIndexOf("]"));
                for (String kv : inside.split(";")) {
                    if (kv.contains("=")) {
                        String[] pair = kv.split("=",2);
                        options.put(pair[0].toLowerCase(), pair[1]);
                    }
                }
            }

            Entity e;
            if ("FallingBlock".equalsIgnoreCase(typeName)) {
                Material m = Material.valueOf(options.getOrDefault("block","STONE").toUpperCase());
                e = loc.getWorld().spawnFallingBlock(loc, m.createBlockData());
            } else if ("Item".equalsIgnoreCase(typeName)) {
                Material m = Material.valueOf(options.getOrDefault("material","STONE").toUpperCase());
                e = loc.getWorld().dropItem(loc, new ItemStack(m));
            } else if ("TNT".equalsIgnoreCase(typeName)) {
                TNTPrimed tnt = loc.getWorld().spawn(loc, TNTPrimed.class);
                if (options.containsKey("delay")) {
                    try { tnt.setFuseTicks(Integer.parseInt(options.get("delay"))*20); } catch (NumberFormatException ignored) {}
                }
                e = tnt;
            } else if ("Firework".equalsIgnoreCase(typeName)) {
                Firework fw = loc.getWorld().spawn(loc, Firework.class);
                FireworkMeta meta = fw.getFireworkMeta();
                if (options.containsKey("color")) {
                    try {
                        String hex = options.get("color").replace("#","");
                        int rgb = Integer.parseInt(hex,16);
                        org.bukkit.Color c = org.bukkit.Color.fromRGB((rgb>>16)&0xFF,(rgb>>8)&0xFF,rgb&0xFF);
                        meta.addEffect(FireworkEffect.builder().withColor(c).withFade(c).build());
                    } catch (Exception ignored) {}
                }
                if (options.containsKey("power")) {
                    try { meta.setPower(Integer.parseInt(options.get("power"))); } catch (NumberFormatException ignored) {}
                }
                fw.setFireworkMeta(meta);
                e = fw;
            } else {
                EntityType type = EntityType.valueOf(typeName.toUpperCase());
                if (!type.isSpawnable()) return;
                e = loc.getWorld().spawnEntity(loc, type);
            }

            if (options.containsKey("motion")) {
                String[] v = options.get("motion").replace("[","").replace("]","").split(",");
                if (v.length==3) e.setVelocity(new Vector(Double.parseDouble(v[0]),Double.parseDouble(v[1]),Double.parseDouble(v[2])));
            }
        } catch (Exception ex) {
            getLogger().warning("Invalid entity format: "+entry);
        }
    }

    private boolean isSpecial(ItemStack item, NamespacedKey key) {
        if (item==null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }
}
