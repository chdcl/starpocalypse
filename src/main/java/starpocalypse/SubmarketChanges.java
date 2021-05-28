package starpocalypse;

import java.util.Random;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.FleetDataAPI;
import com.fs.starfarer.api.campaign.PlayerMarketTransaction;
import com.fs.starfarer.api.campaign.SubmarketPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.campaign.listeners.ColonyInteractionListener;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.DModManager;
import com.fs.starfarer.api.impl.campaign.ids.HullMods;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;

import lombok.extern.log4j.Log4j;
import starpocalypse.settings.Whitelist;

@Log4j
public class SubmarketChanges implements ColonyInteractionListener {

    private Whitelist whitelist = new Whitelist("submarketWhitelist.csv");

    public SubmarketChanges() {
        Global.getSector().getListenerManager().addListener(this, true);
    }

    @Override
    public void reportPlayerOpenedMarket(MarketAPI market) {
        log.info("Processing market " + market.getName());
        if (!canModify(market)) {
            return;
        }
        processSubmarkets(market);
    }

    @Override
    public void reportPlayerClosedMarket(MarketAPI market) {
    }

    @Override
    public void reportPlayerOpenedMarketAndCargoUpdated(MarketAPI market) {
    }

    @Override
    public void reportPlayerMarketTransaction(PlayerMarketTransaction transaction) {
    }

    private boolean canModify(MarketAPI market) {
        if (!whitelist.has(market.getFactionId())) {
            log.info("> Skipping non-whitelisted market");
            return false;
        }
        if (market.isHidden()) {
            log.info("> Skipping hidden market");
            return false;
        }
        return true;
    }

    private void processSubmarkets(MarketAPI market) {
        for (SubmarketAPI submarket : market.getSubmarketsCopy()) {
            log.info("> Processing submarket " + submarket.getNameOneLine());
            processSubmarket(submarket);
        }
    }

    private void processSubmarket(SubmarketAPI submarket) {
        if (!canModify(submarket)) {
            log.info(">> Skipping submarket");
            return;
        }
        log.info(">> Modifying submarket");
        SubmarketAPI militaryMarket = submarket.getMarket().getSubmarket(Submarkets.GENERIC_MILITARY);
        clearCargo(militaryMarket, submarket);
        clearShips(submarket);
    }

    private boolean canModify(SubmarketAPI submarket) {
        SubmarketPlugin plugin = submarket.getPlugin();
        plugin.updateCargoPrePlayerInteraction();
        return plugin.isOpenMarket() || plugin.isBlackMarket();
    }

    private void clearCargo(SubmarketAPI militaryMarket, SubmarketAPI submarket) {
        CargoAPI cargo = submarket.getCargo();
        for (CargoStackAPI stack : cargo.getStacksCopy()) {
            if (isInvalid(stack)) {
                if (militaryMarket != null) {
                    log.info(">> Moving to military " + stack.getDisplayName());
                    militaryMarket.getCargo().addFromStack(stack);
                } else {
                    log.info(">> Removing " + stack.getDisplayName());
                }
                cargo.removeStack(stack);
            }
        }
    }

    private void clearShips(SubmarketAPI submarket) {
        CargoAPI cargo = submarket.getCargo();
        FleetDataAPI ships = cargo.getMothballedShips();
        for (FleetMemberAPI ship : ships.getMembersListCopy()) {
            removeOrBreak(ships, ship);
        }
    }

    private boolean isInvalid(CargoStackAPI stack) {
        return stack.isWeaponStack() || stack.isFighterWingStack();
    }

    private boolean isInvalid(FleetMemberAPI fleetMember) {
        return !fleetMember.getVariant().hasHullMod(HullMods.CIVGRADE);
    }

    private void removeOrBreak(FleetDataAPI ships, FleetMemberAPI ship) {
        if (isInvalid(ship)) {
            log.info(">> Removing " + ship.getHullSpec().getHullName());
            ships.removeFleetMember(ship);
            return;
        }
        if (!ship.getVariant().isDHull()) {
            log.info(">> Damaging " + ship.getHullSpec().getHullName());
            DModManager.addDMods(ship, false, 2, new Random());
        }
    }
}