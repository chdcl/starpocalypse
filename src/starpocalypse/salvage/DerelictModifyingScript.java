package starpocalypse.salvage;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial;
import starpocalypse.helper.ConfigHelper;
import starpocalypse.helper.ShipRecoveryUtils;

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

    private void forceStoryPointRecovery(SectorEntityToken entity) {
        // Only process derelict objects once
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

                // If stingyRecoveriesDerelictsUsePlayerChance is true, try to apply disabled player ship recovery logic
                if (ConfigHelper.isStingyRecoveriesDerelictsUsePlayerChance()) {
                    // Try / catch block because ShipRecoverySpecialData is internal and sometimes behaves weirdly
                    try {
                        // This only really makes sense if the derelict entity has a single ship
                        if (data.ships.size() == 1) {
                            ShipRecoverySpecial.PerShipData shipData = data.ships.get(0);
                            ShipVariantAPI shipVariant = shipData.variant;
                            // Sometimes shipData.variant is null, need to parse from variantId
                            if (shipVariant == null) {
                                shipVariant = Global.getSettings().getVariant(shipData.variantId);
                            }

                            // Minimum 50% chance to cost a story point, no matter the ship size and config
                            // Otherwise, stingy recovery chance has weird quadratic scaling where a low chance
                            // results in both increased new ships from derelicts and decreased combat losses
                            final float stingyRecoveryMinChance = 0.5f;

                            // Determine if the recover should cost an SP, based on hull size
                            data.storyPointRecovery = ShipRecoveryUtils.isStingyRecovery(shipVariant,
                                    stingyRecoveryMinChance);
                        }
                    } catch (Exception ignored) {
                        // Something went wrong with parsing the ship type, so it will just keep costing a story point
                    }
                }
            }
        }
    }
}
