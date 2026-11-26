package net.coconut.emotecreator;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class EmoteCreatorPlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final Map<String, Emote> emotes = new LinkedHashMap<>();
    private final Map<UUID, Long> lastUse = new ConcurrentHashMap<>();
    private final Map<UUID, String> awaitingInput = new HashMap<>();
    private NamespacedKey actionKey;
    private File emotesFile;
    private YamlConfiguration emoteConfig;

    @Override public void onEnable() {
        saveDefaultConfig();
        saveResource("messages.yml", false);
        saveResource("emotes.yml", false);
        actionKey = new NamespacedKey(this, "menu_action");
        emotesFile = new File(getDataFolder(), "emotes.yml");
        loadEmotes();
        getServer().getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("emotes")).setExecutor(this);
        Objects.requireNonNull(getCommand("emotes")).setTabCompleter(this);
        Objects.requireNonNull(getCommand("emoteadmin")).setExecutor(this);
        getLogger().info("EmoteCreator enabled. Coconut theme is configurable in config.yml.");
    }
    @Override public void onDisable() { awaitingInput.clear(); lastUse.clear(); }

    private Component msg(String key) {
        String raw = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "messages.yml")).getString(key, "<red>Missing message: "+key);
        return mm.deserialize(raw);
    }
    private Component parse(String s) { return mm.deserialize(s == null ? "" : s); }
    private String plain(Component c) { return PlainTextComponentSerializer.plainText().serialize(c); }
    private void send(Player p, String key) { p.sendMessage(parse(getConfig().getString("prefix", "")).append(msg(key))); }

    private void loadEmotes() {
        emotes.clear();
        emoteConfig = YamlConfiguration.loadConfiguration(emotesFile);
        ConfigurationSection section = emoteConfig.getConfigurationSection("emotes");
        if (section == null) return;
        for (String id : section.getKeys(false)) {
            ConfigurationSection s = section.getConfigurationSection(id);
            if (s == null) continue;
            Material icon = Material.matchMaterial(s.getString("icon", "PAPER"));
            if (icon == null || !icon.isItem()) icon = Material.PAPER;
            emotes.put(id.toLowerCase(Locale.ROOT), new Emote(id, s.getString("name", id), s.getString("description", ""),
                s.getString("category", "General"), icon, s.getString("rarity", "COMMON"),
                s.getString("permission", ""), s.getString("effect", "HEART")));
        }
    }
    private void saveEmotes() {
        try { emoteConfig.save(emotesFile); }
        catch (java.io.IOException ex) { getLogger().severe("Could not save emotes.yml: " + ex.getMessage()); }
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Players only."); return true; }
        if (command.getName().equalsIgnoreCase("emoteadmin")) {
            if (!p.hasPermission("emotecreator.admin")) { send(p, "no-permission"); return true; }
            openAdmin(p); return true;
        }
        if (args.length > 0) {
            if (args[0].equalsIgnoreCase("stop")) { p.clearTitle(); p.sendMessage(parse("<gray>Your emote was stopped.")); return true; }
            if (args[0].equalsIgnoreCase("create")) {
                if (!p.hasPermission("emotecreator.create")) { send(p, "no-permission"); return true; }
                awaitingInput.put(p.getUniqueId(), "create");
                p.closeInventory();
                p.sendMessage(parse("<gold>Enter an emote ID in chat (letters, numbers, underscores). Type cancel to stop."));
                return true;
            }
            playEmote(p, args[0]); return true;
        }
        openEmotes(p, 0); return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && command.getName().equalsIgnoreCase("emotes"))
            return emotes.keySet().stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        return List.of();
    }

    private void openEmotes(Player p, int page) {
        int rows = Math.max(3, Math.min(6, getConfig().getInt("gui.rows", 6)));
        Inventory inv = Bukkit.createInventory(new MenuHolder("emotes", page), rows * 9, parse(getConfig().getString("gui.title", "<gold>COCONUT EMOTES")));
        fill(inv);
        List<Emote> available = emotes.values().stream().filter(e -> e.permission.isBlank() || p.hasPermission(e.permission)).toList();
        int start = page * 28, end = Math.min(available.size(), start + 28);
        int[] slots = contentSlots();
        for (int i = start; i < end; i++) {
            Emote e = available.get(i);
            ItemStack item = item(e.icon, e.name, List.of(e.description, "<dark_gray>Category: <gray>"+e.category,
                rarityColour(e.rarity)+"Rarity: "+e.rarity, "<yellow>Click to use"), "play:"+e.id);
            inv.setItem(slots[i-start], item);
        }
        inv.setItem(inv.getSize()-5, item(Material.NETHER_STAR, "<gold><bold>COCONUT EMOTES", List.of("<gray>Choose your island vibe"), "none"));
        if (page > 0) inv.setItem(inv.getSize()-9, item(Material.ARROW, "<yellow>Previous Page", List.of("<gray>Page "+page), "page:"+(page-1)));
        if (end < available.size()) inv.setItem(inv.getSize()-1, item(Material.ARROW, "<yellow>Next Page", List.of("<gray>Page "+(page+2)), "page:"+(page+1)));
        inv.setItem(inv.getSize()-3, item(Material.BARRIER, "<red>Close", List.of("<gray>Close this menu"), "close"));
        p.openInventory(inv);
    }
    private void openAdmin(Player p) {
        Inventory inv = Bukkit.createInventory(new MenuHolder("admin", 0), 54, parse("<gradient:#f6c779:#fff0c2><bold>COCONUT • EMOTE STUDIO"));
        fill(inv);
        inv.setItem(20, item(Material.WRITABLE_BOOK, "<gold><bold>Create Emote", List.of("<gray>Start creating a custom emote", "<yellow>Click to begin"), "create"));
        inv.setItem(22, item(Material.BOOK, "<aqua><bold>Manage Emotes", List.of("<gray>Click an emote to delete it"), "manage"));
        inv.setItem(24, item(Material.COMPARATOR, "<green><bold>Reload", List.of("<gray>Reload emotes.yml and config.yml"), "reload"));
        inv.setItem(49, item(Material.BARRIER, "<red>Close", List.of(), "close"));
        p.openInventory(inv);
    }
    private void openManage(Player p) {
        Inventory inv = Bukkit.createInventory(new MenuHolder("manage", 0), 54, parse("<gold><bold>EMOTE MANAGEMENT"));
        fill(inv); int slot = 10;
        for (Emote e : emotes.values()) {
            if (slot >= 44) break;
            inv.setItem(slot++, item(e.icon, e.name, List.of("<gray>"+e.description, "<red>Click to delete", "<dark_gray>ID: "+e.id), "delete:"+e.id));
            if (slot % 9 == 8) slot += 2;
        }
        inv.setItem(49, item(Material.ARROW, "<yellow>Back", List.of(), "admin"));
        p.openInventory(inv);
    }
    private void fill(Inventory inv) {
        Material mat = Material.matchMaterial(getConfig().getString("gui.filler", "BLACK_STAINED_GLASS_PANE"));
        if (mat == null || !mat.isItem()) mat = Material.BLACK_STAINED_GLASS_PANE;
        ItemStack pane = item(mat, "<dark_gray> ", List.of(), "none");
        for (int i=0;i<inv.getSize();i++) inv.setItem(i,pane);
    }
    private int[] contentSlots() {
        return new int[]{10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43};
    }
    private ItemStack item(Material material, String name, List<String> lore, String action) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(parse(name));
        meta.lore(lore.stream().map(this::parse).toList());
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        stack.setItemMeta(meta);
        return stack;
    }
    private String rarityColour(String r) {
        return switch (r.toUpperCase(Locale.ROOT)) { case "LEGENDARY" -> "<gold>"; case "EPIC" -> "<light_purple>"; case "RARE" -> "<aqua>"; default -> "<gray>"; };
    }

    @EventHandler public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player p) || !(event.getView().getTopInventory().getHolder() instanceof MenuHolder holder)) return;
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) return;
        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        String action = clicked.getItemMeta().getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        if (action == null || action.equals("none")) return;
        if (action.equals("close")) { p.closeInventory(); return; }
        if (action.startsWith("play:")) { playEmote(p, action.substring(5)); return; }
        if (action.startsWith("page:")) { openEmotes(p, Integer.parseInt(action.substring(5))); return; }
        switch (action) {
            case "admin" -> openAdmin(p);
            case "create" -> beginCreate(p);
            case "manage" -> openManage(p);
            case "reload" -> { reloadConfig(); loadEmotes(); p.sendMessage(parse("<green>EmoteCreator reloaded.")); openAdmin(p); }
            default -> { if (action.startsWith("delete:")) {
                String id = action.substring(7); emotes.remove(id); emoteConfig.set("emotes."+id, null); saveEmotes();
                p.sendMessage(parse("<green>Deleted emote <white>"+id)); openManage(p);
            }}
        }
    }
    @EventHandler public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof MenuHolder) event.setCancelled(true);
    }
    private void beginCreate(Player p) {
        p.closeInventory(); awaitingInput.put(p.getUniqueId(), "create");
        p.sendMessage(parse("<gold><bold>EMOTE STUDIO</bold> <gray>Enter a new emote ID in chat."));
        p.sendMessage(parse("<gray>Use lowercase letters, numbers and underscores. Type <red>cancel <gray>to stop."));
    }
    @EventHandler public void onChat(org.bukkit.event.player.AsyncPlayerChatEvent event) {
        Player p = event.getPlayer();
        String mode = awaitingInput.get(p.getUniqueId());
        if (mode == null) return;
        event.setCancelled(true);
        String input = event.getMessage().trim().toLowerCase(Locale.ROOT);
        Bukkit.getScheduler().runTask(this, () -> {
            if (input.equals("cancel")) { awaitingInput.remove(p.getUniqueId()); p.sendMessage(parse("<yellow>Creation cancelled.")); return; }
            if (!input.matches("[a-z0-9_]{3,32}") || emotes.containsKey(input)) {
                p.sendMessage(parse("<red>Invalid or already-used ID. Try again, or type cancel.")); return;
            }
            awaitingInput.remove(p.getUniqueId());
            Emote e = new Emote(input, "<gold><bold>"+input.replace('_',' '), "<gray>A custom Coconut emote.", "Custom", Material.COCOA_BEANS, "COMMON", "", "HEART");
            emotes.put(input,e);
            emoteConfig.set("emotes."+input+".name", e.name);
            emoteConfig.set("emotes."+input+".description", e.description);
            emoteConfig.set("emotes."+input+".category", e.category);
            emoteConfig.set("emotes."+input+".icon", e.icon.name());
            emoteConfig.set("emotes."+input+".rarity", e.rarity);
            emoteConfig.set("emotes."+input+".permission", e.permission);
            emoteConfig.set("emotes."+input+".effect", e.effect);
            saveEmotes();
            p.sendMessage(parse("<green>Created <gold>"+input+"<green>. Edit its details in emotes.yml."));
            openAdmin(p);
        });
    }
    private void playEmote(Player p, String id) {
        Emote e = emotes.get(id.toLowerCase(Locale.ROOT));
        if (e == null) { send(p, "unknown-emote"); return; }
        if (!p.hasPermission("emotecreator.use") || (!e.permission.isBlank() && !p.hasPermission(e.permission))) { send(p, "no-permission"); return; }
        long now = System.currentTimeMillis(), cd = getConfig().getLong("cooldown-seconds",3)*1000L;
        if (!p.hasPermission("emotecreator.bypasscooldown") && now-lastUse.getOrDefault(p.getUniqueId(),0L)<cd) {
            p.sendMessage(parse("<yellow>Please wait "+Math.max(1,(cd-(now-lastUse.get(p.getUniqueId())))/1000)+" seconds before using another emote.")); return;
        }
        lastUse.put(p.getUniqueId(),now);
        p.sendMessage(parse("<gray>You used ").append(parse(e.name)).append(parse("<gray>!")));
        Location base = p.getLocation().add(0,1,0);
        Particle particle = switch(e.effect.toUpperCase(Locale.ROOT)) {
            case "TOTEM" -> Particle.TOTEM_OF_UNDYING; case "SWEEP" -> Particle.SWEEP_ATTACK;
            case "HAPPY" -> Particle.HAPPY_VILLAGER; case "NOTE" -> Particle.NOTE;
            case "FIREWORK" -> Particle.FIREWORK; case "HEART" -> Particle.HEART;
            default -> Particle.HEART;
        };
        int duration = Math.max(10,getConfig().getInt("animation.duration-ticks",50));
        int count = Math.max(1,getConfig().getInt("animation.particle-count",12));
        new BukkitRunnable() {
            int ticks=0;
            @Override public void run() {
                if (!p.isOnline() || ticks++ >= duration) { cancel(); return; }
                Location at = p.getLocation().add(0,1.0,0);
                p.getWorld().spawnParticle(particle,at,count/3+1,0.45,0.65,0.45,0.02);
                if (ticks % 10 == 0) p.getWorld().playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME,0.45f,1.2f);
            }
        }.runTaskTimer(this,0L,2L);
    }
    private record Emote(String id, String name, String description, String category, Material icon, String rarity, String permission, String effect) {}
    private static final class MenuHolder implements InventoryHolder {
        private final String type; private final int page;
        private MenuHolder(String type,int page){this.type=type;this.page=page;}
        @Override public Inventory getInventory(){return null;}
    }
}
