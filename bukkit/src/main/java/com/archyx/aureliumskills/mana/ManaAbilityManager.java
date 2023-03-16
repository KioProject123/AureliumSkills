package com.archyx.aureliumskills.mana;

import com.archyx.aureliumskills.AureliumSkills;
import com.archyx.aureliumskills.api.event.ManaAbilityRefreshEvent;
import com.archyx.aureliumskills.configuration.Option;
import com.archyx.aureliumskills.configuration.OptionL;
import com.archyx.aureliumskills.configuration.OptionValue;
import com.archyx.aureliumskills.data.PlayerData;
import com.archyx.aureliumskills.skills.Skill;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

public class ManaAbilityManager implements Listener {

    private final Map<UUID, EnumMap<MAbility, Integer>> cooldowns;
    private final Map<UUID, EnumMap<MAbility, Boolean>> ready;
    private final Map<UUID, EnumMap<MAbility, Boolean>> activated;
    private final Map<UUID, EnumMap<MAbility, Integer>> errorTimer;

    private final Map<MAbility, ManaAbilityProvider> providers;

    private final AureliumSkills plugin;

    public ManaAbilityManager(AureliumSkills plugin) {
        this.plugin = plugin;
        cooldowns = new HashMap<>();
        ready = new HashMap<>();
        activated = new HashMap<>();
        errorTimer = new HashMap<>();
        providers = new HashMap<>();
    }

    public void init() {
        registerProviders();
        startTimer();
    }

    private void registerProviders() {
        for (MAbility mAbility : MAbility.values()) {
            Class<? extends ManaAbilityProvider> providerClass = mAbility.getProvider();
            if (providerClass != null) {
                try {
                    Constructor<? extends ManaAbilityProvider> constructor = providerClass.getConstructor(AureliumSkills.class);
                    ManaAbilityProvider provider = constructor.newInstance(plugin);
                    register(provider);
                    providers.put(mAbility, provider);
                } catch (NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
                    plugin.getLogger().warning("Failed to register mana ability provider for " + mAbility.toString().toLowerCase(Locale.ROOT));
                }
            }
        }
    }

    private void register(ManaAbilityProvider provider) {
        Bukkit.getPluginManager().registerEvents(provider, plugin);
    }

    @Nullable
    public ManaAbilityProvider getProvider(MAbility mAbility) {
        return providers.get(mAbility);
    }

    public void setActivated(Player player, MAbility mAbility, boolean isActivated) {
        Map<MAbility, Boolean> map = activated.computeIfAbsent(player.getUniqueId(), k -> new EnumMap<>(MAbility.class));
        map.put(mAbility, isActivated);
    }

    //Sets cooldown
    public void setPlayerCooldown(UUID id, MAbility ability, int cooldown) {
        EnumMap<MAbility, Integer> abilityCooldowns = cooldowns.get(id);
        if (abilityCooldowns != null) {
            abilityCooldowns.put(ability, cooldown);
        } else {
            abilityCooldowns = new EnumMap<>(MAbility.class);
            abilityCooldowns.put(ability, cooldown);
            cooldowns.put(id, abilityCooldowns);
        }
    }

    public void setPlayerCooldown(Player player, MAbility mAbility) {
        PlayerData playerData = plugin.getPlayerManager().getPlayerData(player);
        if (playerData != null) {
            double cooldown = getCooldown(mAbility, playerData);
            if (cooldown != 0) {
                setPlayerCooldown(player.getUniqueId(), mAbility, (int) (cooldown * 20));
            }
        }
    }

    //Gets cooldown
    public int getPlayerCooldown(UUID id, MAbility ability) {
        Map<MAbility, Integer> abilityCooldowns = cooldowns.get(id);
        if (abilityCooldowns == null) {
            cooldowns.put(id, new EnumMap<>(MAbility.class));
            return 0;
        }
        Integer cooldown = abilityCooldowns.get(ability);
        if (cooldown != null) {
            return cooldown;
        } else {
            abilityCooldowns.put(ability, 0);
            return 0;
        }
    }

    //Gets if ability is ready
    public boolean isReady(UUID id, MAbility ability) {
        Map<MAbility, Boolean> readyMap = ready.get(id);
        if (readyMap == null) {
            ready.put(id, new EnumMap<>(MAbility.class));
            return false;
        }
        Boolean readyValue = readyMap.get(ability);
        if (readyValue != null) {
            return readyValue;
        } else {
            readyMap.put(ability, false);
            return false;
        }
    }

    //Gets the error timer
    public int getErrorTimer(UUID id, MAbility ability) {
        Map<MAbility, Integer> errorTimers = errorTimer.get(id);
        if (errorTimers == null) {
            errorTimer.put(id, new EnumMap<>(MAbility.class));
            return 0;
        }
        Integer timer = errorTimers.get(ability);
        if (timer != null) {
            return timer;
        } else {
            errorTimers.put(ability, 2);
            return 0;
        }
    }

