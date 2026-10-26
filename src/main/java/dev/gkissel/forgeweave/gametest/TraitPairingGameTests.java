package dev.gkissel.forgeweave.gametest;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.tool.ToolConstants;
import dev.gkissel.forgeweave.trait.ForgeweaveTraits;

/**
 * Issue #1093: every material carries a trait that works on each side it builds, and the two belong
 * together. The pairs themselves are argued in {@code docs/research/trait-pairing.md} and guarded
 * by {@code TraitReachabilityTest}; what needs a live server is the two mechanics the batch is built
 * on and one companion from each batch, each next to a control carrying no trait at all.
 *
 * <p>Staging follows {@link MaterialTraitCorrectionGameTests}: most of these materials are
 * existence-gated on mods no GameTest server has, so a piece is assembled from iron at a real Tool
 * Station and its {@code forgeweave:traits} component is overwritten with the shipped ids. The
 * {@code trait_definition} files carry no conditions, so what runs is the shipped JSON with its
 * shipped numbers.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class TraitPairingGameTests {

    private static final BlockPos STATION = new BlockPos(1, 1, 1);
    private static final float BLOW = 10.0F;

    /** Every companion trait #1093 added, in the order `docs/research/trait-pairing.md` lists them. */
    private static final List<String> COMPANIONS = List.of(
            "swiftward", "heft2", "bloodward", "vigorward", "blightward", "venomward", "revealward",
            "stormward", "voidward", "voidward", "surgeward", "tideward", "mendward", "duskward",
            "energized", "magic_protection", "kinetic", "ruthless",
            "heft", "heft", "kinetic", "soulrend", "ecological",
            "keen_edge", "keen_edge", "keen_edge", "keen_edge2", "keen_edge2",
            "keen_edge3", "keen_edge4", "prosperity_bloom", "soulrend", "blutonium_pulse",
            "cyanite_chill", "arcing2", "grievous", "arcing2", "writable",
            "keen_edge", "enfeebling", "energized", "ecological",
            // #1097: the companions that replaced melee_protection and magic_protection where those
            // two had stopped answering the material's own idea.
            "temperward", "warded", "battleworn", "magic_protection", "magic_protection", "magic_protection",
            "magic_protection2", "magic_protection2", "magic_protection3", "magic_protection3",
            "blightward", "cyanite_chillback", "witherward", "witherward");

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, path);
    }

    private static List<ResourceLocation> ids(String... traits) {
        return List.of(traits).stream().map(TraitPairingGameTests::id).toList();
    }

    /** A survival mock player holding a hatchet whose trait list is exactly {@code traits}. */
    private static Player holding(GameTestHelper helper, String... traits) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND,
                CombatTraitGameTests.tool(ForgeweaveItems.TOOL_HATCHET.get(), ids(traits), 3.0F));
        player.tick();
        return player;
    }

    /** A survival mock player wearing an iron chestplate whose trait list is exactly {@code traits}. */
    static Player wearing(GameTestHelper helper, String... traits) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack piece = ToolAssembly.assembleAt(helper, player, STATION, ForgeweaveBlocks.TOOL_STATION.get(),
                ToolAssembly.entryOf(ToolConstants.CHESTPLATE), List.of("iron", "iron"));
        piece.set(ForgeweaveDataComponents.TRAITS.get(), ids(traits));
        player.setItemSlot(EquipmentSlot.CHEST, piece);
        player.tick();
        return player;
    }

    /**
     * A survival mock player in a full staged iron set, helmet to boots, each piece carrying exactly
     * {@code traits}: the checklist's four-piece figures, on the shipped definitions.
     */
    static Player wearingSet(GameTestHelper helper, String... traits) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        List<ToolConstants.Entry> pieces = List.of(
                ToolConstants.HELMET, ToolConstants.CHESTPLATE, ToolConstants.LEGGINGS, ToolConstants.BOOTS);
        List<EquipmentSlot> slots = List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);
        for (int i = 0; i < pieces.size(); i++) {
            ItemStack piece = ToolAssembly.assembleAt(helper, player, STATION, ForgeweaveBlocks.TOOL_STATION.get(),
                    ToolAssembly.entryOf(pieces.get(i)), List.of("iron", "iron"));
            piece.set(ForgeweaveDataComponents.TRAITS.get(), ids(traits));
            player.setItemSlot(slots.get(i), piece);
        }
        player.tick();
        return player;
    }

    /** One blow onto a healed, non-invulnerable player; what it cost them. */
    static float lost(Player player, DamageSource source, float amount) {
        player.setHealth(player.getMaxHealth());
        player.invulnerableTime = 0;
        float before = player.getHealth();
        player.hurt(source, amount);
        return before - player.getHealth();
    }

    // ---------------------------------------------------------------- the two shared mechanics

    /**
     * The worn half of {@code Trait#knockbackResistance} (the batch-0 pair for nine heavy
     * materials). A piece pays a quarter of the held figure, so a full four-piece set of one
     * material reaches the held figure and no further -- #1091 had rejected the flat value per
     * piece because four empowered emeradic pieces would have summed past total immunity. Before
     * this issue a worn piece paid nothing at all.
     */
    @GameTest(template = "empty")
    public static void aWornPieceGetsAQuarterOfTheTraitsKnockbackResistance(GameTestHelper helper) {
        double plain = wearing(helper).getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
        double heft = wearing(helper, "heft").getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
        double heavy = wearing(helper, "heavy").getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);

        helper.assertTrue(Math.abs(heft - plain - 0.0625) < 1e-4,
                "Heft I is 0.25 held, so a worn piece must add 0.0625 over " + plain + ", got " + heft);
        helper.assertTrue(Math.abs(heavy - plain - 0.25) < 1e-4,
                "heavy is 1.0 held, so a worn piece must add 0.25 over " + plain + ", got " + heavy);
        helper.assertTrue(heavy - plain < 1.0,
                "and one piece must stay well short of the full immunity the held tool grants");
        helper.succeed();
    }

    /** The control: a piece carrying a trait with no knockback resistance moves the attribute nowhere. */
    @GameTest(template = "empty")
    public static void aWornPieceWithoutThatTraitGetsNoKnockbackResistance(GameTestHelper helper) {
        double plain = wearing(helper).getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
        double swift = wearing(helper, "swiftward").getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);

        helper.assertTrue(Math.abs(swift - plain) < 1e-4,
                "swiftward grants movement speed, not knockback resistance; got " + swift + " against " + plain);
        helper.succeed();
    }

    /**
     * The new {@code forgeweave:protection} behavior, through {@code magic_protection}, the armor
     * side of the seventeen materials that carried no trait at all. A wither blow is in the
     * {@code forgeweave:magic_protection} tag, so the worn piece takes some of it off.
     */
    @GameTest(template = "empty")
    public static void magicProtectionSoftensAMagicBlow(GameTestHelper helper) {
        DamageSource wither = helper.getLevel().damageSources().wither();
        float plain = lost(wearing(helper), wither, BLOW);
        float warded = lost(wearing(helper, "magic_protection"), wither, BLOW);

        helper.assertTrue(warded < plain,
                "a magic blow must cost less through magic_protection, " + warded + " against " + plain);
        helper.succeed();
    }

    /** The control: the same behavior is tag-scoped, so a blow outside the tag costs exactly the same. */
    @GameTest(template = "empty")
    public static void magicProtectionLeavesANonMagicBlowAlone(GameTestHelper helper) {
        DamageSource generic = helper.getLevel().damageSources().generic();
        float plain = lost(wearing(helper), generic, BLOW);
        float warded = lost(wearing(helper, "magic_protection"), generic, BLOW);

        helper.assertTrue(plain == warded,
                "a generic blow is outside the magic tag and must cost the same, " + warded + " against " + plain);
        helper.succeed();
    }

    // ---------------------------------------------------------------- #1097: the two weak groups

    /**
     * Issue #1097's essence ladder. `magic_protection` used to be the armor side of all nine
     * Mystical Agriculture materials, flat, whatever the tier. The armor side climbs now the way
     * the tool side does: the top rung takes more off a magic blow than the first, and the control
     * wearing no trait takes the blow whole.
     */
    @GameTest(template = "empty")
    public static void theEssenceLadderClimbsOnTheArmorSideToo(GameTestHelper helper) {
        DamageSource wither = helper.getLevel().damageSources().wither();
        float plain = lost(wearing(helper), wither, BLOW);
        float first = lost(wearing(helper, "magic_protection"), wither, BLOW);
        float top = lost(wearing(helper, "magic_protection3"), wither, BLOW);

        helper.assertTrue(first < plain,
                "the first rung must still soften a magic blow, " + first + " against " + plain);
        helper.assertTrue(top < first,
                "the top rung must beat the first, " + top + " against " + first);
        helper.succeed();
    }

    /** The control: the ladder is tag-scoped, so a blow outside the magic tag costs the same on every rung. */
    @GameTest(template = "empty")
    public static void theEssenceLadderLeavesANonMagicBlowAlone(GameTestHelper helper) {
        DamageSource generic = helper.getLevel().damageSources().generic();
        float plain = lost(wearing(helper), generic, BLOW);
        float top = lost(wearing(helper, "magic_protection3"), generic, BLOW);

        helper.assertTrue(plain == top,
                "a generic blow is outside the magic tag and must cost the same, " + top + " against " + plain);
        helper.succeed();
    }

    /**
     * Issue #1097's reactor metals: the radiation the blade carries now answers a blow taken, so
     * whoever strikes a ludicrite wearer withers. The control is the same chestplate carrying the
     * tool-side trait instead, which does nothing to an attacker.
     */
    @GameTest(template = "empty")
    public static void ludicriteMeltdownWithersWhoeverStrikesTheWearer(GameTestHelper helper) {
        LivingEntity control = helper.spawn(EntityType.COW, 2, 2, 2);
        Player unprotected = wearing(helper, "arcing");
        for (int i = 0; i < 10; i++) {
            lost(unprotected, helper.getLevel().damageSources().mobAttack(control), BLOW);
        }
        helper.assertTrue(control.getEffect(MobEffects.WITHER) == null,
                "the tool-side trait must leave an attacker alone");

        LivingEntity attacker = helper.spawn(EntityType.COW, 3, 2, 3);
        Player wearer = wearing(helper, "witherward");
        // P(no proc in 20 blows at 60%) is about 1e-8, the repository's usual bar for a chance roll.
        for (int i = 0; i < 20 && attacker.getEffect(MobEffects.WITHER) == null; i++) {
            lost(wearer, helper.getLevel().damageSources().mobAttack(attacker), BLOW);
        }
        helper.assertTrue(attacker.getEffect(MobEffects.WITHER) != null,
                "whoever strikes a ludicrite wearer must wither");
        helper.succeed();
    }

    /**
     * Issue #1097's answer to a flat edge: {@code temperward} takes a flat point off every blow,
     * where the {@code melee_protection} and {@code magic_protection} it replaced were both tag
     * scoped. The control wears the same piece with no trait.
     */
    @GameTest(template = "empty")
    public static void temperwardTakesAFlatPointOffAnyBlow(GameTestHelper helper) {
        DamageSource generic = helper.getLevel().damageSources().generic();
        float plain = lost(wearing(helper), generic, BLOW);
        float tempered = lost(wearing(helper, "temperward"), generic, BLOW);

        helper.assertTrue(tempered < plain,
                "temperward must soften a generic blow, " + tempered + " against " + plain);
        helper.succeed();
    }

    // ---------------------------------------------------------------- one companion per batch

    /** Batch 1's representative: {@code swiftward}, the worn side of eleven materials' quick edge. */
    @GameTest(template = "empty")
    public static void swiftwardMovesTheWearerFaster(GameTestHelper helper) {
        double plain = wearing(helper).getAttributeValue(Attributes.MOVEMENT_SPEED);
        double swift = wearing(helper, "swiftward").getAttributeValue(Attributes.MOVEMENT_SPEED);

        helper.assertTrue(swift > plain, "swiftward must move the wearer faster, " + swift + " against " + plain);
        helper.assertTrue(Math.abs(swift - plain * 1.08) < 1e-4,
                "and by the shipped 8%, so " + plain * 1.08 + " rather than " + swift);
        helper.succeed();
    }

    /**
     * Batch 2's representative: {@code heft}, the tool side of a material whose only trait
     * was a worn step-height grant. Sure footing worn, sure footing held.
     */
    @GameTest(template = "empty")
    public static void ferricoreGripPlantsTheWielderWhileHeld(GameTestHelper helper) {
        double bare = holding(helper).getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
        double grip = holding(helper, "heft").getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);

        helper.assertTrue(Math.abs(grip - bare - 0.25) < 1e-4,
                "Heft I must add 0.25 over " + bare + ", got " + grip);
        helper.succeed();
    }

    /**
     * Batch 3's representative: {@code cyanite_chill}, the tool side of a reactor metal that shipped
     * with an empty {@code traits} block, next to a control tool carrying nothing.
     */
    @GameTest(template = "empty")
    public static void cyaniteChillSlowsWhatItStrikes(GameTestHelper helper) {
        Player attacker = helper.makeMockPlayer(GameType.SURVIVAL);

        LivingEntity control = helper.spawn(EntityType.COW, 2, 2, 2);
        CombatTraitGameTests.onHit(helper, attacker,
                CombatTraitGameTests.tool(ForgeweaveItems.TOOL_HATCHET.get(), List.of(), 3.0F), control);
        helper.assertTrue(control.getEffect(MobEffects.MOVEMENT_SLOWDOWN) == null,
                "a plain hatchet must leave the target unslowed");

        LivingEntity target = helper.spawn(EntityType.COW, 3, 2, 3);
        CombatTraitGameTests.onHit(helper, attacker,
                CombatTraitGameTests.tool(ForgeweaveItems.TOOL_HATCHET.get(), ids("cyanite_chill"), 3.0F), target);
        helper.assertTrue(target.getEffect(MobEffects.MOVEMENT_SLOWDOWN) != null,
                "cyanite chill must slow what it strikes");
        helper.succeed();
    }

    /**
     * Checklist line 73: a full swiftward set is the 32% its sentence quotes. Each piece adds its 8%
     * as {@code ADD_MULTIPLIED_BASE}, so the four slots add rather than compounding to 1.08^4, about
     * 36%. {@code TraitDefinitionAuditTest#aMovementBonusSetReachesTheFigureItsDescriptionQuotes} holds
     * every shipped movement bonus to the same rule.
     */
    @GameTest(template = "empty")
    public static void aFullSwiftwardSetIsThirtyTwoPercentFaster(GameTestHelper helper) {
        double plain = wearingSet(helper).getAttributeValue(Attributes.MOVEMENT_SPEED);
        double swift = wearingSet(helper, "swiftward").getAttributeValue(Attributes.MOVEMENT_SPEED);

        helper.assertTrue(Math.abs(swift - plain * 1.32) < 1e-4,
                "four swiftward pieces must reach the quoted 32%, so " + plain * 1.32 + ", got " + swift);
        helper.succeed();
    }

    /**
     * Checklist line 73's Heft half: one worn piece takes a quarter of Heft I's 25% off a shove. The
     * control is the same staged chestplate with no trait, shoved the same way.
     */
    @GameTest(template = "empty")
    public static void oneHeftPieceTakesAQuarterOfItsFigureOffAShove(GameTestHelper helper) {
        double plain = shoved(wearing(helper));
        double heft = shoved(wearing(helper, "heft"));

        helper.assertTrue(plain > 0.0, "the control must be shoved at all, got " + plain);
        helper.assertTrue(Math.abs(heft / plain - (1.0 - 0.0625)) < 1e-4,
                "one Heft I piece must take 6.25% off the shove, got " + heft + " against " + plain);
        helper.succeed();
    }

    /**
     * Checklist line 73 asks for a Heft piece to hold the wearer against a creeper. 1.21 reads a
     * blast's push off {@code EXPLOSION_KNOCKBACK_RESISTANCE}, a separate attribute from
     * {@code KNOCKBACK_RESISTANCE}, so heft raises both by the same figure.
     */
    @GameTest(template = "empty")
    public static void oneHeftPieceHoldsTheWearerAgainstABlast(GameTestHelper helper) {
        double plain = blasted(helper, 2);
        double heft = blasted(helper, 14, "heft");

        helper.assertTrue(plain > 0.0, "the control must be pushed by the blast, got " + plain);
        helper.assertTrue(Math.abs(heft / plain - (1.0 - 0.0625)) < 1e-3,
                "one Heft I piece must take 6.25% off a blast's push, got " + heft + " against " + plain);
        helper.succeed();
    }

    /** How far a small blast 1.5 blocks away pushes a zombie wearing a staged chestplate of {@code traits}. */
    private static double blasted(GameTestHelper helper, int x, String... traits) {
        net.minecraft.world.entity.monster.Zombie zombie = helper.spawn(EntityType.ZOMBIE, x, 2, 2);
        zombie.setNoAi(true);
        Player builder = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack piece = ToolAssembly.assembleAt(helper, builder, STATION, ForgeweaveBlocks.TOOL_STATION.get(),
                ToolAssembly.entryOf(ToolConstants.CHESTPLATE), List.of("iron", "iron"));
        piece.set(ForgeweaveDataComponents.TRAITS.get(), ids(traits));
        zombie.setItemSlot(EquipmentSlot.CHEST, piece);
        zombie.tick();
        zombie.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        helper.getLevel().explode(null, zombie.getX() - 1.5, zombie.getY(), zombie.getZ(), 1.0F,
                net.minecraft.world.level.Level.ExplosionInteraction.NONE);
        double push = zombie.getDeltaMovement().horizontalDistance();
        zombie.discard();
        return push;
    }

    /** How far one vanilla shove moves {@code player} horizontally. */
    private static double shoved(Player player) {
        player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        player.knockback(0.4, 1.0, 0.0);
        return player.getDeltaMovement().horizontalDistance();
    }

    /**
     * Checklist lines 211 and 244: a heavy material's knockback resistance reaches the held figure
     * with four worn pieces and goes no further. Heavy is 1.0 held, Heft I-III 0.25, 0.40 and 0.55
     * (line 241's heft3), each against the same set worn with no trait.
     */
    @GameTest(template = "empty")
    public static void fourWornPiecesReachTheHeldKnockbackFigureAndNoMore(GameTestHelper helper) {
        double plainSet = wearingSet(helper).getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
        double bare = holding(helper).getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
        String[] traits = {"heavy", "heft", "heft2", "heft3"};
        double[] figures = {1.0, 0.25, 0.40, 0.55};
        for (int i = 0; i < traits.length; i++) {
            double held = holding(helper, traits[i]).getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) - bare;
            double set = wearingSet(helper, traits[i]).getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) - plainSet;
            helper.assertTrue(Math.abs(held - figures[i]) < 1e-4,
                    traits[i] + " held must add " + figures[i] + ", got " + held);
            helper.assertTrue(Math.abs(set - figures[i]) < 1e-4,
                    "four " + traits[i] + " pieces must add exactly the held " + figures[i] + ", got " + set);
        }
        helper.succeed();
    }

    /** Heft I-III hold against a blast by the same figure, held or as a full worn set. */
    @GameTest(template = "empty")
    public static void everyHeftRungResistsABlastByItsOwnFigure(GameTestHelper helper) {
        var blast = Attributes.EXPLOSION_KNOCKBACK_RESISTANCE;
        double plainSet = wearingSet(helper).getAttributeValue(blast);
        double bare = holding(helper).getAttributeValue(blast);
        String[] traits = {"heft", "heft2", "heft3"};
        double[] figures = {0.25, 0.40, 0.55};
        for (int i = 0; i < traits.length; i++) {
            double held = holding(helper, traits[i]).getAttributeValue(blast) - bare;
            double set = wearingSet(helper, traits[i]).getAttributeValue(blast) - plainSet;
            helper.assertTrue(Math.abs(held - figures[i]) < 1e-4,
                    traits[i] + " held must add " + figures[i] + " blast resistance, got " + held);
            helper.assertTrue(Math.abs(set - figures[i]) < 1e-4,
                    "four " + traits[i] + " pieces must add " + figures[i] + " blast resistance, got " + set);
        }
        helper.succeed();
    }

    /** Checklist line 212: whoever strikes a venomward wearer comes away with Poison II for 5 seconds. */
    @GameTest(template = "empty")
    public static void venomwardPoisonsWhoeverStrikesTheWearer(GameTestHelper helper) {
        assertTheAttackerComesAwayWith(helper, "venomward", MobEffects.POISON, 1);
        helper.succeed();
    }

    /** Checklist line 243: whoever strikes a cyanite wearer comes away with Slowness II for 5 seconds. */
    @GameTest(template = "empty")
    public static void cyaniteChillbackSlowsWhoeverStrikesTheWearer(GameTestHelper helper) {
        assertTheAttackerComesAwayWith(helper, "cyanite_chillback", MobEffects.MOVEMENT_SLOWDOWN, 1);
        helper.succeed();
    }

    /**
     * Strikes a {@code trait} wearer until the attacker carries {@code effect} at {@code amplifier}
     * for the shipped 5 seconds. Each blow rolls the shipped 20%, so 60 blows all missing is about 1
     * in 600,000. The control is a cow striking a wearer with no trait, which comes away clean.
     */
    private static void assertTheAttackerComesAwayWith(GameTestHelper helper, String trait,
            net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect, int amplifier) {
        LivingEntity control = helper.spawn(EntityType.COW, 2, 2, 2);
        Player plain = wearing(helper);
        for (int i = 0; i < 20; i++) {
            lost(plain, helper.getLevel().damageSources().mobAttack(control), 1.0F);
        }
        helper.assertTrue(control.getEffect(effect) == null, "an untraited piece must leave the attacker alone");

        LivingEntity attacker = helper.spawn(EntityType.COW, 3, 2, 3);
        Player wearer = wearing(helper, trait);
        for (int i = 0; i < 60 && attacker.getEffect(effect) == null; i++) {
            lost(wearer, helper.getLevel().damageSources().mobAttack(attacker), 1.0F);
        }
        var applied = attacker.getEffect(effect);
        helper.assertTrue(applied != null, "whoever strikes a " + trait + " wearer must come away affected");
        helper.assertTrue(applied.getAmplifier() == amplifier && applied.getDuration() == 100,
                trait + " must leave level " + (amplifier + 1) + " for 5 seconds, got " + applied);
    }

    /** Checklist line 212: being struck in bloodward grants Regeneration for 6 seconds. */
    @GameTest(template = "empty")
    public static void bloodwardRegeneratesTheWearerForSixSeconds(GameTestHelper helper) {
        LivingEntity attacker = helper.spawn(EntityType.COW, 2, 2, 2);
        Player plain = wearing(helper);
        lost(plain, helper.getLevel().damageSources().mobAttack(attacker), 1.0F);
        helper.assertTrue(plain.getEffect(MobEffects.REGENERATION) == null, "an untraited piece grants nothing");

        Player wearer = wearing(helper, "bloodward");
        lost(wearer, helper.getLevel().damageSources().mobAttack(attacker), 1.0F);
        var regeneration = wearer.getEffect(MobEffects.REGENERATION);
        helper.assertTrue(regeneration != null && regeneration.getDuration() == 120,
                "a blow taken in bloodward must grant 6 seconds of Regeneration, got " + regeneration);
        helper.succeed();
    }

    /**
     * Checklist line 225, the tooltip half: a tool of each Just Dire Things material, built at the
     * real assembly path from the shipped material file, shows the trait line the checklist expects in
     * its Shift tooltip. The materials are existence-gated, so they go into a registry of their own
     * here; the trait names come from the live datapack families, which is why this is a GameTest.
     * {@code ferricore}'s sure footing is armor-only and armor tooltips list no traits, so its tool
     * shows heft.
     */
    @GameTest(template = "empty")
    public static void justDireThingsToolsShowTheirTraitLines(GameTestHelper helper) throws Exception {
        assertToolShows(helper, "ferricore", "heft", 1);
        assertToolShows(helper, "blazegold", "fireward", 1);
        assertToolShows(helper, "celestigem", "energized", 2);
        assertToolShows(helper, "eclipsealloy", "energized", 3);
        assertToolShows(helper, "eclipsealloy", "lastbreath", 1);
        helper.succeed();
    }

    private static void assertToolShows(GameTestHelper helper, String material, String family, int level)
            throws Exception {
        List<net.minecraft.network.chat.Component> tooltip = shiftTooltip(helper, material);
        String key = "trait.forgeweave." + family + ".name";
        boolean found = tooltip.stream().anyMatch(line -> line.getContents()
                instanceof net.minecraft.network.chat.contents.TranslatableContents name
                && name.getKey().equals(key)
                && (level == 1 || line.getSiblings().stream().anyMatch(sibling -> sibling.getContents()
                        instanceof net.minecraft.network.chat.contents.TranslatableContents numeral
                        && numeral.getKey().equals("enchantment.level." + level))));
        helper.assertTrue(found, "a " + material + " pickaxe's Shift tooltip must name " + key
                + (level > 1 ? " at level " + level : "") + ", got " + tooltip);
    }

    /** The detailed tooltip of a pickaxe assembled from three parts of the shipped {@code material}. */
    private static List<net.minecraft.network.chat.Component> shiftTooltip(GameTestHelper helper, String material)
            throws Exception {
        ResourceLocation materialId = id(material);
        var ops = net.minecraft.resources.RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE,
                helper.getLevel().registryAccess());
        var resource = helper.getLevel().getServer().getResourceManager()
                .getResource(id("forgeweave/material/" + material + ".json")).orElseThrow();
        dev.gkissel.forgeweave.material.Material parsed;
        try (var reader = resource.openAsReader()) {
            parsed = dev.gkissel.forgeweave.material.Material.CODEC
                    .parse(ops, com.google.gson.JsonParser.parseReader(reader)).getOrThrow();
        }
        var registry = new net.minecraft.core.MappedRegistry<>(dev.gkissel.forgeweave.material.Material.REGISTRY,
                com.mojang.serialization.Lifecycle.stable());
        registry.register(net.minecraft.resources.ResourceKey.create(
                        dev.gkissel.forgeweave.material.Material.REGISTRY, materialId),
                parsed, net.minecraft.core.RegistrationInfo.BUILT_IN);
        var registries = net.minecraft.core.HolderLookup.Provider.create(java.util.stream.Stream.of(registry.asLookup()));

        ItemStack pickaxe = dev.gkissel.forgeweave.menu.ToolAssemblyRecipes.assemble(registries,
                        ToolAssembly.entryFor(ForgeweaveItems.TOOL_PICKAXE.get()), List.of(materialId, materialId, materialId))
                .orElseThrow(() -> new AssertionError("a " + material + " pickaxe must assemble"));
        // ToolTooltip is package-private to item/, and its Shift tier is client-config gated
        // everywhere else, so the GameTest calls the line assembly itself.
        var append = Class.forName("dev.gkissel.forgeweave.item.ToolTooltip").getDeclaredMethod("append",
                ItemStack.class, net.minecraft.core.HolderLookup.Provider.class, boolean.class, float.class,
                List.class);
        append.setAccessible(true);
        List<net.minecraft.network.chat.Component> tooltip = new java.util.ArrayList<>();
        append.invoke(null, pickaxe, registries, true, 1.0F, tooltip);
        return tooltip;
    }

    /** Every companion id resolves: a renamed or dropped definition fails here rather than in play. */
    @GameTest(template = "empty")
    public static void everyCompanionTraitIdResolvesToABehaviour(GameTestHelper helper) {
        for (String trait : COMPANIONS) {
            helper.assertTrue(ForgeweaveTraits.lookup(id(trait)) != null,
                    "expected forgeweave:" + trait + " to resolve; a material names it (issue #1093)");
        }
        helper.succeed();
    }

    private TraitPairingGameTests() {}
}
