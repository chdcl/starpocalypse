package starpocalypse.salvage;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.MutableCharacterStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.impl.campaign.RepairGantry;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial;
import starpocalypse.helper.ConfigHelper;

import java.util.Arrays;
import java.util.List;

public class DerelictModifyingScript implements EveryFrameScript {
    private final String DERELICT_PROCESSED = "STARPOCALYPSE_DERELICT_PROCESSED";

    @Override
    public boolean isDone() {
        return false;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        for (SectorEntityToken entity : getEntities(Tags.DEBRIS_FIELD)) {
            forceStoryPointRecovery(entity);
        }
        for (SectorEntityToken entity : getEntities(Tags.SALVAGEABLE)) {
            forceStoryPointRecovery(entity);
        }
    }

    private List<SectorEntityToken> getEntities(String tag) {
        return Global.getSector().getPlayerFleet().getContainingLocation().getEntitiesWithTag(tag);
    }

    /**
     * 10% base chance, plus 10% to 20% with skill depending on industry/technology aptitude, plus 10% with a single
     * salvage rig or ~20% with three salvage rigs
     */
    private float getDerelictRecoveryChance() {
        // Logic taken from FleetEncounterContext impl
        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
        // This is the salvage skill, maybe there are more sources in mods. Has a base of 1, subtract to get the bonus
        float salvageBonusSkill = playerFleet.getStats().getDynamic().getValue(Stats.BATTLE_SALVAGE_MULT_FLEET) - 1;
        // This is salvage gantries and similar mods (e.g. LOST_SECTOR nskr_rogue_co). Is a value slightly above 0
        float salvageBonusGantry = RepairGantry.getAdjustedGantryModifierForPostCombatSalvage(playerFleet);

        // Salvage skill starts at 50% effectiveness (giving a 10% bonus), up to 100% effectiveness (20% bonus) with
        // 5 points in technology or industry
        float numIndustryAndTechnologySkills = getNumAptitudeSkills(Arrays.asList("industry", "technology"));
        // Min 50% effectiveness with 1 skill, max 100% at 5 skills or more.  0.5 + ((5 - 1) / 8) == 1.0
        float salvageBonusSkillFactor = 0.5f + Math.min(numIndustryAndTechnologySkills - 1, 4) / 8f;

        // Double salvage gantry bonus, this is a very low value otherwise - 5% for 1 rig, ~10% for 3 rigs, ~18% for 10
        float salvageBonusGantryFactor = 2f;

        // Base 10% chance to recover a frigate with no skills or salvage gantries
        float baseChance = 0.1f;

        return baseChance + (salvageBonusSkill * salvageBonusSkillFactor) +
               (salvageBonusGantry * salvageBonusGantryFactor);
    }

    private float getDerelictRecoveryChanceSizeFactor(ShipAPI.HullSize size) {
        // God, I wish there was an easier way to do this (that isn't relying on enum ordinal ordering)
        switch (size) {
            case FRIGATE:
                return 1f;
            case DESTROYER:
                return 0.75f;
            case CRUISER:
                return 0.5f;
            case CAPITAL_SHIP:
                return 0.25f;
            default:
                return 0f; // Should never happen
        }
    }

    private ShipVariantAPI getShipRecoverySpecialDataShipVariant(ShipRecoverySpecial.ShipRecoverySpecialData data) {
        // Try / catch block because ShipRecoverySpecialData is internal and sometimes behaves weirdly
        try {
            // This only really makes sense if the derelict entity has a single ship
            if (data.ships.size() == 1) {
                ShipRecoverySpecial.PerShipData shipData = data.ships.get(0);
                ShipVariantAPI variant = shipData.variant;
                // Sometimes shipData.variant is null, need to parse from variantId
                if (variant == null) {
                    variant = Global.getSettings().getVariant(shipData.variantId);
                }
                return variant;
            }
        } catch (Exception ignored) {}

        return null;
    }

    private void forceStoryPointRecovery(SectorEntityToken entity) {
        // Only process a derelict object once, otherwise it would get rerolled every tick
        if (entity.hasTag(DERELICT_PROCESSED)) {
            return;
        }
        entity.addTag(DERELICT_PROCESSED);

        MemoryAPI memory = entity.getMemoryWithoutUpdate();
        if (memory.contains(MemFlags.SALVAGE_SPECIAL_DATA)) {
            Object specialData = memory.get(MemFlags.SALVAGE_SPECIAL_DATA);
            if (specialData instanceof ShipRecoverySpecial.ShipRecoverySpecialData) {
                ShipRecoverySpecial.ShipRecoverySpecialData data =
                        (ShipRecoverySpecial.ShipRecoverySpecialData) specialData;
                // If the recovery already costs a story point, don't modify it
                if (data.storyPointRecovery != null && data.storyPointRecovery) {
                    return;
                }

                // By default, make it cost a story point
                data.storyPointRecovery = true;

                // If setting is enabled, roll for easy recovery
                if (ConfigHelper.isStingyRecoveriesDerelictsUseSalvageBonus()) {
                    ShipVariantAPI variant = getShipRecoverySpecialDataShipVariant(data);
                    if (variant == null) {
                        // Failed to determine ship variant
                        return;
                    }

                    // Base of 10% for frigates, increased with salvage skill and salvage gantries
                    float recoveryChance = getDerelictRecoveryChance();
                    recoveryChance *= getDerelictRecoveryChanceSizeFactor(variant.getHullSize());
                    if (Math.random() < recoveryChance) {
                        data.storyPointRecovery = false;
                    }
                }
            }
        }
    }

    /** Returns the number of skills matching the specified aptitude(s) */
    private static float getNumAptitudeSkills(List<String> aptitudes) {
        float numMatchingSkills = 0f;
        for (MutableCharacterStatsAPI.SkillLevelAPI skill : Global.getSector().getPlayerStats().getSkillsCopy()) {
            if (aptitudes.contains(skill.getSkill().getGoverningAptitudeId())) {
                numMatchingSkills += 1f;
            }
        }
        return numMatchingSkills;
    }
}
