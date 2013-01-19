package magic.card;

import magic.model.MagicAbility;
import magic.model.MagicPermanent;
import magic.model.MagicSubType;
import magic.model.mstatic.MagicLayer;
import magic.model.mstatic.MagicStatic;

import java.util.Set;

public class Night_Revelers {
    public static final MagicStatic S = new MagicStatic(MagicLayer.Ability) {
        @Override
        public void modAbilityFlags(final MagicPermanent source,final MagicPermanent permanent,final Set<MagicAbility> flags) {
            for (final MagicPermanent target : permanent.getOpponent().getPermanents()) {
                if (target.hasSubType(MagicSubType.Human)) {
                    flags.add(MagicAbility.Haste);
                    break;
                }
            }
        }
    };
}
