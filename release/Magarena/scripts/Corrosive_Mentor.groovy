[
    new MagicStatic(
        MagicLayer.Ability,
        MagicTargetFilter.TARGET_BLACK_CREATURE_YOU_CONTROL) {
        @Override
        public void modAbilityFlags(final MagicPermanent source,final MagicPermanent permanent,final Set<MagicAbility> flags) {
            flags.add(MagicAbility.Wither);
        }
    }
]
