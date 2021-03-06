package magic.model.event;

import java.util.List;

import magic.model.MagicCounterType;
import magic.model.MagicGame;
import magic.model.MagicMessage;
import magic.model.action.ChangeCountersAction;
import magic.model.action.TurnFaceUpAction;

public class MagicMegamorphActivation extends MagicMorphActivation {

    public MagicMegamorphActivation(final List<MagicMatchedCostEvent> aMatchedCostEvents) {
        super(aMatchedCostEvents, "Megamorph");
    }

    @Override
    public void executeEvent(final MagicGame game, final MagicEvent event) {
        game.doAction(new TurnFaceUpAction(event.getPermanent()));
        game.doAction(new ChangeCountersAction(event.getPlayer(), event.getPermanent(), MagicCounterType.PlusOne, 1));
        game.logAppendMessage(
            event.getPlayer(),
            MagicMessage.format("%s turns %s face up and puts a +1/+1 counter on it.", event.getPlayer(), event.getPermanent())
        );
    }
}
