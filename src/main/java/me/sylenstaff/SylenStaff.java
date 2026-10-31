
package me.sylenstaff;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class SylenStaff extends JavaPlugin implements Listener, CommandExecutor {

    private final Set<UUID> staff = new HashSet<>();
    private final Set<UUID> vanished = new HashSet<>();
    private final Set<UUID> frozen = new HashSet<>();
    private final Map<UUID, SavedInventory> savedInventories = new HashMap<>();

    private static class SavedInventory {
        ItemStack[] contents;
        ItemStack[] armor;
        ItemStack offhand;
    }

    private static class MenuHolder implements InventoryHolder {
        String type;
        Inventory inventory;

        MenuHolder(String type) {
            this.type = type;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();

        getCommand("staff").setExecutor(this);
        getServer().getPluginManager().registerEvents(this, this);

        getLogger().info("SylenStaff activado correctamente.");
    }

    @Override
    public void onDisable() {
        for (UUID id : new HashSet<>(staff)) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                disableStaff(player);
            }
        }

        for (UUID id : vanished) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                for (Player online : Bukkit.getOnlinePlayers()) {
                    online.showPlayer(this, player);
                }
            }
        }
    }

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args
    ) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Este comando solo puede usarlo un jugador.");
            return true;
        }

        if (!player.hasPermission("sylenstaff.use")) {
            player.sendMessage("§cNo tienes permiso para utilizar SylenStaff.");
            return true;
        }

        if (staff.contains(player.getUniqueId())) {
            disableStaff(player);
        } else {
            enableStaff(player);
        }

        return true;
    }

    private void enableStaff(Player player) {
        UUID id = player.getUniqueId();

        SavedInventory saved = new SavedInventory();
        PlayerInventory inventory = player.getInventory();

        saved.contents = inventory.getStorageContents();
        saved.armor = inventory.getArmorContents();
        saved.offhand = inventory.getItemInOffHand().clone();

        savedInventories.put(id, saved);

        inventory.clear();
        inventory.setArmorContents(new ItemStack[4]);
        inventory.setItemInOffHand(null);

        staff.add(id);

        player.sendMessage("§aModo staff activado.");
        openMainMenu(player);
    }

    private void disableStaff(Player player) {
        UUID id = player.getUniqueId();

        player.closeInventory();

        SavedInventory saved = savedInventories.remove(id);

        if (saved != null) {
            PlayerInventory inventory = player.getInventory();
            inventory.clear();
            inventory.setStorageContents(saved.contents);
            inventory.setArmorContents(saved.armor);
            inventory.setItemInOffHand(saved.offhand);
        }

        staff.remove(id);
        frozen.remove(id);

        if (vanished.remove(id)) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                online.showPlayer(this, player);
            }
        }

        player.sendMessage("§cModo staff desactivado.");
    }

    private void openMainMenu(Player player) {
        MenuHolder holder = new MenuHolder("main");
        Inventory menu = Bukkit.createInventory(
                holder, 27, "§eSylenStaff §8| §7Menú"
        );
        holder.inventory = menu;

        menu.setItem(10, createItem(
                Material.ENDER_EYE, "§bVanish",
                "§7Ocultar o mostrarte."
        ));

        menu.setItem(12, createItem(
                Material.COMPASS, "§aTeletransporte aleatorio",
                "§7Ir hacia un jugador aleatorio."
        ));

        menu.setItem(14, createItem(
                Material.ICE, "§bFreezer",
                "§7Congelar o descongelar jugadores."
        ));

        menu.setItem(16, createItem(
                Material.BLAZE_ROD, "§6Inspeccionar",
                "§7Ver inventarios de jugadores."
        ));

        menu.setItem(20, createItem(
                Material.FEATHER, "§eTeletransportarse",
                "§7Elegir un jugador al que ir."
        ));

        menu.setItem(22, createItem(
                Material.CLOCK, "§fJugadores online",
                "§7Consultar jugadores conectados."
        ));

        player.openInventory(menu);
    }

    private ItemStack createItem(Material material, String name, String lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(name);
        meta.setLore(Collections.singletonList(lore));

        item.setItemMeta(meta);
        return item;
    }

    private void toggleVanish(Player player) {
        UUID id = player.getUniqueId();

        if (vanished.contains(id)) {
            vanished.remove(id);

            for (Player online : Bukkit.getOnlinePlayers()) {
                online.showPlayer(this, player);
            }

            player.sendMessage("§cVanish desactivado.");
        } else {
            vanished.add(id);

            for (Player online : Bukkit.getOnlinePlayers()) {
                if (!online.equals(player)) {
                    online.hidePlayer(this, player);
                }
            }

            player.sendMessage("§aVanish activado.");
        }
    }

    private void openPlayerList(Player player, String action) {
        MenuHolder holder = new MenuHolder(action);
        Inventory menu = Bukkit.createInventory(
                holder, 54, "§eSylenStaff §8| §7Jugadores"
        );
        holder.inventory = menu;

        int slot = 0;

        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target.equals(player)) continue;
            if (slot >= 54) break;

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();

            meta.setOwningPlayer(target);
            meta.setDisplayName("§e" + target.getName());

            head.setItemMeta(meta);
            menu.setItem(slot++, head);
        }

        if (slot == 0) {
            player.sendMessage("§cNo hay otros jugadores conectados.");
            return;
        }

        player.openInventory(menu);
    }

    private void inspectPlayer(Player viewer, Player target) {
        MenuHolder holder = new MenuHolder("inspect");
        Inventory menu = Bukkit.createInventory(
                holder, 54, "§eInventario §8| §f" + target.getName()
        );
        holder.inventory = menu;

        ItemStack[] contents = target.getInventory().getStorageContents();

        for (int i = 0; i < contents.length && i < 36; i++) {
            if (contents[i] != null) {
                menu.setItem(i, contents[i].clone());
            }
        }

        ItemStack[] armor = target.getInventory().getArmorContents();

        for (int i = 0; i < armor.length; i++) {
            if (armor[i] != null) {
                menu.setItem(36 + i, armor[i].clone());
            }
        }

        ItemStack offhand = target.getInventory().getItemInOffHand();

        if (offhand.getType() != Material.AIR) {
            menu.setItem(40, offhand.clone());
        }

        viewer.openInventory(menu);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getInventory().getHolder() instanceof MenuHolder holder)) return;

        event.setCancelled(true);

        if (!staff.contains(player.getUniqueId())) return;

        int slot = event.getRawSlot();

        if (holder.type.equals("main")) {
            switch (slot) {
                case 10 -> toggleVanish(player);

                case 12 -> {
                    List<Player> targets = new ArrayList<>(
                            Bukkit.getOnlinePlayers()
                    );
                    targets.remove(player);

                    if (!targets.isEmpty()) {
                        Player target = targets.get(
                                new Random().nextInt(targets.size())
                        );
                        player.teleport(target.getLocation());
                        player.sendMessage("§aTeletransportado hacia §e"
                                + target.getName());
                    } else {
                        player.sendMessage("§cNo hay jugadores disponibles.");
                    }
                }

                case 14 -> openPlayerList(player, "freeze");
                case 16 -> openPlayerList(player, "inspect-list");
                case 20 -> openPlayerList(player, "teleport");
                case 22 -> player.sendMessage(
                        "§eJugadores online: §f"
                                + Bukkit.getOnlinePlayers().size()
                );
            }

            return;
        }

        if (holder.type.equals("inspect")) return;

        ItemStack clicked = event.getCurrentItem();

        if (clicked == null || clicked.getType() != Material.PLAYER_HEAD) {
            return;
        }

        if (!(clicked.getItemMeta() instanceof SkullMeta meta)) return;

        OfflinePlayer owner = meta.getOwningPlayer();
        if (owner == null) return;

        Player target = Bukkit.getPlayer(owner.getUniqueId());

        if (target == null) {
            player.sendMessage("§cEse jugador ya no está conectado.");
            return;
        }

        switch (holder.type) {
            case "inspect-list" -> inspectPlayer(player, target);

            case "teleport" -> {
                player.teleport(target.getLocation());
                player.closeInventory();
                player.sendMessage("§aTeletransportado hacia §e"
                        + target.getName());
            }

            case "freeze" -> {
                UUID id = target.getUniqueId();

                if (frozen.remove(id)) {
                    target.sendMessage("§aYa puedes moverte.");
                    player.sendMessage("§e" + target.getName()
                            + " §aha sido descongelado.");
                } else {
                    frozen.add(id);
                    target.sendMessage("§cHas sido congelado por el staff.");
                    player.sendMessage("§e" + target.getName()
                            + " §cha sido congelado.");
                }
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof MenuHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!frozen.contains(event.getPlayer().getUniqueId())) return;
        if (event.getTo() == null) return;

        if (event.getFrom().getBlockX() != event.getTo().getBlockX()
                || event.getFrom().getBlockY() != event.getTo().getBlockY()
                || event.getFrom().getBlockZ() != event.getTo().getBlockZ()) {
            event.setTo(event.getFrom());
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player joining = event.getPlayer();

        for (UUID id : vanished) {
            Player hidden = Bukkit.getPlayer(id);

            if (hidden != null && !hidden.equals(joining)) {
                joining.hidePlayer(this, hidden);
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        if (staff.contains(player.getUniqueId())) {
            disableStaff(player);
        }

        frozen.remove(player.getUniqueId());
    }
}