    //Sets error timer
    public void setErrorTimer(UUID id, MAbility ability, int time) {
        Map<MAbility, Integer> errorTimers = errorTimer.computeIfAbsent(id, k -> new EnumMap<>(MAbility.class));
        errorTimers.put(ability, time);
    }

    //Gets if ability is ready
    public boolean isActivated(UUID id, MAbility ability) {
        Map<MAbility, Boolean> activatedMap = activated.get(id);
        if (activatedMap == null) {
            activated.put(id, new EnumMap<>(MAbility.class));
            return false;
        }
        Boolean activatedValue = activatedMap.get(ability);
        if (activatedValue != null) {
            return activatedValue;
        } else {
            activatedMap.put(ability, false);
            return false;
        }
    }

    //Sets ability ready status
    public void setReady(UUID id, MAbility ability, boolean isReady) {
        Map<MAbility, Boolean> readyMap = ready.computeIfAbsent(id, k -> new EnumMap<>(MAbility.class));
        readyMap.put(ability, isReady);
    }

    private void startTimer() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Map.Entry<UUID, EnumMap<MAbility, Integer>> entry : cooldowns.entrySet()) {
                    UUID id = entry.getKey();
                    Map<MAbility, Integer> abilityCooldowns = entry.getValue();
                    if (abilityCooldowns != null) {
                        for (Map.Entry<MAbility, Integer> entry2 : abilityCooldowns.entrySet()) {
                            MAbility ab = entry2.getKey();
                            int cooldown = entry2.getValue();
                            if (cooldown > 20) {
                                abilityCooldowns.put(ab, cooldown - 20);
                            } else if (cooldown > 0) {
                                abilityCooldowns.put(ab, 0);
                            }
                            if (cooldown > 0 && cooldown <= 20) {
                                PlayerData playerData = plugin.getPlayerManager().getPlayerData(id);
                                if (playerData != null) {
                                    ManaAbilityRefreshEvent event = new ManaAbilityRefreshEvent(playerData.getPlayer(), ab);
                                    Bukkit.getPluginManager().callEvent(event);
                                }
                            }
                        }
                    } else {
                        cooldowns.put(id, new EnumMap<>(MAbility.class));
                    }
                }

                for (Map.Entry<UUID, EnumMap<MAbility, Integer>> entry : errorTimer.entrySet()) {
                    UUID id = entry.getKey();
                    Map<MAbility, Integer> errorTimers = entry.getValue();
                    if (errorTimers != null) {
                        for (Map.Entry<MAbility, Integer> entry2 : errorTimers.entrySet()) {
                            MAbility ab = entry2.getKey();
                            int timer = entry2.getValue();
                            if (timer > 0) {
                                errorTimers.put(ab, timer - 1);
                            }
                        }
                    } else {
                        errorTimer.put(id, new EnumMap<>(MAbility.class));
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        if (!cooldowns.containsKey(id)) {
            cooldowns.put(id, new EnumMap<>(MAbility.class));
        }
        if (!ready.containsKey(id)) {
            ready.put(id, new EnumMap<>(MAbility.class));
        }
        if (!activated.containsKey(id)) {
            activated.put(id, new EnumMap<>(MAbility.class));
        }
        if (!errorTimer.containsKey(id)) {
            errorTimer.put(id, new EnumMap<>(MAbility.class));
        }
    }

    @EventHandler
    public void onLeave(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        // Remove cooldown map from memory if player has no cooldowns
        Map<MAbility, Integer> abilityCooldowns = cooldowns.get(id);
        if (abilityCooldowns != null) {
            boolean noCooldowns = true;
            for (Integer cooldown : abilityCooldowns.values()) {
                if (cooldown != 0) {
                    noCooldowns = false;
                    break;
                }
            }
            if (noCooldowns) {
                cooldowns.remove(id);
            }
        }
        // Remove activated map if player has no active mana abilities
        Map<MAbility, Boolean> activatedMap = activated.get(id);
        if (activatedMap != null) {
            boolean noneActivated = true;
            for (Boolean activated : activatedMap.values()) {
                if (activated) {
                    noneActivated = false;
                    break;
                }
            }
            if (noneActivated) {
                activated.remove(id);
            }
        }
        ready.remove(id);
        errorTimer.remove(id);
    }

    public double getValue(MAbility mAbility, int level) {
        return getBaseValue(mAbility) + (getValuePerLevel(mAbility) * (level - 1));
    }

    public double getValue(MAbility mAbility, PlayerData playerData) {
        return getValue(mAbility, playerData.getManaAbilityLevel(mAbility));
    }

    public double getDisplayValue(MAbility mAbility, int level) {
        if (mAbility == MAbility.SHARP_HOOK && getOptionAsBooleanElseTrue(mAbility, "display_damage_with_scaling")) {
            return getValue(mAbility, level) * OptionL.getDouble(Option.HEALTH_HP_INDICATOR_SCALING);
        } else {
            return getValue(mAbility, level);
        }
    }

    public double getBaseValue(MAbility mAbility) {
        ManaAbilityOption option = plugin.getAbilityManager().getAbilityOption(mAbility);
        if (option != null) {
            return option.getBaseValue();
        }
        return mAbility.getDefaultBaseValue();
    }

    public double getValuePerLevel(MAbility mAbility) {
        ManaAbilityOption option = plugin.getAbilityManager().getAbilityOption(mAbility);
        if (option != null) {
            return option.getValuePerLevel();
        }
        return mAbility.getDefaultValuePerLevel();
    }

    public double getCooldown(MAbility mAbility, int level) {
        double cooldown = getBaseCooldown(mAbility) + (getCooldownPerLevel(mAbility) * (level - 1));
        return cooldown > 0 ? cooldown : 0;
    }

    public double getCooldown(MAbility mAbility, PlayerData playerData) {
        double cooldown = getBaseCooldown(mAbility) + (getCooldownPerLevel(mAbility) * (playerData.getManaAbilityLevel(mAbility) - 1));
        return cooldown > 0 ? cooldown : 0;
    }

    public double getBaseCooldown(MAbility mAbility) {
        ManaAbilityOption option = plugin.getAbilityManager().getAbilityOption(mAbility);
        if (option != null) {
            return option.getBaseCooldown();
        }
        return mAbility.getDefaultBaseCooldown();
    }

    public double getCooldownPerLevel(MAbility mAbility) {
        ManaAbilityOption option = plugin.getAbilityManager().getAbilityOption(mAbility);
        if (option != null) {
            return option.getCooldownPerLevel();
        }
        return mAbility.getDefaultCooldownPerLevel();
    }

    public double getManaCost(MAbility mAbility, PlayerData playerData) {
        return getBaseManaCost(mAbility) + (getManaCostPerLevel(mAbility) * (playerData.getManaAbilityLevel(mAbility) - 1));
    }

    public double getManaCost(MAbility mAbility, int level) {
        return getBaseManaCost(mAbility) + (getManaCostPerLevel(mAbility) * (level - 1));
    }

    public double getBaseManaCost(MAbility mAbility) {
        ManaAbilityOption option = plugin.getAbilityManager().getAbilityOption(mAbility);
        if (option != null) {
            return option.getBaseManaCost();
        }
        return mAbility.getDefaultBaseManaCost();
    }

    public double getManaCostPerLevel(MAbility mAbility) {
        ManaAbilityOption option = plugin.getAbilityManager().getAbilityOption(mAbility);
        if (option != null) {
            return option.getManaCostPerLevel();
        }
        return mAbility.getDefaultManaCostPerLevel();
    }

    public int getUnlock(MAbility mAbility) {
        ManaAbilityOption option = plugin.getAbilityManager().getAbilityOption(mAbility);
        if (option != null) {
            return option.getUnlock();
        }
        return 7;
    }

    public int getLevelUp(MAbility mAbility) {
        ManaAbilityOption option = plugin.getAbilityManager().getAbilityOption(mAbility);
        if (option != null) {
            return option.getLevelUp();
        }
        return 7;
    }

    public int getMaxLevel(MAbility mAbility) {
        ManaAbilityOption option = plugin.getAbilityManager().getAbilityOption(mAbility);
        if (option != null) {
            return option.getMaxLevel();
        }
        return 0;
    }

    /**
     * Gets the mana ability unlocked or leveled up at a certain level
     * @param skill The skill
     * @param level The skill level
     * @return The mana ability unlocked or leveled up, or null
     */
    @Nullable
    public MAbility getManaAbility(Skill skill, int level) {
        MAbility mAbility = skill.getManaAbility();
        if (mAbility != null) {
            if (level >= getUnlock(mAbility) && (level - getUnlock(mAbility)) % getLevelUp(mAbility) == 0) {
                return mAbility;
            }
        }
        return null;
    }

    @Nullable
    public OptionValue getOption(MAbility mAbility, String key) {
        ManaAbilityOption option = plugin.getAbilityManager().getAbilityOption(mAbility);
        if (option != null) {
            return option.getOption(key);
        } else {
            return mAbility.getDefaultOptions().get(key);
        }
    }

    public boolean getOptionAsBooleanElseTrue(MAbility mAbility, String key) {
        OptionValue value = getOption(mAbility, key);
        if (value != null) {
            return value.asBoolean();
        }
        return true;
    }

    public boolean getOptionAsBooleanElseFalse(MAbility mAbility, String key) {
        OptionValue value = getOption(mAbility, key);
        if (value != null) {
            return value.asBoolean();
        }
        return false;
    }

    public int getOptionAsInt(MAbility mAbility, String key, int defaultValue) {
        OptionValue value = getOption(mAbility, key);
        if (value != null) {
            return value.asInt();
        }
        return defaultValue;
    }

    public double getOptionAsDouble(MAbility mAbility, String key) {
        OptionValue value = getOption(mAbility, key);
        if (value != null) {
            return value.asDouble();
        }
        return mAbility.getDefaultOptions().get(key).asDouble();
    }

    @Nullable
    public Set<String> getOptionKeys(MAbility mAbility) {
        if (mAbility.getDefaultOptions() != null) {
            return mAbility.getDefaultOptions().keySet();
        }
        return null;
    }

}
