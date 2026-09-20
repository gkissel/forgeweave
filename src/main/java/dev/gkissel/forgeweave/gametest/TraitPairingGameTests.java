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
            "swiftward", "deadweight", "bloodward", "vigorward", "blightward", "venomward", "revealward",
            "stormward", "voidward", "unravelward", "surgeward", "tideward", "mendward", "duskward",
            "kinetic_reserve", "magic_protection", "azure_electrum_rush", "azure_silver_plunge",
            "ferricore_grip", "ironwood_grip", "gravitite_dive", "mendreach", "slimevine_snap",
            "inferium_edge", "prudentium_edge", "tertium_edge", "imperium_edge", "supremium_edge",
            "awakened_supremium_edge", "insanium_edge", "prosperity_bloom", "soulium_reap", "blutonium_pulse",
            "cyanite_chill", "ludicrite_surge", "uraninite_decay", "fluix_arc", "silicon_lattice",
            "quartz_enriched_edge", "iesnium_rite", "fluorite_focus", "vine_weave",
            // #1097: the companions that replaced melee_protection and magic_protection where those
            // two had stopped answering the material's own idea.
            "temperward", "warded", "battleworn", "inferium_ward", "prudentium_ward", "tertium_ward",
            "imperium_ward", "supremium_ward", "awakened_supremium_ward", "insanium_ward",
            "uraninite_sickness", "cyanite_chillback", "blutonium_fallout", "ludicrite_meltdown");

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
    private static Player wearing(GameTestHelper helper, String... traits) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack piece = ToolAssembly.assembleAt(helper, player, STATION, ForgeweaveBlocks.TOOL_STATION.get(),
                ToolAssembly.entryOf(ToolConstants.CHESTPLATE), List.of("iron", "iron"));
        piece.set(ForgeweaveDataComponents.TRAITS.get(), ids(traits));
        player.setItemSlot(EquipmentSlot.CHEST, piece);
        player.tick();
        return player;
    }

    /** One blow onto a healed, non-invulnerable player; what it cost them. */
    private static float lost(Player player, DamageSource source, float amount) {
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
        double heft = wearing(helper, "compressed_iron_heft").getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
        double heavy = wearing(helper, "heavy").getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);

        helper.assertTrue(Math.abs(heft - plain - 0.025) < 1e-4,
                "compressed heft is 0.1 held, so a worn piece must add 0.025 over " + plain + ", got " + heft);
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
        float first = lost(wearing(helper, "inferium_ward"), wither, BLOW);
        float top = lost(wearing(helper, "insanium_ward"), wither, BLOW);

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
        float top = lost(wearing(helper, "insanium_ward"), generic, BLOW);

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
        Player unprotected = wearing(helper, "ludicrite_surge");
        for (int i = 0; i < 10; i++) {
            lost(unprotected, helper.getLevel().damageSources().mobAttack(control), BLOW);
        }
        helper.assertTrue(control.getEffect(MobEffects.WITHER) == null,
                "the tool-side trait must leave an attacker alone");

        LivingEntity attacker = helper.spawn(EntityType.COW, 3, 2, 3);
        Player wearer = wearing(helper, "ludicrite_meltdown");
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
        helper.assertTrue(Math.abs(swift - plain * 1.06) < 1e-4,
                "and by the shipped 6%, so " + plain * 1.06 + " rather than " + swift);
        helper.succeed();
    }

    /**
     * Batch 2's representative: {@code ferricore_grip}, the tool side of a material whose only trait
     * was a worn step-height grant. Sure footing worn, sure footing held.
     */
    @GameTest(template = "empty")
    public static void ferricoreGripPlantsTheWielderWhileHeld(GameTestHelper helper) {
        double bare = holding(helper).getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
        double grip = holding(helper, "ferricore_grip").getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);

        helper.assertTrue(Math.abs(grip - bare - 0.1) < 1e-4,
                "ferricore grip must add 0.1 over " + bare + ", got " + grip);
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
