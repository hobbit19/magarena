package magic.card;

import magic.model.MagicGame;
import magic.model.MagicLocationType;
import magic.model.MagicManaCost;
import magic.model.MagicPayedCost;
import magic.model.MagicPermanent;
import magic.model.MagicSource;
import magic.model.MagicSubType;
import magic.model.action.MagicPermanentAction;
import magic.model.action.MagicRemoveFromPlayAction;
import magic.model.action.MagicTapAction;
import magic.model.choice.MagicTargetChoice;
import magic.model.condition.MagicCondition;
import magic.model.event.MagicActivationHints;
import magic.model.event.MagicEvent;
import magic.model.event.MagicPayManaCostTapEvent;
import magic.model.event.MagicPermanentActivation;
import magic.model.event.MagicTiming;
import magic.model.target.MagicTapTargetPicker;

public class Holy_Justiciar {
    public static final MagicPermanentActivation A = new MagicPermanentActivation(
        new MagicCondition[]{
            MagicCondition.CAN_TAP_CONDITION,
            MagicManaCost.TWO_WHITE.getCondition()
        },
        new MagicActivationHints(MagicTiming.Tapping),
        "Tap"
    ) {

        @Override
        public MagicEvent[] getCostEvent(final MagicSource source) {
            return new MagicEvent[]{
                new MagicPayManaCostTapEvent(
                    source,
                    source.getController(),
                    MagicManaCost.TWO_WHITE
                )
            };
        }

        @Override
        public MagicEvent getPermanentEvent(
                final MagicPermanent source,
                final MagicPayedCost payedCost) {
            return new MagicEvent(
                source,
                MagicTargetChoice.NEG_TARGET_CREATURE,
                new MagicTapTargetPicker(true,false),
                this,
                "Tap target creature$. If that creature is a Zombie, exile it."
            );
        }

        @Override
        public void executeEvent(
                final MagicGame game,
                final MagicEvent event,
                final Object[] data,
                final Object[] choiceResults) {
            event.processTargetPermanent(game,choiceResults,0,new MagicPermanentAction() {
                public void doAction(final MagicPermanent creature) {
                    game.doAction(new MagicTapAction(creature,true));
                    if (creature.hasSubType(MagicSubType.Zombie)) {
                        game.doAction(new MagicRemoveFromPlayAction(
                                creature,
                                MagicLocationType.Exile));
                    }
                }
            });
        }
    };
}
