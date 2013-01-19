package magic.card;

import magic.model.MagicGame;
import magic.model.MagicPermanent;
import magic.model.MagicPowerToughness;
import magic.model.mstatic.MagicLayer;
import magic.model.MagicAbility;
import magic.model.mstatic.MagicStatic;
import magic.model.target.MagicTargetFilter;

import java.util.Set;

public class Master_of_the_Pearl_Trident {
    public static final MagicStatic S2 = new MagicStatic(
        MagicLayer.Ability,
        MagicTargetFilter.TARGET_MERFOLK_YOU_CONTROL) {
        @Override
        public void modAbilityFlags(final MagicPermanent source,final MagicPermanent permanent,final Set<MagicAbility> flags) {
            flags.add(MagicAbility.Islandwalk);
        }
        @Override
        public boolean condition(final MagicGame game, final MagicPermanent source,final MagicPermanent target) {
            return source != target;
        }
    };

    public static final MagicStatic S1 = new MagicStatic(
        MagicLayer.ModPT,
        MagicTargetFilter.TARGET_MERFOLK_YOU_CONTROL) {
        @Override
        public void modPowerToughness(final MagicPermanent source,final MagicPermanent permanent,final MagicPowerToughness pt) {
            pt.add(1,1);
        }
        @Override
        public boolean condition(final MagicGame game, final MagicPermanent source,final MagicPermanent target) {
            return source != target;
        }
    };
}
