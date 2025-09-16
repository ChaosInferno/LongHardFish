// org/aincraft/processor/FishProcessorUI.java
package org.aincraft.processor;

import org.aincraft.items.FishKeys;
import org.aincraft.items.Keys;
import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public final class FishProcessorUI implements Listener {

    private static final int SIZE = 54;
    private static final String TITLE = ChatColor.DARK_AQUA + "Fish Processor";

    private static final int[] INPUT_SLOTS = {
            slot(0,0), slot(1,0), slot(2,0),
            slot(0,1), slot(1,1), slot(2,1),
            slot(0,2), slot(1,2), slot(2,2)
    };

    private static final int[] OUTPUT_SLOTS = buildOutputSlots();

    private static final int KNIFE_SLOT = slot(4,2);
    private static final int PROCESS_SLOT = slot(4,3);

    private final JavaPlugin plugin;
    private final FishMaterialProvider provider;

    public FishProcessorUI(JavaPlugin plugin, FishMaterialProvider provider) {
        this.plugin = plugin;
        this.provider = provider;
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(new Holder(), SIZE, TITLE);
        paint(inv);
        player.openInventory(inv);
    }

    /* ---------------- layout helpers ---------------- */

    private static int slot(int col, int row) { return row * 9 + col; }

    private static int[] buildOutputSlots() {
        List<Integer> out = new ArrayList<>();
        for (int row = 0; row < 6; row++) {
            for (int col = 6; col <= 8; col++) out.add(slot(col, row));
        }
        return out.stream().mapToInt(i -> i).toArray();
    }

    private void paint(Inventory inv) {
        ItemStack filler = pane(DyeColor.GRAY);
        for (int i = 0; i < SIZE; i++) inv.setItem(i, filler);

        for (int s : INPUT_SLOTS) inv.setItem(s, null);
        for (int s : OUTPUT_SLOTS) inv.setItem(s, null);
        inv.setItem(KNIFE_SLOT, null);

        inv.setItem(PROCESS_SLOT, processButton());
        inv.setItem(slot(4,1), nameItem(new ItemStack(Material.IRON_SWORD), ChatColor.YELLOW + "Knife Slot"));
    }

    private static ItemStack processButton() {
        ItemStack b = new ItemStack(Material.EMERALD);
        ItemMeta m = b.getItemMeta();
        m.setDisplayName(ChatColor.GREEN + "Process");
        m.setLore(List.of(ChatColor.GRAY + "Convert fish → fillets", ChatColor.DARK_GRAY + "Consumes knife durability"));
        b.setItemMeta(m);
        return b;
    }

    private static ItemStack pane(DyeColor color) {
        ItemStack it = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(" ");
        it.setItemMeta(m);
        return it;
    }

    private static ItemStack nameItem(ItemStack base, String name) {
        ItemMeta m = base.getItemMeta();
        m.setDisplayName(name);
        base.setItemMeta(m);
        return base;
    }

    /* ---------------- event wiring ---------------- */

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder)) return;
        if (e.getClickedInventory() == null) return;

        final int raw = e.getRawSlot();
        final boolean top = raw < SIZE;

        if (top) {
            if (raw == PROCESS_SLOT && e.getClick() == ClickType.LEFT) {
                e.setCancelled(true);
                if (e.getWhoClicked() instanceof Player p) processAll(p, e.getView().getTopInventory());
                return;
            }

            if (isOutputSlot(raw)) {
                switch (e.getAction()) {
                    case PICKUP_ALL:
                    case PICKUP_HALF:
                    case PICKUP_ONE:
                    case PICKUP_SOME:
                    case SWAP_WITH_CURSOR:
                    case HOTBAR_SWAP:
                    case HOTBAR_MOVE_AND_READD:
                        return;
                    case MOVE_TO_OTHER_INVENTORY: {
                        e.setCancelled(true);
                        if (!(e.getWhoClicked() instanceof Player p)) return;
                        ItemStack clicked = e.getCurrentItem();
                        if (clicked == null || clicked.getType().isAir()) return;
                        ItemStack give = clicked.clone();
                        e.setCurrentItem(null);
                        var overflow = p.getInventory().addItem(give);
                        overflow.values().forEach(it -> p.getWorld().dropItemNaturally(p.getLocation(), it));
                        p.updateInventory();
                        return;
                    }
                    default:
                        e.setCancelled(true);
                        return;
                }
            }

            if (isInputSlot(raw) || raw == KNIFE_SLOT) {
                if (e.getAction() == InventoryAction.COLLECT_TO_CURSOR) e.setCancelled(true);
                return;
            }

            e.setCancelled(true);
            return;
        }

        if (e.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            ItemStack moving = e.getCurrentItem();
            if (moving != null && !moving.getType().isAir() && isFishItem(moving)) {
                e.setCancelled(true);
                if (moveIntoInputs(e.getView().getTopInventory(), moving)) {
                    e.setCurrentItem(moving.getAmount() <= 0 ? null : moving);
                    ((Player)e.getWhoClicked()).updateInventory();
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder)) return;
        for (int raw : e.getRawSlots()) {
            if (raw < SIZE) {
                boolean allowed = isInputSlot(raw) || raw == KNIFE_SLOT || raw == PROCESS_SLOT;
                if (!allowed) { e.setCancelled(true); return; }
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder)) return;
        HumanEntity he = e.getPlayer();
        Inventory inv = e.getInventory();

        List<ItemStack> giveBack = new ArrayList<>();
        for (int s : INPUT_SLOTS) {
            ItemStack it = inv.getItem(s);
            if (it != null && it.getType() != Material.AIR) { giveBack.add(it); inv.setItem(s, null); }
        }
        ItemStack knife = inv.getItem(KNIFE_SLOT);
        if (knife != null && knife.getType() != Material.AIR) { giveBack.add(knife); inv.setItem(KNIFE_SLOT, null); }

        if (!giveBack.isEmpty() && he instanceof Player p) {
            for (ItemStack g : giveBack) {
                HashMap<Integer, ItemStack> overflow = p.getInventory().addItem(g);
                overflow.values().forEach(it -> p.getWorld().dropItemNaturally(p.getLocation(), it));
            }
        }
    }

    private boolean isFishItem(ItemStack it) {
        if (it == null || it.getType().isAir()) return false;
        String key = FishKeys.getFishKey(plugin, it);
        return key != null && !key.isBlank();
    }

    private boolean moveIntoInputs(Inventory top, ItemStack moving) {
        if (moving == null || moving.getType().isAir()) return false;
        int toMove = moving.getAmount();

        for (int s : INPUT_SLOTS) {
            if (toMove <= 0) break;
            ItemStack cur = top.getItem(s);
            if (cur == null || cur.getType().isAir()) continue;
            if (!canStack(cur, moving)) continue;
            int max = Math.min(cur.getMaxStackSize(), 64);
            int add = Math.min(toMove, max - cur.getAmount());
            if (add > 0) { cur.setAmount(cur.getAmount() + add); toMove -= add; }
        }

        for (int s : INPUT_SLOTS) {
            if (toMove <= 0) break;
            ItemStack cur = top.getItem(s);
            if (cur != null && !cur.getType().isAir()) continue;
            ItemStack place = moving.clone();
            int placeAmt = Math.min(place.getMaxStackSize(), Math.min(64, toMove));
            place.setAmount(placeAmt);
            top.setItem(s, place);
            toMove -= placeAmt;
        }

        moving.setAmount(toMove);
        return true;
    }

    /* ---------------- processing core ---------------- */

    private void processAll(Player player, Inventory inv) {
        int totalFish = 0;
        for (int s : INPUT_SLOTS) {
            ItemStack it = inv.getItem(s);
            if (it == null || it.getType().isAir()) continue;
            String key = FishKeys.getFishKey(plugin, it);
            if (key == null || key.isBlank()) continue;
            totalFish += it.getAmount();
        }
        if (totalFish == 0) {
            player.sendMessage(ChatColor.GRAY + "[FishProc] Place fish into the input slots.");
            return;
        }

        ItemStack knife = inv.getItem(KNIFE_SLOT);
        if (!isKnife(knife)) {
            player.sendMessage(ChatColor.RED + "[FishProc] Insert a valid knife into the knife slot.");
            return;
        }
        ItemMeta kMeta = knife.getItemMeta();
        Damageable kDmg = (kMeta instanceof Damageable) ? (Damageable) kMeta : null;
        int unb = (kMeta != null) ? kMeta.getEnchantLevel(Enchantment.UNBREAKING) : 0;
        int max = maxDamageFor(knife, kDmg);
        int cur = (kDmg != null) ? kDmg.getDamage() : 0;
        if (max <= 0) {
            player.sendMessage(ChatColor.RED + "[FishProc] Knife cannot take durability.");
            return;
        }

        ItemStack outProto = null;
        for (int s : INPUT_SLOTS) {
            ItemStack in = inv.getItem(s);
            if (in == null || in.getType().isAir()) continue;
            String fk = FishKeys.getFishKey(plugin, in);
            if (fk == null || fk.isBlank()) continue;
            ItemStack sample = provider.materialFor(fk, 1);
            if (sample != null && !sample.getType().isAir() && sample.getAmount() > 0) {
                outProto = sample.clone(); outProto.setAmount(1); break;
            }
        }
        if (outProto == null) {
            player.sendMessage(ChatColor.GRAY + "[FishProc] Nothing to produce from these fish.");
            return;
        }

        int capLeft = calcCapacity(inv, outProto, OUTPUT_SLOTS);
        if (capLeft <= 0) {
            player.sendMessage(ChatColor.RED + "[FishProc] No space in output.");
            return;
        }

        int processed = 0, produced = 0;
        boolean broke = false, outOfSpace = false;
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        outer:
        for (int s : INPUT_SLOTS) {
            ItemStack in = inv.getItem(s);
            if (in == null || in.getType().isAir()) continue;
            String fk = FishKeys.getFishKey(plugin, in);
            if (fk == null || fk.isBlank()) continue;

            int amt = in.getAmount();
            while (amt > 0) {
                ItemStack oneOut = provider.materialFor(fk, 1);
                if (oneOut == null || oneOut.getType().isAir() || oneOut.getAmount() <= 0) { amt--; continue; }
                if (!canStack(outProto, oneOut)) {
                    player.sendMessage(ChatColor.RED + "[FishProc] These fish produce different outputs. Process one type at a time.");
                    updateViewers(inv);
                    return;
                }

                int yield = Math.max(1, oneOut.getAmount());
                if (capLeft < yield) {
                    outOfSpace = true;
                    writeBack(inv, s, in, amt);         // << ensure client sees the reduced input
                    updateViewers(inv);                 // << force redraw
                    break outer;
                }

                // consume 1 fish
                amt--;
                processed++;
                produced += yield;
                capLeft -= yield;

                // per-hit Unbreaking
                if (kDmg != null && rng.nextInt(unb + 1) == 0) {
                    cur++;
                    if (cur >= max) {
                        writeBack(inv, s, in, amt);     // << ensure client sees the reduced input
                        inv.setItem(KNIFE_SLOT, null);
                        broke = true;
                        kDmg = null;
                        updateViewers(inv);             // << force redraw (knife + input changed)
                        break outer;
                    }
                }
            }

            // finished this slot normally -> commit the remainder (likely 0)
            writeBack(inv, s, in, amt);
        }

        if (!broke && kDmg != null) {
            kDmg.setDamage(cur);
            knife.setItemMeta(kDmg);
        }

        if (produced > 0) {
            depositMany(inv, outProto, produced, OUTPUT_SLOTS);
            player.playSound(player, Sound.UI_LOOM_TAKE_RESULT, 1.0f, 1.1f);

            int left = Math.max(0, totalFish - processed);
            if (broke) {
                player.sendMessage(ChatColor.YELLOW + "[FishProc] Processed " + processed + " fish → " + produced +
                        " fillet(s); knife broke with " + left + " fish left.");
                player.playSound(player, Sound.ENTITY_ITEM_BREAK, 1.0f, 0.9f);
            } else if (outOfSpace) {
                player.sendMessage(ChatColor.GOLD + "[FishProc] Processed " + processed + " fish → " + produced +
                        " fillet(s); outputs are full (" + left + " fish left).");
            } else {
                player.sendMessage(ChatColor.GREEN + "[FishProc] Processed " + processed + " fish → " + produced + " fillet(s).");
            }
        } else {
            player.sendMessage(ChatColor.GRAY + "[FishProc] Nothing to produce.");
        }

        updateViewers(inv); // final safety refresh
    }

    /* ---------------- helpers ---------------- */

    private static void writeBack(Inventory inv, int slot, ItemStack in, int remaining) {
        if (remaining <= 0) {
            inv.setItem(slot, null);
        } else {
            // clone is safer for client refresh in some implementations
            ItemStack copy = in.clone();
            copy.setAmount(remaining);
            inv.setItem(slot, copy);
        }
    }

    private static void updateViewers(Inventory inv) {
        for (HumanEntity he : inv.getViewers()) {
            if (he instanceof Player p) p.updateInventory();
        }
    }

    private static boolean hasMaxDamage(Damageable dmg) {
        try { return (boolean) dmg.getClass().getMethod("hasMaxDamage").invoke(dmg); }
        catch (Throwable ignore) { return false; }
    }

    private static int maxDamageFor(ItemStack tool, Damageable dmg) {
        if (dmg != null && hasMaxDamage(dmg)) return dmg.getMaxDamage();
        return tool.getType().getMaxDurability();
    }

    private static void depositMany(Inventory inv, ItemStack proto, int amount, int[] slots) {
        int left = amount;
        int maxStack = Math.min(proto.getMaxStackSize(), 64);
        for (int s : slots) {
            if (left <= 0) break;
            ItemStack cur = inv.getItem(s);
            if (cur == null || cur.getType().isAir()) continue;
            if (!canStack(cur, proto)) continue;
            int add = Math.min(left, maxStack - cur.getAmount());
            if (add > 0) { cur.setAmount(cur.getAmount() + add); left -= add; }
        }
        for (int s : slots) {
            if (left <= 0) break;
            ItemStack cur = inv.getItem(s);
            if (cur != null && !cur.getType().isAir()) continue;
            ItemStack place = proto.clone();
            place.setAmount(Math.min(maxStack, left));
            inv.setItem(s, place);
            left -= place.getAmount();
        }
    }

    private static boolean canStack(ItemStack a, ItemStack b) {
        if (a == null || b == null) return false;
        if (a.getType() != b.getType()) return false;
        ItemMeta am = a.getItemMeta(), bm = b.getItemMeta();
        return Objects.equals(am, bm);
    }

    private static int calcCapacity(Inventory inv, ItemStack kind, int[] slots) {
        int cap = 0, maxStack = Math.min(kind.getMaxStackSize(), 64);
        for (int s : slots) {
            ItemStack cur = inv.getItem(s);
            if (cur == null || cur.getType() == Material.AIR) cap += maxStack;
            else if (canStack(cur, kind)) cap += Math.max(0, maxStack - cur.getAmount());
        }
        return cap;
    }

    private boolean isInputSlot(int rawSlot) {
        for (int s : INPUT_SLOTS) if (s == rawSlot) return true;
        return false;
    }
    private boolean isOutputSlot(int rawSlot) {
        for (int s : OUTPUT_SLOTS) if (s == rawSlot) return true;
        return false;
    }

    private boolean isKnife(ItemStack it) {
        if (it == null || it.getType() == Material.AIR) return false;
        if (!it.getType().name().endsWith("_SWORD")) return false;
        ItemMeta meta = it.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(Keys.knife(plugin), PersistentDataType.BYTE);
    }

    private static final class Holder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }
}