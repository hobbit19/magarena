[
    new MagicStatic(
        MagicLayer.Ability,
        MagicTargetFilter.TARGET_CREATURE_YOU_CONTROL) {
        @Override
        public void modAbilityFlags(final MagicPermanent source,final MagicPermanent permanent,final Set<MagicAbility> flags) {
            flags.addAll(MagicAbility.of(
               MagicAbility.Flying,
               MagicAbility.FirstStrike,
               MagicAbility.Vigilance,
               MagicAbility.Trample,
               MagicAbility.Haste,
               MagicAbility.ProtectionFromBlack,
               MagicAbility.ProtectionFromRed
           ));
        }
    }
]
