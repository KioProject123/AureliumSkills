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
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

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
            if (isHoldingMaterial(player)) terraformBreak(player, block);
            return;
        }
        // Checks if ability is ready
        if (isReady(player) && isHoldingMaterial(player) && hasEnoughMana(player)) {
            activate(player);
            terraformBreak(player, block);
        }
    }

    private void terraformBreak(Player player, Block block) {
        Material material = block.getType();
//        BlockFace[] faces = new BlockFace[] {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN};
//        LinkedList<Block> toCheck = new LinkedList<>();
//        toCheck.add(block);
//        int count = 0;
//        int maxCount = plugin.getManaAbilityManager().getOptionAsInt(MAbility.TERRAFORM, "max_blocks", 61);
//        while ((block = toCheck.poll()) != null && count < maxCount) {
        for (Block block0 : getBlocks(block)) {
            block = block0;
            if (block.getType() == material) {
                block.setMetadata("AureliumSkills-Terraform", new FixedMetadataValue(plugin, true));
                breakBlock(player, block);

                // KioCG start
                ItemStack itemInMainHand = player.getInventory().getItemInMainHand();
                Damageable damageable = (Damageable) itemInMainHand.getItemMeta();
                if (damageable.getDamage() >= itemInMainHand.getType().getMaxDurability()) {
                    return;
                }
                // KioCG end

//                for (BlockFace face : faces) {
//                    toCheck.add(block.getRelative(face));
//                }
//                count++;
            }
        }
    }

    // KioCG start
    private Set<Block> getBlocks(final Block source) {
        final Set<Block> blocks = new HashSet<>();
        blocks.add(source.getRelative(BlockFace.WEST));
        blocks.add(source.getRelative(BlockFace.EAST));
        blocks.add(source.getRelative(BlockFace.DOWN));
        blocks.add(source.getRelative(BlockFace.UP));
        blocks.add(source.getRelative(BlockFace.NORTH));
        blocks.add(source.getRelative(BlockFace.SOUTH));
        return blocks;
    }
    // KioCG end

    private void breakBlock(Player player, Block block) {
        if (!plugin.getTownySupport().canBreak(player, block)) {
            block.removeMetadata("AureliumSkills-Terraform", plugin);
            return;
        }
        TerraformBlockBreakEvent event = new TerraformBlockBreakEvent(block, player);
        Bukkit.getPluginManager().callEvent(event);
        if (!event.isCancelled()) {
            block.breakNaturally(player.getInventory().getItemInMainHand());
            player.damageItemStack(player.getInventory().getItemInMainHand(), 1); // KioCG
        }
        block.removeMetadata("AureliumSkills-Terraform", plugin);
    }

}
