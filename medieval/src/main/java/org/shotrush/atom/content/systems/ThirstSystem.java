package org.shotrush.atom.content.systems;

import lombok.Getter;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.shotrush.atom.core.api.annotation.RegisterSystem;
import org.shotrush.atom.core.util.ActionBarManager;
import org.shotrush.atom.core.api.world.EnvironmentalFactorAPI;

@RegisterSystem(
        id = "thirst_system",
        priority = 2,
        dependencies = {"action_bar_manager"},
        toggleable = true,
        description = "Manages player thirst mechanics"
)
public class ThirstSystem implements Listener {

    @Getter
    public static ThirstSystem instance;

    private final Plugin plugin;
    private final Map<UUID, Double> thirstLevels = new HashMap<>();
    private final Map<UUID, Integer> thirstDamageTicks = new HashMap<>();
    
    private static final double MAX_THIRST = 20.0;
    private static final double THIRST_DAMAGE_THRESHOLD = 0.0;
    private static final double BASE_DRAIN_PER_SEC = 0.0166; 

    public ThirstSystem(Plugin plugin) {
        this.plugin = plugin;
        instance = this;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        double savedThirst = org.shotrush.atom.core.api.player.PlayerDataAPI.getDouble(player, "thirst.level", MAX_THIRST);

        thirstLevels.put(playerId, savedThirst);
        updateThirstDisplay(player);
        startThirstTickForPlayer(player);
    }

    @EventHandler
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        double thirst = thirstLevels.getOrDefault(playerId, MAX_THIRST);
        org.shotrush.atom.core.api.player.PlayerDataAPI.setDouble(player, "thirst.level", thirst);

        thirstLevels.remove(playerId);
        thirstDamageTicks.remove(playerId);
        ActionBarManager.getInstance().removeMessage(player, "thirst");
    }

    private void startThirstTickForPlayer(Player player) {
        org.shotrush.atom.core.api.scheduler.SchedulerAPI.runTaskTimer(player, task -> {
            if (!player.isOnline()) {
                task.cancel();
                return;
            }

            updateThirst(player);
        }, 20L, 20L);
    }

    private void updateThirst(Player player) {
        UUID playerId = player.getUniqueId();
        double currentThirst = thirstLevels.getOrDefault(playerId, MAX_THIRST);
        
        double drain = BASE_DRAIN_PER_SEC;

        if (player.isSprinting()) {
            drain *= 2.0;
        }

        double ambient = EnvironmentalFactorAPI.getAmbientTemperature(player);
        if (ambient > 45.0) {
            drain *= 3.0;
        } else if (ambient > 30.0) {
            drain *= 1.5;
        }
        
        currentThirst -= drain;
        currentThirst = Math.max(0, Math.min(MAX_THIRST, currentThirst));
        thirstLevels.put(playerId, currentThirst);

        if (currentThirst <= THIRST_DAMAGE_THRESHOLD) {
            int damageTicks = thirstDamageTicks.getOrDefault(playerId, 0);
            damageTicks += 20;
            thirstDamageTicks.put(playerId, damageTicks);

            if (damageTicks >= 80) { // Every 4 seconds damage
                player.damage(1.0);
                thirstDamageTicks.put(playerId, 0);
            }
        } else {
            thirstDamageTicks.remove(playerId);

            if (currentThirst <= 5.0) {
                player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                        org.bukkit.potion.PotionEffectType.SLOWNESS, 40, 0, false, false
                ));
            }
        }

        checkWaterPurification(player);
        updateThirstDisplay(player);
    }

    @EventHandler
    public void onPlayerItemConsume(org.bukkit.event.player.PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item.getType() == Material.POTION) {
            org.bukkit.inventory.meta.PotionMeta meta = (org.bukkit.inventory.meta.PotionMeta) item.getItemMeta();
            if (meta != null) {
                if (meta.hasCustomEffect(PotionEffectType.REGENERATION)) {
                    drinkPurifiedWater(player);
                } else if (meta.getBasePotionType() == org.bukkit.potion.PotionType.WATER) {
                    drinkRawWater(player);
                }
            }
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK &&
                event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_AIR) return;

        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (item.getType() == Material.AIR || item.getType() == Material.BUCKET) {
            org.bukkit.util.RayTraceResult result = player.rayTraceBlocks(5, org.bukkit.FluidCollisionMode.ALWAYS);
            if (result != null && result.getHitBlock() != null && isWaterBlock(result.getHitBlock())) {
                drinkRawWater(player);
                event.setCancelled(true);
                return;
            }

            if (event.getClickedBlock() != null && isWaterBlock(event.getClickedBlock())) {
                drinkRawWater(player);
                event.setCancelled(true);
                return;
            }
        }
    }

    private boolean isWaterBlock(org.bukkit.block.Block block) {
        return block.getType() == Material.WATER ||
                block.getBlockData() instanceof org.bukkit.block.data.Waterlogged waterlogged && waterlogged.isWaterlogged();
    }

    private void drinkRawWater(Player player) {
        addThirst(player, 5);

        player.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 200, 0, false, false));
        
        ActionBarManager.send(player, "<aqua>+5</aqua> Thirst <red>(Raw Water)</red>");
    }

    private void drinkPurifiedWater(Player player) {
        addThirst(player, 10);
        ActionBarManager.send(player, "<aqua>+10</aqua> Thirst <gray>(Purified)</gray>");
    }

    private void checkWaterPurification(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() != Material.POTION) return;

        double heat = ItemHeatSystem.getItemHeat(item);
        if (heat < 100.0) return;

        org.bukkit.inventory.meta.PotionMeta meta = (org.bukkit.inventory.meta.PotionMeta) item.getItemMeta();
        if (meta != null && !meta.hasCustomEffect(PotionEffectType.REGENERATION)) {
            org.shotrush.atom.core.items.CustomItem purifiedWater =
                    org.shotrush.atom.Atom.getInstance().getItemRegistry().getItem("purified_water");
            if (purifiedWater != null) {
                ItemStack newItem = purifiedWater.create();
                ItemHeatSystem.setItemHeat(newItem, heat);
                player.getInventory().setItemInMainHand(newItem);
            }
        }
    }

    private void updateThirstDisplay(Player player) {
        double thirst = thirstLevels.getOrDefault(player.getUniqueId(), MAX_THIRST);
        int displayThirst = (int) Math.ceil(thirst);

        String color;

        if (thirst <= 0) {
            color = "<dark_red>";
        } else if (thirst <= 5) {
            color = "<red>";
        } else if (thirst <= 10) {
            color = "<gold>";
        } else if (thirst <= 15) {
            color = "<yellow>";
        } else {
            color = "<aqua>";
        }

        String message = color + displayThirst + "<dark_gray>/<gray>" + (int)MAX_THIRST;
        ActionBarManager.getInstance().setMessage(player, "thirst", message);
    }

    public void addThirst(Player player, int amount) {
        UUID playerId = player.getUniqueId();
        double currentThirst = thirstLevels.getOrDefault(playerId, MAX_THIRST);
        setThirst(player, currentThirst + amount);
    }

    public void setThirst(Player player, double amount) {
        thirstLevels.put(player.getUniqueId(), Math.clamp(amount, 0, MAX_THIRST));
        updateThirstDisplay(player);
    }

    public int getThirst(Player player) {
        return (int) Math.ceil(thirstLevels.getOrDefault(player.getUniqueId(), MAX_THIRST));
    }
}
