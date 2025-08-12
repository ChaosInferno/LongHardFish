package org.aincraft.listener;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Chest;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import static net.kyori.adventure.key.Key.*;
import static net.kyori.adventure.text.Component.*;

public class PirateChestListener implements Listener, CommandExecutor {

    @Override
    public boolean onCommand(
            @org.jetbrains.annotations.NotNull CommandSender sender,
            @org.jetbrains.annotations.NotNull Command command,
            @org.jetbrains.annotations.NotNull String label,
            @org.jetbrains.annotations.NotNull String[] args
    ) {
        if (sender instanceof Player player) {
            Inventory gui = Bukkit.createInventory(
                    null,
                    54,
                    text("\ue003\ue001\ue001\ua001").font(key("longhardfish", "interface")).color(TextColor.color(0xFFFFFF))
            );
            player.openInventory(gui);
        }
        return false;
    }
}