package magic.headless;

import magic.exception.UndoClickedException;
import magic.ai.MagicAI;
import magic.model.ILogBookListener;
import magic.model.MagicCardList;
import magic.model.MagicGame;
import magic.model.MagicLogBookEvent;
import magic.model.MagicPlayer;
import magic.model.MagicSource;
import magic.model.event.MagicEvent;
import magic.model.event.MagicPriorityEvent;
import magic.model.target.MagicTarget;
import magic.model.target.MagicTargetNone;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import magic.model.IGameController;
import magic.model.MagicColor;
import magic.model.MagicManaCost;
import magic.model.MagicSubType;
import magic.model.choice.MagicPlayChoiceResult;

public class HeadlessGameController implements IGameController {

    private long MAX_TEST_MODE_DURATION=10000;
   
    private final MagicGame game;
    private final AtomicBoolean running = new AtomicBoolean(false);
    
    /** Fully artificial test game. */
    public HeadlessGameController(final MagicGame aGame) {
        game = aGame;
    }

    public void setMaxTestGameDuration(final long duration) {
        MAX_TEST_MODE_DURATION = duration;
    }

    private Object[] getArtificialNextEventChoiceResults(final MagicEvent event) {
        //dynamically get the AI based on the player's index
        final MagicPlayer player = event.getPlayer();
        final MagicAI ai = game.getDuel().getAIs()[player.getIndex()];
        return ai.findNextEventChoiceResults(game, player);
    }

    private void executeNextEventWithChoices(final MagicEvent event) {
        game.executeNextEvent(getArtificialNextEventChoiceResults(event));
    }

    /**
     * Main game loop runs on separate thread.
     */
    @Override
    public void runGame() {
        final long startTime=System.currentTimeMillis();
        running.set(true);
        while (running.get()) {
            if (game.isFinished()) {
                game.advanceDuel();
                running.set(false);
            } else {
                executeNextEventOrPhase();
                if (System.currentTimeMillis() - startTime > MAX_TEST_MODE_DURATION) {
                    System.err.println("WARNING. Max time for AI game exceeded");
                    running.set(false);
                }
            }
        }
    }
  
    @Override
    public void haltGame() {
        running.set(false);
    }

    private void executeNextEventOrPhase() {
        if (game.hasNextEvent()) {
            executeNextEvent();
        } else {
            game.executePhase();
        }
    }

    private void executeNextEvent() {
        final MagicEvent event=game.getNextEvent();
        if (event instanceof MagicPriorityEvent) {
            game.logMessages();
        }
        if (event.hasChoice()) {
            executeNextEventWithChoices(event);
        } else {
            game.executeNextEvent();
        }
    }
    
    @Override
    public void showMessage(final MagicSource source, final String message) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void enableForwardButton() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void disableActionButton(final boolean thinking) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void pause(final int t) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void waitForInput() throws UndoClickedException {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isActionClicked() {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> T getChoiceClicked() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setSourceCardDefinition(final MagicSource source) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void focusViewers(final int handGraveyard) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clearCards() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void showCards(final MagicCardList cards) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clearValidChoices() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setValidChoices(final Set<?> aValidChoices,final boolean aCombatChoice) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateGameView() {
        throw new UnsupportedOperationException();
    }

    @Override
    public MagicSubType getLandSubTypeChoice(final MagicSource source) throws UndoClickedException {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean getPayBuyBackCostChoice(final MagicSource source, final String costText) throws UndoClickedException {
        throw new UnsupportedOperationException();
    }

    @Override
    public MagicColor getColorChoice(final MagicSource source) throws UndoClickedException {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getMultiKickerCountChoice(
            final MagicSource source,
            final MagicManaCost cost,
            final int maximumCount,
            final String name) throws UndoClickedException {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getSingleKickerCountChoice(
            final MagicSource source,
            final MagicManaCost cost,
            final String name) throws UndoClickedException {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean getMayChoice(final MagicSource source, final String description) throws UndoClickedException {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean getTakeMulliganChoice(
            final MagicSource source,
            final MagicPlayer player) throws UndoClickedException {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getModeChoice(final MagicSource source, final List<Integer> availableModes) throws UndoClickedException {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getPayManaCostXChoice(final MagicSource source, final int maximumX) throws UndoClickedException {
        throw new UnsupportedOperationException();
    }

    @Override
    public MagicPlayChoiceResult getPlayChoice(final MagicSource source, final List<MagicPlayChoiceResult> results) throws UndoClickedException {
        throw new UnsupportedOperationException();
    }
}
