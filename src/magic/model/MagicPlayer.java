package magic.model;

import magic.data.CardDefinitions;
import magic.model.choice.MagicBuilderManaCost;
import magic.model.event.MagicActivationPriority;
import magic.model.event.MagicSourceManaActivation;
import magic.model.event.MagicSourceActivation;
import magic.model.target.MagicTarget;
import magic.model.target.MagicTargetFilter;
import magic.model.action.MagicLoseGameAction;
import magic.model.mstatic.MagicLayer;
import magic.model.mstatic.MagicStatic;
import magic.model.mstatic.MagicPermanentStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class MagicPlayer implements MagicTarget {

    public static final MagicPlayer NONE = new MagicPlayer(-1, new MagicPlayerDefinition(), -1) {
        @Override
        public String toString() {
            return "";
        }
        @Override
        public boolean controlsPermanent(final MagicPermanent permanent) {
            return false;
        }
        @Override
        public boolean isValid() {
            return false;
        }
        @Override
        public MagicPlayer copy(final MagicCopyMap copyMap) {
            return this;
        }
        @Override
        public MagicGame getGame() {
            throw new RuntimeException("getGame called for MagicPlayer.NONE");
        }
        @Override
        public MagicPlayer getOpponent() {
            return this;
        }
    };

    private static final int LOSING_POISON=10;
    private static final long ID_FACTOR=31;

    private final MagicPlayerDefinition playerDefinition;
    private final int index;

    private int life;
    private int stateFlags;
    private int poison;
    private int preventDamage;
    private int extraTurns;
    private int drawnCards;
    private int maxHandSize;
    private final MagicCardList hand;
    private final MagicCardList library;
    private final MagicCardList graveyard;
    private final MagicCardList exile;
    private final MagicPermanentSet permanents;
    private final MagicPermanentSet manaPermanents;
    private MagicGame currGame;
    private MagicBuilderManaCost builderCost;
    private MagicActivationPriority activationPriority;
    private Set<MagicAbility> cachedAbilityFlags;

    private long[] keys;

    MagicPlayer(final int aLife,final MagicPlayerDefinition aPlayerDefinition,final int aIndex) {
        playerDefinition = aPlayerDefinition;
        index = aIndex;
        life = aLife;

        hand=new MagicCardList();
        library=new MagicCardList();
        graveyard=new MagicCardList();
        exile=new MagicCardList();
        permanents=new MagicPermanentSet();
        manaPermanents=new MagicPermanentSet();
        builderCost=new MagicBuilderManaCost();
        activationPriority=new MagicActivationPriority();
    }

    private MagicPlayer(final MagicCopyMap copyMap, final MagicPlayer sourcePlayer) {
        copyMap.put(sourcePlayer, this);

        playerDefinition = sourcePlayer.playerDefinition;
        index = sourcePlayer.index;
        life = sourcePlayer.life;
        poison=sourcePlayer.poison;
        stateFlags=sourcePlayer.stateFlags;
        preventDamage=sourcePlayer.preventDamage;
        extraTurns=sourcePlayer.extraTurns;
        drawnCards=sourcePlayer.drawnCards;
        maxHandSize=sourcePlayer.maxHandSize;
        hand=new MagicCardList(copyMap, sourcePlayer.hand);
        library=new MagicCardList(copyMap, sourcePlayer.library);
        graveyard=new MagicCardList(copyMap, sourcePlayer.graveyard);
        exile=new MagicCardList(copyMap, sourcePlayer.exile);
        permanents=new MagicPermanentSet(copyMap,sourcePlayer.permanents);
        manaPermanents=new MagicPermanentSet(copyMap,sourcePlayer.manaPermanents);
        builderCost=new MagicBuilderManaCost(sourcePlayer.builderCost);
        activationPriority=new MagicActivationPriority(sourcePlayer.activationPriority);
        cachedAbilityFlags=sourcePlayer.cachedAbilityFlags;
    }

    @Override
    public MagicPlayer copy(final MagicCopyMap copyMap) {
        return new MagicPlayer(copyMap, this);
    }

    @Override
    public MagicPlayer map(final MagicGame game) {
        return game.getPlayer(index);
    }

    public void setGame(final MagicGame game) {
        currGame = game;
    }

    public MagicGame getGame() {
        return currGame;
    }

    public long getStateId() {
        keys = new long[] {
            life,
            poison,
            stateFlags,
            preventDamage,
            extraTurns,
            drawnCards,
            maxHandSize,
            hand.getUnorderedStateId(),
            library.getStateId(),
            graveyard.getStateId(),
            exile.getUnorderedStateId(),
            permanents.getStateId(),
            builderCost.getMinimumAmount(),
            activationPriority.getPriority(),
            activationPriority.getActivationId(),
            cachedAbilityFlags.hashCode()
        };
        return magic.MurmurHash3.hash(keys);
    }

    String getIdString() {
        final StringBuilder sb = new StringBuilder();
        sb.append(keys[0]);
        for (int i = 1; i < keys.length; i++) {
            sb.append(' ');
            sb.append(keys[i]);
        }
        return sb.toString();
    }

    long getPlayerId(final long id) {
        // Exile is not used for id.
        long playerId=id;
        playerId=playerId*ID_FACTOR+life;
        playerId=playerId*ID_FACTOR+poison;
        playerId=playerId*ID_FACTOR+builderCost.getMinimumAmount();
        playerId=playerId*ID_FACTOR+permanents.getStateId();
        playerId=playerId*ID_FACTOR+hand.getStateId();
        playerId=playerId*ID_FACTOR+graveyard.getStateId();
        return playerId;
    }

    @Override
    public String toString() {
        return playerDefinition.getName();
    }

    public Set<MagicSourceActivation<? extends MagicSource>> getSourceActivations() {
        Set<MagicSourceActivation<? extends MagicSource>> set = new TreeSet<MagicSourceActivation<? extends MagicSource>>();
        for (final MagicCard card : hand) {
            set.addAll(card.getSourceActivations());
        }
        for (final MagicPermanent perm : permanents) {
            set.addAll(perm.getSourceActivations());
        }
        return set;
    }

    public MagicPlayerDefinition getPlayerDefinition() {
        return playerDefinition;
    }

    public int getIndex() {
        return index;
    }

    public long getId() {
        return 1000000000L + index;
    }

    public void setState(final MagicPlayerState state) {
        stateFlags|=state.getMask();
    }

    public void clearState(final MagicPlayerState state) {
        stateFlags&=Integer.MAX_VALUE-state.getMask();
    }

    public boolean hasState(final MagicPlayerState state) {
        return state.hasState(stateFlags);
    }

    public int getStateFlags() {
        return stateFlags;
    }

    public void setStateFlags(final int flags) {
        stateFlags=flags;
    }

    public void setLife(final int life) {
        this.life=life;
    }

    public int getLife() {
        return life;
    }

    public void setPoison(final int poison) {
        this.poison=poison;
    }

    public int getPoison() {
        return poison;
    }

    public void changeExtraTurns(final int amount) {
        extraTurns+=amount;
    }

    public int getExtraTurns() {
        return extraTurns;
    }

    public int getHandSize() {
        return hand.size();
    }

    public int getNumExcessCards() {
        return Math.max(0, getHandSize() - maxHandSize);
    }

    public void noMaxHandSize() {
        maxHandSize = Integer.MAX_VALUE;
    }

    public MagicCardList getHand() {
        return hand;
    }

    public void addCardToHand(final MagicCard card) {
        hand.addToTop(card);
    }

    public void addCardToHand(final MagicCard card,final int aIndex) {
        hand.add(aIndex,card);
    }

    public int removeCardFromHand(final MagicCard card) {
        return hand.removeCard(card);
    }

    void setHandToUnknown() {
        hand.setKnown(false);
    }

    void showRandomizedHandAndLibrary() {
        // put hand back into library
        final int handSize = hand.size();
        while (hand.size() > 0) {
            final MagicCard card = hand.getCardAtTop();
            removeCardFromHand(card);
            library.addToTop(card);
        }
        // shuffle
        library.shuffle(MagicRandom.nextInt(999999));
        library.setKnown(true);
        // put handSize cards into hand
        for (int i = 0; i < handSize; i++) {
            addCardToHand(library.removeCardAtTop());
        }
    }

    void createHandAndLibrary(final int handSize) {
        for (final MagicCardDefinition cardDefinition : playerDefinition.getDeck()) {
            final long id = currGame.getUniqueId();
            library.add(new MagicCard(cardDefinition,this,id));
        }

        //library order depends on player index, game no, random seed
        final long seed = magic.MurmurHash3.hash(new long[] {
            2 * index - 1,
            MagicGame.getCount(),
            (System.getProperty("rndSeed") != null) ?
                Long.parseLong(System.getProperty("rndSeed")) :
                System.currentTimeMillis()
        });

        library.initialShuffle(seed);

        for (int count = handSize; count > 0 && !library.isEmpty(); count--) {
            addCardToHand(library.removeCardAtTop());
        }
    }

    public MagicCardList getLibrary() {
        return library;
    }

    public MagicCardList getGraveyard() {
        return graveyard;
    }

    public MagicCardList getExile() {
        return exile;
    }

    public MagicPermanentSet getPermanents() {
        return permanents;
    }

    public void addPermanent(final MagicPermanent permanent) {
        final boolean added = permanents.add(permanent);
        assert added == true : permanent + " cannot be added to " + this;
        if (permanent.producesMana()) {
            manaPermanents.add(permanent);
        }
    }

    public void removePermanent(final MagicPermanent permanent) {
        final boolean removed = permanents.remove(permanent);
        assert removed == true : permanent + " cannot be removed from " + this;
        if (permanent.producesMana()) {
            manaPermanents.remove(permanent);
        }
    }

    public List<MagicSourceManaActivation> getManaActivations(final MagicGame game) {
        final List<MagicSourceManaActivation> activations=new ArrayList<MagicSourceManaActivation>();
        for (final MagicPermanent permanent : manaPermanents) {

            if (game.isArtificial()&&permanent.hasState(MagicPermanentState.ExcludeManaSource)) {
                continue;
            }

            final MagicSourceManaActivation sourceActivation=new MagicSourceManaActivation(game,permanent);
            if (sourceActivation.available) {
                activations.add(sourceActivation);
            }
        }
        return activations;
    }

    private int getManaActivationsCount(final MagicGame game) {
        int count=0;
        for (final MagicPermanent permanent : manaPermanents) {

            final MagicSourceManaActivation sourceActivation=new MagicSourceManaActivation(game,permanent);
            if (sourceActivation.available) {
                count++;
            }
        }
        return count;
    }

    public void setBuilderCost(final MagicBuilderManaCost builderCost) {
        this.builderCost=builderCost;
    }

    public MagicBuilderManaCost getBuilderCost() {
        return builderCost;
    }

    public void setActivationPriority(final MagicActivationPriority abilityPriority) {
        this.activationPriority=abilityPriority;
    }

    public MagicActivationPriority getActivationPriority() {
        return activationPriority;
    }

    public int getMaximumX(final MagicGame game,final MagicManaCost cost) {
        return (getManaActivationsCount(game) -
                builderCost.getMinimumAmount() -
                cost.getConvertedCost()) /
                cost.getXCount();
    }

    public int getNrOfAttackers() {
        int count=0;
        for (final MagicPermanent permanent : permanents) {
            if (permanent.hasState(MagicPermanentState.Attacking)) {
                count++;
            }
        }
        return count;
    }

    public int getNrOfBlockers() {
        int count=0;
        for (final MagicPermanent permanent : permanents) {
            if (permanent.hasState(MagicPermanentState.Blocking)) {
                count++;
            }
        }
        return count;
    }

    public int getNrOfPermanentsWithType(final MagicType type) {
        int count=0;
        for (final MagicPermanent permanent : permanents) {
            if (permanent.hasType(type)) {
                count++;
            }
        }
        return count;
    }

    public int getNrOfPermanentsWithSubType(final MagicSubType subType) {
        int count=0;
        for (final MagicPermanent permanent : permanents) {
            if (permanent.hasSubType(subType)) {
                count++;
            }
        }
        return count;
    }

    public int getNrOfPermanents(final MagicTargetFilter<MagicPermanent> filter) {
        int count = 0;
        for (final MagicPermanent permanent : permanents) {
            if (filter.accept(currGame, this, permanent)) {
                count++;
            }
        }
        return count;
    }


    public boolean controlsPermanent(final MagicTargetFilter<MagicPermanent> filter) {
        for (final MagicPermanent permanent : permanents) {
            if (filter.accept(currGame, this, permanent)) {
                return true;
            }
        }
        return false;
    }

    public boolean controlsPermanent(final MagicPermanent permanent) {
        return permanents.contains(permanent);
    }

    public boolean controlsPermanent(final MagicColor color) {
        for (final MagicPermanent permanent : permanents) {
            if (permanent.hasColor(color)) {
                return true;
            }
        }
        return false;
    }

    public boolean controlsPermanent(final MagicType type) {
        for (final MagicPermanent permanent : permanents) {
            if (permanent.hasType(type)) {
                return true;
            }
        }
        return false;
    }

    public boolean controlsPermanent(final MagicSubType subType) {
        for (final MagicPermanent permanent : permanents) {
            if (permanent.hasSubType(subType)) {
                return true;
            }
        }
        return false;
    }

    public boolean controlsPermanent(final MagicAbility ability) {
        for (final MagicPermanent permanent : permanents) {
            if (permanent.hasAbility(ability)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public MagicCardDefinition getCardDefinition() {
        throw new RuntimeException("player has no card definition");
    }

    @Override
    public String getName() {
        return playerDefinition.getName();
    }

    @Override
    public boolean isPermanent() {
        return false;
    }

    @Override
    public boolean isCreature() {
        return false;
    }

    @Override
    public boolean isPlayer() {
        return true;
    }

    @Override
    public boolean isPlaneswalker() {
        return false;
    }

    @Override
    public boolean isSpell() {
        return false;
    }

    @Override
    public int getPreventDamage() {
        return preventDamage;
    }

    @Override
    public void setPreventDamage(final int amount) {
        preventDamage=amount;
    }

    @Override
    public MagicPlayer getController() {
        return this;
    }

    public boolean isValid() {
        return true;
    }

    public MagicPlayer getOpponent() {
        return currGame.getOpponent(this);
    }

    public void addAbility(final MagicAbility ability) {
        cachedAbilityFlags.add(ability);
    }

    @Override
    public boolean hasAbility(final MagicAbility ability) {
        return cachedAbilityFlags.contains(ability);
    }

    @Override
    public boolean hasType(final MagicType type) {
        return false;
    }

    @Override
    public boolean hasSubType(final MagicSubType subType) {
        return false;
    }

    @Override
    public boolean hasColor(final MagicColor color) {
        return false;
    }

    @Override
    public boolean isValidTarget(final MagicSource source) {
        // Can't be the target of spells or abilities your opponents controls.
        if (hasAbility(MagicAbility.Hexproof) && source.getController() != this) {
            return false;
        }
        return true;
    }

    @Override
    public boolean isLegalTarget(final MagicPlayer player, final MagicTargetFilter<? extends MagicTarget> targetFilter) {
        return true;
    }

    public void incDrawnCards() {
        drawnCards++;
    }

    public void decDrawnCards() {
        drawnCards--;
    }

    public void setDrawnCards(final int drawnCards) {
        this.drawnCards = drawnCards;
    }

    public int getDrawnCards() {
        return drawnCards;
    }

    public void generateStateBasedActions() {
        if (getLife() <= 0) {
            currGame.addDelayedAction(new MagicLoseGameAction(this,MagicLoseGameAction.LIFE_REASON));
        }
        if (getPoison() >= LOSING_POISON) {
            currGame.addDelayedAction(new MagicLoseGameAction(this,MagicLoseGameAction.POISON_REASON));
        }
    }

    public void apply(final MagicLayer layer) {
        switch (layer) {
            case Player:
                cachedAbilityFlags = MagicAbility.noneOf();
                stateFlags = 0;
                maxHandSize = 7;
                break;
            default:
                throw new RuntimeException("No case for " + layer + " in MagicPlayer.apply");
        }
    }

    private void apply(final MagicPermanent source, final MagicStatic mstatic) {
        final MagicLayer layer = mstatic.getLayer();
        switch (layer) {
            case Player:
                mstatic.modPlayer(source, this);
                break;
            default:
                throw new RuntimeException("No case for " + layer + " in MagicPlayer.apply");
        }
    }

    public static void update(final MagicGame game) {
        for (final MagicPlayer player : game.getPlayers()) {
            player.apply(MagicLayer.Player);
        }
        for (final MagicPermanentStatic mpstatic : game.getStatics(MagicLayer.Player)) {
            final MagicStatic mstatic = mpstatic.getStatic();
            final MagicPermanent source = mpstatic.getPermanent();
            for (final MagicPlayer player : game.getPlayers()) {
                if (mstatic.accept(game, source, source)) {
                   player.apply(source, mstatic);
                }
            }
        }
    }
}
