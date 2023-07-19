package com.archyx.aureliumskills.skills.mining;

import com.archyx.aureliumskills.AureliumSkills;
import com.archyx.aureliumskills.ability.Ability;
import com.archyx.aureliumskills.configuration.Option;
import com.archyx.aureliumskills.configuration.OptionL;
import com.archyx.aureliumskills.leveler.SkillLeveler;
import com.archyx.aureliumskills.skills.Skills;
import com.archyx.aureliumskills.source.SourceTag;
import com.archyx.aureliumskills.util.version.VersionUtils;
import com.cryptomorin.xseries.XMaterial;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;


public class MiningLeveler extends SkillLeveler implements Listener {

	private final MiningAbilities miningAbilities;

	public MiningLeveler(AureliumSkills plugin) {
		super(plugin, Ability.MINER);
		this.miningAbilities = new MiningAbilities(plugin);
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onBlockBreak(BlockBreakEvent event) {
		if (OptionL.isEnabled(Skills.MINING)) {
			// Check cancelled
			if (OptionL.getBoolean(Option.MINING_CHECK_CANCELLED) && event.isCancelled()) {
				return;
			}
			Block block = event.getBlock();
			// KioCG start
			if (isOre(block.getType())) {
				if (hasSilkTouch(event.getPlayer())) {
					return;
				}
			} else {
			// Check block replace
			if (OptionL.getBoolean(Option.CHECK_BLOCK_REPLACE) && plugin.getRegionManager().isPlacedBlock(block)) {
				return;
			}
			}
			// KioCG end

			Player player = event.getPlayer();
			if (blockXpGainLocation(block.getLocation(), player, Skills.MINING)) return;
			if (blockXpGainPlayer(player)) return;

			// Search through sources until a match is found for the block broken
			for (MiningSource source : MiningSource.values()) {
				// Add XP to player if matched
				if (!source.isMatch(block)) continue;
				// Check silk touch
				if (source.requiresSilkTouch() && !hasSilkTouch(player)) {
					return;
				}
				plugin.getLeveler().addXp(player, Skills.MINING, getAbilityXp(player, source));
				// Apply abilities if has tag
				if (hasTag(source, SourceTag.LUCKY_MINER_APPLICABLE) && event.isDropItems()) {
					miningAbilities.luckyMiner(player, block, source);
				}
				break; // Stop searching if matched
			}
			// Check custom blocks
			checkCustomBlocks(player, block, Skills.MINING);
		}
	}

	// KioCG start
	static boolean isOre(Material mat) {
		if (VersionUtils.isAtLeastVersion(17)) {
			return mat.name().endsWith("_ORE");
		} else {
			if (mat.equals(Material.DIAMOND_ORE) || mat.equals(Material.LAPIS_ORE) ||
				mat.equals(Material.REDSTONE_ORE) || mat.name().equals("GLOWING_REDSTONE_ORE") ||
				mat.equals(Material.EMERALD_ORE) || mat.equals(Material.COAL_ORE) ||
				mat.equals(XMaterial.NETHER_QUARTZ_ORE.parseMaterial()) || mat.equals(XMaterial.NETHER_GOLD_ORE.parseMaterial())) {
				return true;
			}
			return false;
		}
	}
	// KioCG end
}
