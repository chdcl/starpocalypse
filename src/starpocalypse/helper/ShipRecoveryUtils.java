package starpocalypse.helper;

import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import lombok.extern.log4j.Log4j;

@Log4j
public class ShipRecoveryUtils {
    /** Returns true if the ship should cost a story point to recover, using the config for player ships */
    public static boolean isStingyRecovery(ShipVariantAPI shipVariant) {
        // Determine stingy chance based on ship size and setting. 1 = always cost SP, 0 = never cost SP.
        float stingyChance = 0f;
        if (ConfigHelper.isStingyRecoveriesIncludePlayerShips()) {
            if (shipVariant.getHullSize() == ShipAPI.HullSize.FRIGATE) {
                stingyChance = ConfigHelper.getStingyRecoveriesCombatPlayerChanceFrigate();
            } else if (shipVariant.getHullSize() == ShipAPI.HullSize.DESTROYER) {
                stingyChance = ConfigHelper.getStingyRecoveriesCombatPlayerChanceDestroyer();
            } else if (shipVariant.getHullSize() == ShipAPI.HullSize.CRUISER) {
                stingyChance = ConfigHelper.getStingyRecoveriesCombatPlayerChanceCruiser();
            } else if (shipVariant.getHullSize() == ShipAPI.HullSize.CAPITAL_SHIP) {
                stingyChance = ConfigHelper.getStingyRecoveriesCombatPlayerChanceCapital();
            }
        }

        if (stingyChance >= 1f) {
            return true;
        }

        if (stingyChance <= 0f) {
            return false;
        }

        // If lower roll than stingy chance, the ship should cost a story point to recover
        return Math.random() <= stingyChance;
    }
}
