package com.archyx.aureliumskills.mana;

import com.archyx.aureliumskills.AureliumSkills;
import com.archyx.aureliumskills.api.event.TerraformBlockBreakEvent;
import com.archyx.aureliumskills.configuration.OptionL;
import com.archyx.aureliumskills.data.PlayerData;
import com.archyx.aureliumskills.lang.ManaAbilityMessage;
import com.archyx.aureliumskills.skills.Skills;
import com.archyx.aureliumskills.skills.excavation.ExcavationSource;
import com.archyx.aureliumskills.source.SourceTag;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.inventory.CookingRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.*;

public class Terraform extends ReadiedManaAbility {

    public Terraform(AureliumSkills plugin) {
        super(plugin, MAbility.TERRAFORM, ManaAbilityMessage.TERRAFORM_START, ManaAbilityMessage.TERRAFORM_END,
                new String[]{"SHOVEL", "SPADE"}, new Action[]{Action.RIGHT_CLICK_BLOCK, Action.RIGHT_CLICK_AIR});
    }

    @Override
    public void onActivate(Player player, PlayerData playerData) {
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1, 1);
    }

    @Override
    public void onStop(Player player, PlayerData playerData) {

    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        if (!OptionL.isEnabled(Skills.EXCAVATION)) return;
        if (event.isCancelled()) return;
        Player player = event.getPlayer();
        Block block = event.getBlock();
        if (blockAbility(player)) return;
        if (!plugin.getAbilityManager().isEnabled(MAbility.TERRAFORM)) return;
        if (block.hasMetadata("block-ignore")) { // Compatibility fix
            return;
        }
        if (!block.hasMetadata("AureliumSkills-Terraform") && event.getClass() == BlockBreakEvent.class) {
            applyTerraform(player, block);
        }
    }

    private void applyTerraform(Player player, Block block) {
        // Check if block is applicable to ability
        ExcavationSource source = ExcavationSource.getSource(block);
        if (source == null) return;
        if (!hasTag(source, SourceTag.TERRAFORM_APPLICABLE)) return;
        // Apply if activated
        if (isActivated(player)) {
            if (isHoldingMaterial(player)) terraformBreak(player, block); // KioCG
            return;
        }
        // Checks if ability is ready
        if (isReady(player) && isHoldingMaterial(player) && hasEnoughMana(player)) {
            if (activate(player)) {
                terraformBreak(player, block);
            }
        }
    }

    // KioCG start
    private final Map<Material, ItemStack> smeltCache = new EnumMap<>(Material.class);
    private final ItemStack air = new ItemStack(Material.AIR);

    @EventHandler
    public void onBlockDropItem(BlockDropItemEvent event) {
        if (!event.getBlock().hasMetadata("AureliumSkills-Terraform")) return;
        event.getBlock().removeMetadata("AureliumSkills-Terraform", plugin);

        event.getItems().forEach(item -> {
            final ItemStack origin = item.getItemStack();
            if (origin.hasItemMeta()) return; // 尽可能兼容其他插件

            ItemStack result = smeltCache.computeIfAbsent(origin.getType(), material -> {
                ItemStack itemStack = origin;

                outer:
                while (true) {
                    Iterator<Recipe> iterator = Bukkit.recipeIterator();
                    while (iterator.hasNext()) {
                        Recipe recipe = iterator.next();
                        if (!(recipe instanceof CookingRecipe)) continue;
                        if (!((CookingRecipe<?>) recipe).getInputChoice().test(itemStack)) continue;
                        itemStack = recipe.getResult();
                        continue outer;
                    }
                    return itemStack != origin ? itemStack : air;
                }
            });

            if (result != air) {
                item.setItemStack(result.asQuantity(result.getAmount() * origin.getAmount()));
            }
        });
    }
    // KioCG end

    private void terraformBreak(Player player, Block block) {
        block.setMetadata("AureliumSkills-Terraform", new FixedMetadataValue(plugin, true)); // KioCG

        /*
        Material material = block.getType();
        BlockFace[] faces = new BlockFace[] {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};
        LinkedList<Block> toCheck = new LinkedList<>();
        toCheck.add(block);
        int count = 0;
        int maxCount = plugin.getManaAbilityManager().getOptionAsInt(MAbility.TERRAFORM, "max_blocks", 61);
        while ((block = toCheck.poll()) != null && count < maxCount) {
            if (block.getType() == material) {
                block.setMetadata("AureliumSkills-Terraform", new FixedMetadataValue(plugin, true));
                breakBlock(player, block);
                for (BlockFace face : faces) {
                    toCheck.add(block.getRelative(face));
                }
                count++;
            }
        }
         */
    }

    private void breakBlock(Player player, Block block) {
        if (!plugin.getTownySupport().canBreak(player, block)) {
            block.removeMetadata("AureliumSkills-Terraform", plugin);
            return;
        }
        TerraformBlockBreakEvent event = new TerraformBlockBreakEvent(block, player);
        Bukkit.getPluginManager().callEvent(event);
        if (!event.isCancelled()) {
            block.breakNaturally(player.getInventory().getItemInMainHand());
        }
        block.removeMetadata("AureliumSkills-Terraform", plugin);
    }

}
