[
    new MagicIfDamageWouldBeDealtTrigger(3) {
        @Override
        public MagicEvent executeTrigger(final MagicGame game,final MagicPermanent permanent,final MagicDamage damage) {
            final MagicSource source=damage.getSource();
            if (permanent.isFriend(source) &&
                source.isSpell() &&
                source.hasColor(MagicColor.Red) &&
                source.getCardDefinition().isSpell()) {
                // Generates no event or action.
                damage.setAmount(damage.getAmount() * 2);
            }
            return MagicEvent.NONE;
        }
    }
]
