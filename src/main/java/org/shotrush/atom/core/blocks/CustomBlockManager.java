package org.shotrush.atom.core.blocks;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.reflections.Reflections;
import org.shotrush.atom.Atom;
// import org.shotrush.atom.content.blocks.cog.CogManager; // DISABLED
import org.shotrush.atom.core.blocks.annotation.AutoRegister;
import org.shotrush.atom.core.data.PersistentData;
import org.shotrush.atom.core.util.ActionBarManager;

import java.lang.reflect.Constructor;
import java.util.*;
import java.util.stream.Collectors;


public class CustomBlockManager implements Listener {

    private final Atom plugin;
    @Getter
    public final CustomBlockRegistry registry;
    private final NamespacedKey wrenchKey;
    @Getter
    public final List<CustomBlock> blocks;
    private final CustomBlockDataManager dataManager;
    private ScheduledTask globalUpdateTask;
    private float globalAngle = 0;

    public CustomBlockManager(Atom plugin) {
        this.plugin = plugin;
        this.registry = new CustomBlockRegistry(plugin);
        this.wrenchKey = new NamespacedKey(plugin, "wrench");
        this.blocks = new ArrayList<>();
        this.dataManager = new CustomBlockDataManager(plugin, registry);

        
        registerBlockTypes();

        
        Bukkit.getPluginManager().registerEvents(this, plugin);

        org.shotrush.atom.core.api.scheduler.SchedulerAPI.runGlobalTaskLater(() -> {
            loadBlocks();
            startGlobalUpdate();
        }, 1L);
    }

    
    private void registerBlockTypes() {
        plugin.getLogger().info("Auto-registering block types...");
        
        try {
            Reflections reflections = new Reflections("org.shotrush.atom");
            Set<Class<?>> annotatedClasses = reflections.getTypesAnnotatedWith(AutoRegister.class);
            
            
            List<Class<?>> sortedClasses = annotatedClasses.stream()
                .sorted(Comparator.comparingInt(cls -> 
                    cls.getAnnotation(AutoRegister.class).priority()))
                .collect(Collectors.toList());
            
            for (Class<?> clazz : sortedClasses) {
                if (BlockType.class.isAssignableFrom(clazz)) {
                    try {
                        if (CustomBlock.class.isAssignableFrom(clazz)) {
                            registry.register(clazz, plugin);
                            plugin.getLogger().info("  ✓ Registered: " + clazz.getSimpleName());
                        } else {
                            Constructor<?> constructor = clazz.getConstructor(Atom.class);
                            BlockType blockType = (BlockType) constructor.newInstance(plugin);
                            registry.register(blockType.getIdentifier(), blockType);
                            plugin.getLogger().info("  ✓ Registered: " + blockType.getIdentifier());
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("Failed to register block type: " + clazz.getName());
                        e.printStackTrace();
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to auto-register block types!");
            e.printStackTrace();
        }
    }

    
    private void startGlobalUpdate() {
        globalUpdateTask = org.shotrush.atom.core.api.scheduler.SchedulerAPI.runGlobalTaskTimer(() -> {
            globalAngle += 0.1f;
            if (globalAngle > Math.PI * 2) {
                globalAngle = 0;
            }

            
            
            for (CustomBlock block : new ArrayList<>(blocks)) {
                BlockType type = registry.getBlockType(block.getBlockType());
                if (type != null && type.requiresUpdate()) {
                    block.update(globalAngle);
                }
            }
        }, 1L, 1L);
    }

    public void stopGlobalUpdate() {
        if (globalUpdateTask != null) {
            globalUpdateTask.cancel();
        }
    }

    private void loadBlocks() {
        List<CustomBlock> loadedBlocks = dataManager.loadBlocks();
        blocks.addAll(loadedBlocks);
        plugin.getLogger().info("Loaded " + loadedBlocks.size() + " block(s) from data");
        
        org.shotrush.atom.core.api.scheduler.SchedulerAPI.runGlobalTaskLater(() -> {
            int spawnedCount = 0;
            
            for (CustomBlock block : blocks) {
                if (block.getSpawnLocation().getWorld() != null) {
                    block.setDisplayUUID(null);
                    block.setInteractionUUID(null);

                    if (block instanceof InteractiveSurface surface) {
                        for (InteractiveSurface.PlacedItem item : surface.getPlacedItems()) {
                            item.setDisplayUUID(null);
                        }
                        plugin.getLogger().info("Cleared stale UUIDs for " + block.getIdentifier());
                    } else {
                        plugin.getLogger().info("Cleared stale UUIDs for " + block.getIdentifier());
                    }
                    
                    block.spawn(plugin);
                    spawnedCount++;
                }
            }
            
            plugin.getLogger().info("Spawned " + spawnedCount + " block(s) after world load");


            // CogManager cogManager = new CogManager(plugin);
            // cogManager.recalculatePower(blocks);
        }, 20L);

    }

    public void saveBlocks() {
        blocks.removeIf(block -> !block.isValid());
        dataManager.saveBlocks(blocks);
        plugin.getLogger().info("Saved " + blocks.size() + " block(s)");
    }
    
    public void cleanupAllDisplays() {
        plugin.getLogger().info("Skipping entity cleanup on shutdown (Folia-safe)");
    }

    public void removeAllBlocks() {
        for (CustomBlock block : blocks) {
            block.remove();
        }
        blocks.clear();
        dataManager.saveBlocks(blocks);
        plugin.getLogger().info("Removed all blocks");
    }

    public ItemStack createWrench() {
        ItemStack wrench = new ItemStack(Material.WOODEN_HOE);
        ItemMeta meta = wrench.getItemMeta();

        if (meta != null) {
            meta.setDisplayName("§e🔧 Mechanical Wrench");
            meta.setLore(Arrays.asList(
                "§7A tool for working with blocks",
                "§8• Right-click: Interact",
                "§8• Shift + Right-click: Remove",
                "§8[Engineering Tool]"
            ));

            PersistentData.flag(meta, wrenchKey.getKey());
            meta.setUnbreakable(true);
            wrench.setItemMeta(meta);
        }

        return wrench;
    }

    public void giveBlockItem(Player player, String blockTypeId) {
        ItemStack item = createBlockItem(blockTypeId);
        if (item == null) {
            ActionBarManager.send(player, "§cUnknown block type: " + blockTypeId);
            return;
        }

        player.getInventory().addItem(item);
        ActionBarManager.send(player, "§aYou received a " + item.getItemMeta().getDisplayName() + "!");
    }


    public ItemStack createBlockItem(String blockTypeId) {
        BlockType blockType = registry.getBlockType(blockTypeId);
        if (blockType == null) {
            return null;
        }

        ItemStack item = new ItemStack(blockType.getItemMaterial());
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(blockType.getDisplayName());
            meta.setLore(Arrays.asList(blockType.getLore()));

            org.bukkit.inventory.meta.components.CustomModelDataComponent component = meta.getCustomModelDataComponent();
            component.setStrings(java.util.List.of(blockTypeId));
            meta.setCustomModelDataComponent(component);

            NamespacedKey key = registry.getKey(blockTypeId);
            PersistentData.flag(meta, key.getKey());

            item.setItemMeta(meta);
        }

        return item;
    }

    public void giveWrench(Player player) {
        ItemStack wrench = plugin.getItemRegistry().createItem("wrench");
        if (wrench != null) {
            player.getInventory().addItem(wrench);
            ActionBarManager.send(player, "§aYou received a Mechanical Wrench!");
        }
    }
    
    public CustomBlock getBlockAt(Location location) {
        for (CustomBlock block : blocks) {
            if (block.getBlockLocation().equals(location)) {
                return block;
            }
        }
        return null;
    }
    
    private void updateBlockEntityUUIDs(CustomBlock block) {
        if (block.getSpawnLocation().getWorld() == null) return;
        
        
        for (Entity entity : block.getSpawnLocation().getWorld().getNearbyEntities(block.getSpawnLocation(), 0.5, 0.5, 0.5)) {
            double distance = entity.getLocation().distance(block.getSpawnLocation());
            if (distance < 0.1) {
                if (entity instanceof org.bukkit.entity.ItemDisplay) {
                    block.setDisplayUUID(entity.getUniqueId());
                } else if (entity instanceof org.bukkit.entity.Interaction) {
                    block.setInteractionUUID(entity.getUniqueId());
                }
            }
        }
    }
    
    public void removeBlock(CustomBlock block) {
        block.remove();
        blocks.remove(block);
        block.onRemoved();
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        
        for (Map.Entry<String, BlockType> entry : registry.getAllBlockTypes().entrySet()) {
            BlockType blockType = entry.getValue();
            if (item.getType() != blockType.getItemMaterial()) continue;

            NamespacedKey key = registry.getKey(entry.getKey());
            if (!PersistentData.isFlagged(meta, key.getKey())) continue;

            event.setCancelled(true);

            Location blockLocation = event.getBlock().getLocation();
            Location spawnLocation = blockLocation.clone().add(0.5, 0, 0.5);
            
            for (CustomBlock existingBlock : blocks) {
                if (existingBlock.getBlockLocation().equals(blockLocation)) {
                    event.getPlayer().sendMessage("§cA block is already placed here!");
                    return;
                }
            }
            
            CustomBlock customBlock = blockType.createBlock(
                spawnLocation,
                blockLocation,
                event.getBlockAgainst().getFace(event.getBlock())
            );
            customBlock.spawn(plugin);
            blocks.add(customBlock);
            customBlock.onPlaced();

            if (item.getAmount() > 1) {
                item.setAmount(item.getAmount() - 1);
            } else {
                event.getPlayer().getInventory().setItem(event.getHand(), new ItemStack(Material.AIR));
            }
            return;
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEntityEvent event) {
        if (event.isCancelled()) return;
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        
        Entity entity = event.getRightClicked();
        if (!(entity instanceof Interaction)) return;
        
        Interaction interaction = (Interaction) entity;
        
        for (int i = 0; i < blocks.size(); i++) {
            CustomBlock block = blocks.get(i);
            if (block.getInteractionUUID() != null && block.getInteractionUUID().equals(entity.getUniqueId())) {
                org.shotrush.atom.core.api.scheduler.SchedulerAPI.runTask(interaction.getLocation(), () -> {
                    org.bukkit.entity.Entity ent = Bukkit.getEntity(interaction.getUniqueId());
                    if (ent instanceof Interaction inter) {
                        inter.setResponsive(false);
                        inter.setResponsive(true);
                    }
                });
                
                handleBlockInteraction(block, i, event.getPlayer(), event);
                return;
            }
        }

        Location interactionLoc = interaction.getLocation();
        for (int i = 0; i < blocks.size(); i++) {
            CustomBlock block = blocks.get(i);
            Location blockLoc = block.getSpawnLocation();
            if (blockLoc.getWorld().equals(interactionLoc.getWorld()) &&
                blockLoc.distance(interactionLoc) < 0.5) {

                block.setInteractionUUID(entity.getUniqueId());

                for (Entity nearbyEntity : blockLoc.getWorld().getNearbyEntities(blockLoc, 1, 1, 1)) {
                    if (nearbyEntity instanceof org.bukkit.entity.ItemDisplay) {
                        if (nearbyEntity.getLocation().distance(blockLoc) < 0.5) {
                            block.setDisplayUUID(nearbyEntity.getUniqueId());
                            plugin.getLogger().info("Updated display and interaction UUIDs for block at " + blockLoc);
                            break;
                        }
                    }
                }

                if (block instanceof InteractiveSurface surface) {
                    surface.updateItemDisplayUUIDs();
                    plugin.getLogger().info("Updated item display UUIDs for InteractiveSurface");
                }
                
                org.shotrush.atom.core.api.scheduler.SchedulerAPI.runTask(interaction.getLocation(), () -> {
                    org.bukkit.entity.Entity ent = Bukkit.getEntity(interaction.getUniqueId());
                    if (ent instanceof Interaction inter) {
                        inter.setResponsive(false);
                        inter.setResponsive(true);
                    }
                });
                
                handleBlockInteraction(block, i, event.getPlayer(), event);
                return;
            }
        }
    }
    
    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST)
    public void onEntityDamage(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        if (event.isCancelled()) return;
        if (!(event.getDamager() instanceof Player)) return;
        if (!(event.getEntity() instanceof Interaction)) return;
        
        Player player = (Player) event.getDamager();
        Interaction interaction = (Interaction) event.getEntity();

        for (int i = 0; i < blocks.size(); i++) {
            CustomBlock block = blocks.get(i);
            if (block.getInteractionUUID() != null && block.getInteractionUUID().equals(interaction.getUniqueId())) {
                event.setCancelled(true);

                if (block.getBlockLocation().getBlock().getType() != Material.BLACK_STAINED_GLASS) {
                    BlockType blockType = registry.getBlockType(block.getBlockType());
                    if (blockType != null) {
                        ItemStack dropItem = blockType.getDropItem();
                        if (dropItem != null) {
                            block.getSpawnLocation().getWorld().dropItemNaturally(block.getSpawnLocation(), dropItem);
                        }
                    }
                    
                    block.remove();
                    blocks.remove(i);
                    block.onRemoved();
                    ActionBarManager.send(player, "§cCustom block removed");
                }
                return;
            }
        }

        Location interactionLoc = interaction.getLocation();
        for (int i = 0; i < blocks.size(); i++) {
            CustomBlock block = blocks.get(i);
            Location blockLoc = block.getSpawnLocation();
            if (blockLoc.getWorld().equals(interactionLoc.getWorld()) &&
                blockLoc.distance(interactionLoc) < 0.5) {

                block.setInteractionUUID(interaction.getUniqueId());

                for (Entity nearbyEntity : blockLoc.getWorld().getNearbyEntities(blockLoc, 1, 1, 1)) {
                    if (nearbyEntity instanceof org.bukkit.entity.ItemDisplay) {
                        if (nearbyEntity.getLocation().distance(blockLoc) < 0.5) {
                            block.setDisplayUUID(nearbyEntity.getUniqueId());
                            break;
                        }
                    }
                }

                if (block instanceof InteractiveSurface surface) {
                    surface.updateItemDisplayUUIDs();
                }
                
                event.setCancelled(true);

                if (block.getBlockLocation().getBlock().getType() != Material.BLACK_STAINED_GLASS) {
                    BlockType blockType = registry.getBlockType(block.getBlockType());
                    if (blockType != null) {
                        ItemStack dropItem = blockType.getDropItem();
                        if (dropItem != null) {
                            block.getSpawnLocation().getWorld().dropItemNaturally(block.getSpawnLocation(), dropItem);
                        }
                    }
                    
                    block.remove();
                    blocks.remove(i);
                    block.onRemoved();
                    ActionBarManager.send(player, "§cCustom block removed");
                }
                return;
            }
        }
    }
    
    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST)
    public void onBlockBreak(org.bukkit.event.block.BlockBreakEvent event) {
        if (event.isCancelled()) return;
        
        Material blockType = event.getBlock().getType();
        
        // Only handle BLACK_STAINED_GLASS, exclude CAMPFIRE to avoid conflicts with CampfireSystem
        if (blockType != Material.BLACK_STAINED_GLASS) return;
        
        Location blockLoc = event.getBlock().getLocation();
        for (int i = 0; i < blocks.size(); i++) {
            CustomBlock block = blocks.get(i);
            if (block.getBlockLocation().equals(blockLoc)) {
                
                BlockType blockTypeData = registry.getBlockType(block.getBlockType());
                if (blockTypeData != null) {
                    ItemStack dropItem = blockTypeData.getDropItem();
                    if (dropItem != null) {
                        blockLoc.getWorld().dropItemNaturally(blockLoc, dropItem);
                    }
                }
                
                
                block.remove();
                blocks.remove(i);
                block.onRemoved();
                
                
                return;
            }
        }
    }
    
    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
    public void onPlayerInteractBlock(org.bukkit.event.player.PlayerInteractEvent event) {
        if (event.isCancelled()) return;
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        if (event.getClickedBlock() == null) return;
        
        Material blockType = event.getClickedBlock().getType();
        
        // Only handle BLACK_STAINED_GLASS, exclude CAMPFIRE to avoid conflicts with CampfireSystem
        if (blockType != Material.BLACK_STAINED_GLASS) return;
        
        Location clickedLoc = event.getClickedBlock().getLocation();
        for (int i = 0; i < blocks.size(); i++) {
            CustomBlock block = blocks.get(i);
            if (block.getBlockLocation().equals(clickedLoc)) {
                handleBlockInteraction(block, i, event.getPlayer(), event);
                return;
            }
        }
    }
    
    private void handleBlockInteraction(CustomBlock block, int index, Player player, org.bukkit.event.Cancellable event) {
        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        boolean hasWrench = plugin.getItemRegistry().getItem("wrench") != null &&
                           plugin.getItemRegistry().getItem("wrench").isCustomItem(itemInHand);

        if (block instanceof org.shotrush.atom.core.blocks.InteractiveSurface) {
            if (hasWrench) {
                event.setCancelled(true);
                if (player.isSneaking()) {
                    if (block.onWrenchInteract(player, true)) {
                        return;
                    }
                    block.remove();
                    blocks.remove(index);
                    block.onRemoved();
                    ActionBarManager.send(player, "§cBlock removed!");
                    return;
                } else {
                    block.onWrenchInteract(player, false);
                    return;
                }
            } else {
                boolean handled = block.onInteract(player, player.isSneaking());
                if (!handled) {
                    handled = block.onWrenchInteract(player, false);
                }
                if (handled) {
                    event.setCancelled(true);
                }
                return;
            }
        }
        
        
        if (hasWrench) {
            event.setCancelled(true);
            if (player.isSneaking()) {
                if (block.onWrenchInteract(player, true)) {
                    return;
                }
                block.remove();
                blocks.remove(index);
                block.onRemoved();
                ActionBarManager.send(player, "§cBlock removed!");
                return;
            } else {
                block.onWrenchInteract(player, false);
                return;
            }
        } else {
            
            
            block.onInteract(player, player.isSneaking());
        }
    }

}
