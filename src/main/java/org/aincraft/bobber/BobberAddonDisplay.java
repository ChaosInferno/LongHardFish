// src/main/java/org/aincraft/bobber/BobberAddonDisplay.java
package org.aincraft.bobber;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.WrappedDataValue;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import org.aincraft.rods.RodDefinition;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class BobberAddonDisplay implements Listener {

    public interface RodResolver {
        RodDefinition resolveFor(Player angler);
    }

    public interface BobberItemFactory {
        ItemStack createItemForBobberKey(String bobberModelKey);
    }

    private final Plugin plugin;
    private final ProtocolManager protocol;
    private final RodResolver rodResolver;
    private final BobberItemFactory itemFactory;
    private int faceTaskId = -1;

    // hookId -> (viewer -> fakeDisplayId)
    private final Map<Integer, Map<UUID, Integer>> displayByHookByViewer = new ConcurrentHashMap<>();
    // hookId -> (viewer -> fakeShimId)
    private final Map<Integer, Map<UUID, Integer>> shimByHookByViewer = new ConcurrentHashMap<>();
    // hookId -> owner (real angler)
    private final Map<Integer, UUID> ownerByHookId = new ConcurrentHashMap<>();

    public BobberAddonDisplay(Plugin plugin, RodResolver rodResolver, BobberItemFactory itemFactory) {
        this.plugin = plugin;
        this.rodResolver = rodResolver;
        this.itemFactory = itemFactory;
        this.protocol = ProtocolLibrary.getProtocolManager();
    }

    public void register() {
        // Track owner early from Bukkit (avoids heuristic later)
        Bukkit.getPluginManager().registerEvents(this, plugin);

        // Spawn: hook -> shim (ArmorStand) -> display (ItemDisplay)
        protocol.addPacketListener(new PacketAdapter(plugin, PacketType.Play.Server.SPAWN_ENTITY) {
            @Override public void onPacketSending(PacketEvent event) {
                if (event.isCancelled()) return;
                if (event.getPacket().getEntityTypeModifier().read(0) != EntityType.FISHING_BOBBER) return;

                final Player viewer = event.getPlayer();
                final int hookId = event.getPacket().getIntegers().read(0);

                Player angler = tryFindOwner(viewer, hookId);
                RodDefinition def = (angler != null) ? rodResolver.resolveFor(angler) : null;
                String key = (def != null) ? def.bobberModelKey() : null;
                if (key == null || key.isBlank()) return;

                ItemStack item = itemFactory.createItemForBobberKey(key);
                if (item == null) return;

                // Spawn positions from packet
                double x = event.getPacket().getDoubles().read(0);
                double y = event.getPacket().getDoubles().read(1);
                double z = event.getPacket().getDoubles().read(2);
                Location loc = new Location(viewer.getWorld(), x, y, z);

                int shimId = newClientEntityId();
                int displayId = newClientEntityId();

                // spawn shim (invisible marker armorstand) + display
                spawnShimArmorStand(viewer, shimId, loc);
                spawnItemDisplay(viewer, displayId, loc);
                setDisplayItemOnly(viewer, displayId, item);

                // establish mount chain: hook <- shim <- display
                mountPassenger(viewer, hookId, shimId);
                mountPassenger(viewer, shimId, displayId);

                shimByHookByViewer.computeIfAbsent(hookId, k -> new ConcurrentHashMap<>())
                        .put(viewer.getUniqueId(), shimId);
                displayByHookByViewer.computeIfAbsent(hookId, k -> new ConcurrentHashMap<>())
                        .put(viewer.getUniqueId(), displayId);

                // re-assert next tick to handle ordering/race
                Bukkit.getScheduler().runTask(plugin, () -> {
                    mountPassenger(viewer, hookId, shimId);
                    mountPassenger(viewer, shimId, displayId);
                });
                startFacingTask();
            }
        });

        // If server remounts, re-assert our local chain after
        protocol.addPacketListener(new PacketAdapter(plugin, PacketType.Play.Server.MOUNT) {
            @Override public void onPacketSending(PacketEvent event) {
                if (event.isCancelled()) return;
                final Player viewer = event.getPlayer();
                final int vehicleId = event.getPacket().getIntegers().read(0);

                // If the vehicle is the hook, ensure shim is on it; if the vehicle is shim, ensure display is on shim.
                // We only ever created a shim for HOOK ids we know about.
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Map<UUID, Integer> shimMap = shimByHookByViewer.get(vehicleId);
                    if (shimMap != null) {
                        Integer shimId = shimMap.get(viewer.getUniqueId());
                        if (shimId != null) {
                            mountPassenger(viewer, vehicleId, shimId);
                            Map<UUID, Integer> dispMap = displayByHookByViewer.get(vehicleId);
                            if (dispMap != null) {
                                Integer displayId = dispMap.get(viewer.getUniqueId());
                                if (displayId != null) {
                                    mountPassenger(viewer, shimId, displayId);
                                }
                            }
                            return;
                        }
                    }
                    // Vehicle might be a shim; reassert display on it too
                    for (Map<UUID, Integer> perViewerShim : shimByHookByViewer.values()) {
                        Integer maybeShim = perViewerShim.get(viewer.getUniqueId());
                        if (maybeShim != null && maybeShim == vehicleId) {
                            // find corresponding hook (reverse search)
                            Integer displayId = null;
                            Integer hookId = null;
                            for (Map.Entry<Integer, Map<UUID, Integer>> e : displayByHookByViewer.entrySet()) {
                                Integer id = e.getValue().get(viewer.getUniqueId());
                                if (id != null) { displayId = id; hookId = e.getKey(); break; }
                            }
                            if (displayId != null) {
                                mountPassenger(viewer, vehicleId, displayId);
                                if (hookId != null) mountPassenger(viewer, hookId, vehicleId);
                            }
                            break;
                        }
                    }
                });
            }
        });

        // Cleanup per viewer when hook is destroyed
        protocol.addPacketListener(new PacketAdapter(plugin, ListenerPriority.NORMAL, PacketType.Play.Server.ENTITY_DESTROY) {
            @Override public void onPacketSending(PacketEvent event) {
                final Player viewer = event.getPlayer();

                List<Integer> destroyed;
                try {
                    destroyed = event.getPacket().getIntLists().read(0); // 1.20.5+
                } catch (Exception ex) {
                    return;
                }
                if (destroyed == null || destroyed.isEmpty()) return;

                for (int hookId : destroyed) {
                    ownerByHookId.remove(hookId);

                    Integer displayId = null;
                    Map<UUID, Integer> dispPerViewer = displayByHookByViewer.get(hookId);
                    if (dispPerViewer != null) displayId = dispPerViewer.remove(viewer.getUniqueId());

                    Integer shimId = null;
                    Map<UUID, Integer> shimPerViewer = shimByHookByViewer.get(hookId);
                    if (shimPerViewer != null) shimId = shimPerViewer.remove(viewer.getUniqueId());

                    if (displayId != null) destroyClientEntity(viewer, displayId);
                    if (shimId != null) destroyClientEntity(viewer, shimId);

                    if (dispPerViewer != null && dispPerViewer.isEmpty()) displayByHookByViewer.remove(hookId);
                    if (shimPerViewer != null && shimPerViewer.isEmpty()) shimByHookByViewer.remove(hookId);
                }
            }
        });
    }

    // ——————— Spawns ———————

    private static int newClientEntityId() {
        return ThreadLocalRandom.current().nextInt(1_500_000_000, Integer.MAX_VALUE);
    }

    private void spawnShimArmorStand(Player viewer, int id, Location at) {
        PacketContainer spawn = new PacketContainer(PacketType.Play.Server.SPAWN_ENTITY);
        spawn.getIntegers().write(0, id);
        spawn.getUUIDs().write(0, UUID.randomUUID());
        spawn.getEntityTypeModifier().write(0, EntityType.ARMOR_STAND);
        spawn.getDoubles().write(0, at.getX());
        spawn.getDoubles().write(1, at.getY());
        spawn.getDoubles().write(2, at.getZ());
        spawn.getBytes().write(0, (byte) 0); // pitch
        spawn.getBytes().write(1, (byte) 0); // yaw
        spawn.getIntegers().write(1, 0);     // data
        send(viewer, spawn);

        // Metadata: Invisible + NoGravity + Small + Marker
        List<WrappedDataValue> values = new ArrayList<>();

        // Entity shared flags (index 0, Byte) -> set Invisible bit (0x20)
        byte entityFlags = 0x20; // invisible
        values.add(new WrappedDataValue(0, WrappedDataWatcher.Registry.get(Byte.class), entityFlags));

        // NoGravity (index 5, Boolean)
        values.add(new WrappedDataValue(5, WrappedDataWatcher.Registry.get(Boolean.class), true));

        // ArmorStand flags (index 15, Byte): 0x01=small, 0x10=marker
        byte asFlags = (byte) (0x01 | 0x10);
        values.add(new WrappedDataValue(15, WrappedDataWatcher.Registry.get(Byte.class), asFlags));

        PacketContainer meta = new PacketContainer(PacketType.Play.Server.ENTITY_METADATA);
        meta.getIntegers().write(0, id);
        meta.getDataValueCollectionModifier().write(0, values);
        send(viewer, meta);
    }

    private void spawnItemDisplay(Player viewer, int id, Location at) {
        PacketContainer spawn = new PacketContainer(PacketType.Play.Server.SPAWN_ENTITY);
        spawn.getIntegers().write(0, id);
        spawn.getUUIDs().write(0, UUID.randomUUID());
        spawn.getEntityTypeModifier().write(0, EntityType.ITEM_DISPLAY);
        spawn.getDoubles().write(0, at.getX());
        spawn.getDoubles().write(1, at.getY());
        spawn.getDoubles().write(2, at.getZ());
        spawn.getBytes().write(0, (byte) 0);
        spawn.getBytes().write(1, (byte) 0);
        spawn.getIntegers().write(1, 0);
        send(viewer, spawn);

        // Keep it light: just NoGravity true (index 5)
        List<WrappedDataValue> values = new ArrayList<>();
        values.add(new WrappedDataValue(5, WrappedDataWatcher.Registry.get(Boolean.class), true));

        PacketContainer meta = new PacketContainer(PacketType.Play.Server.ENTITY_METADATA);
        meta.getIntegers().write(0, id);
        meta.getDataValueCollectionModifier().write(0, values);
        send(viewer, meta);
    }

    private void setDisplayItemOnly(Player viewer, int id, ItemStack item) {
        // Index 23 = displayed item for Item Display on 1.21.x
        Object nmsItem = com.comphenix.protocol.utility.MinecraftReflection.getMinecraftItemStack(item);
        List<WrappedDataValue> values = new ArrayList<>();
        values.add(new WrappedDataValue(23, WrappedDataWatcher.Registry.getItemStackSerializer(false), nmsItem));

        PacketContainer meta = new PacketContainer(PacketType.Play.Server.ENTITY_METADATA);
        meta.getIntegers().write(0, id);
        meta.getDataValueCollectionModifier().write(0, values);
        send(viewer, meta);
    }

    // ——————— Mount & destroy ———————

    private void mountPassenger(Player viewer, int vehicleId, int passengerId) {
        PacketContainer mount = new PacketContainer(PacketType.Play.Server.MOUNT);
        mount.getIntegers().write(0, vehicleId);
        mount.getIntegerArrays().write(0, new int[]{ passengerId });
        send(viewer, mount);
    }

    private void destroyClientEntity(Player viewer, int id) {
        PacketContainer destroy = new PacketContainer(PacketType.Play.Server.ENTITY_DESTROY);
        destroy.getIntLists().write(0, Collections.singletonList(id));
        send(viewer, destroy);
    }

    private void send(Player viewer, PacketContainer packet) {
        try {
            protocol.sendServerPacket(viewer, packet, false);
        } catch (Exception ignored) {}
    }

    // ——————— Owner resolution ———————

    private Player tryFindOwner(Player viewer, int hookId) {
        UUID u = ownerByHookId.get(hookId);
        if (u != null) return Bukkit.getPlayer(u);

        // Fallback: nearest player to hook
        World w = viewer.getWorld();
        Entity hook = protocol.getEntityFromID(w, hookId);
        if (hook == null) return null;

        double best = 36 * 36;
        Player found = null;
        for (Player p : w.getPlayers()) {
            double d = p.getLocation().distanceSquared(hook.getLocation());
            if (d < best) { best = d; found = p; }
        }
        return found;
    }

    private void startFacingTask() {
        if (faceTaskId != -1) return;
        // start after half a second, run every 2 ticks
        faceTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::tickFaceOwners, 10L, 2L);
    }

    private void tickFaceOwners() {
        // For each hook we’re tracking:
        shimByHookByViewer.forEach((hookId, perViewerShim) -> {
            UUID owner = ownerByHookId.get(hookId);
            if (owner == null) return;
            Player angler = Bukkit.getPlayer(owner);
            if (angler == null) return;

            // We'll face toward the angler's eyes
            Location target = angler.getEyeLocation();

            // Use the real hook's position (server entity) as pivot if we can
            Entity hook = protocol.getEntityFromID(angler.getWorld(), hookId);
            Location pivot = (hook != null ? hook.getLocation() : target);

            // compute yaw (MC expects 0..360 yaw -> byte)
            float yawDeg = yawTo(pivot, target);
            byte yawByte = (byte) (int) Math.floor(yawDeg * 256f / 360f);
            byte pitchByte = 0; // keeping it level; compute if you want tilt

            // rotate each viewer's fake display (or shim) toward the angler
            Map<UUID, Integer> perViewerDisp = displayByHookByViewer.get(hookId);
            perViewerShim.forEach((viewerId, shimId) -> {
                Player viewer = Bukkit.getPlayer(viewerId);
                if (viewer == null) return;

                Integer displayId = (perViewerDisp != null) ? perViewerDisp.get(viewerId) : null;
                int idToRotate = (displayId != null ? displayId : shimId); // prefer rotating the display

                // 1) Try pure look
                if (!sendLook(viewer, idToRotate, yawByte, pitchByte)) {
                    // 2) Fallback: teleport with same coords but new yaw (uses pivot coords)
                    sendTeleportYaw(viewer, idToRotate, pivot, yawByte, pitchByte);
                }
            });
        });
    }

    // yaw so the entity faces 'to' from 'from'
    private static float yawTo(Location from, Location to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        // Minecraft yaw: 0 = south, +180 = north; atan2 handles quadrants
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        // normalize to [0,360)
        if (yaw < 0) yaw += 360f;
        return yaw;
    }

    private boolean sendLook(Player viewer, int entityId, byte yaw, byte pitch) {
        try {
            PacketContainer look = new PacketContainer(PacketType.Play.Server.ENTITY_LOOK);
            look.getIntegers().write(0, entityId);
            look.getBytes().write(0, yaw);
            look.getBytes().write(1, pitch);
            look.getBooleans().write(0, true); // onGround: true is fine
            protocol.sendServerPacket(viewer, look, false);

            // also set head yaw where applicable (harmless if ignored)
            PacketContainer head = new PacketContainer(PacketType.Play.Server.ENTITY_HEAD_ROTATION);
            head.getIntegers().write(0, entityId);
            head.getBytes().write(0, yaw);
            protocol.sendServerPacket(viewer, head, false);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void sendTeleportYaw(Player viewer, int entityId, Location pos, byte yaw, byte pitch) {
        try {
            PacketContainer tp = new PacketContainer(PacketType.Play.Server.ENTITY_TELEPORT);
            tp.getIntegers().write(0, entityId);
            tp.getDoubles().write(0, pos.getX());
            tp.getDoubles().write(1, pos.getY());
            tp.getDoubles().write(2, pos.getZ());
            tp.getBytes().write(0, yaw);
            tp.getBytes().write(1, pitch);
            tp.getBooleans().write(0, true);
            protocol.sendServerPacket(viewer, tp, false);
        } catch (Exception ignored) {}
    }

    // ——————— Bukkit events ———————

    @EventHandler
    public void onFish(PlayerFishEvent e) {
        if (e.getHook() == null) return;
        final int hookId = e.getHook().getEntityId();
        switch (e.getState()) {
            case FISHING: // cast started
                ownerByHookId.put(hookId, e.getPlayer().getUniqueId());
                break;

            case BITE:
                // keep mapping so rotation continues
                break;

            case FAILED_ATTEMPT:
            case IN_GROUND:
                // bobber still around -> keep mapping so it keeps rotating
                break;

            case REEL_IN:
            case CAUGHT_FISH:
            case CAUGHT_ENTITY:
                // these end the cast -> safe to clear
                ownerByHookId.remove(hookId);
                break;

            default:
                break;
        }

    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID u = e.getPlayer().getUniqueId();

        List<Integer> toDestroy = new ArrayList<>();
        List<Integer> emptyHooks = new ArrayList<>();

        // collect viewer's fake ids and clean empty maps
        displayByHookByViewer.forEach((hook, perViewer) -> {
            Integer disp = perViewer.remove(u);
            if (disp != null) toDestroy.add(disp);
            if (perViewer.isEmpty()) emptyHooks.add(hook);
        });
        emptyHooks.forEach(displayByHookByViewer::remove);
        emptyHooks.clear();

        shimByHookByViewer.forEach((hook, perViewer) -> {
            Integer shim = perViewer.remove(u);
            if (shim != null) toDestroy.add(shim);
            if (perViewer.isEmpty()) emptyHooks.add(hook);
        });
        emptyHooks.forEach(shimByHookByViewer::remove);

        // send destroys (client ignores if not tracking)
        toDestroy.forEach(id -> destroyClientEntity(e.getPlayer(), id));
    }
}