package dev.gkissel.forgeweave.compat.apotheosis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import io.netty.buffer.Unpooled;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;

import dev.gkissel.forgeweave.compat.apotheosis.ApotheosisSockets.AttributeGrant;
import dev.gkissel.forgeweave.compat.apotheosis.ApotheosisSockets.GemEffect;
import dev.gkissel.forgeweave.compat.apotheosis.ApotheosisSockets.Quantity;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.modifier.ForgeweaveModifiers;
import dev.gkissel.forgeweave.modifier.ModifierApplication.Outcome;
import dev.gkissel.forgeweave.modifier.ModifierEntry;

/**
 * Issue #969: the socket component's two codecs, the socket arithmetic, the bonus fold, and the
 * gem-effect mapping table's totality.
 *
 * <p>No Apotheosis here, deliberately. Reading a real gem's bonus needs both a baked loot-category
 * registry and a loaded gem datapack, neither of which a unit test has, so every bonus case below
 * drives {@link ApotheosisSockets}'s own fold through a fake {@link ApotheosisSockets.Bridge} -- the
 * split {@code DraconicModuleEffectGameTests} already makes for the same reason. What a real bridge
 * answers is a manual release-checklist line (JC-B).
 */
class ApotheosisSocketTest {

    private static RegistryOps<com.google.gson.JsonElement> ops;
    private static RegistryAccess registries;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        ops = RegistryOps.create(JsonOps.INSTANCE, registries);
    }

    @AfterEach
    void removeAnyBridge() {
        ApotheosisSockets.install(null);
    }

    // ---------------------------------------------------------------- the component's two codecs

    @Test
    void theSocketComponentRoundTripsThroughItsCodec() {
        ItemContainerContents sockets = ItemContainerContents.fromItems(
                List.of(new ItemStack(Items.DIAMOND), ItemStack.EMPTY, new ItemStack(Items.EMERALD)));

        var encoded = ItemContainerContents.CODEC.encodeStart(ops, sockets).getOrThrow();
        ItemContainerContents decoded = ItemContainerContents.CODEC.parse(ops, encoded).getOrThrow();

        assertEquals(sockets, decoded, "the socket component must survive a save/load round trip");
        List<ItemStack> items = decoded.stream().toList();
        assertEquals(3, items.size(), "a gem in socket three keeps socket three, empties and all");
        assertTrue(items.get(0).is(Items.DIAMOND));
        assertTrue(items.get(1).isEmpty(), "the empty middle socket must stay empty rather than shift");
        assertTrue(items.get(2).is(Items.EMERALD));
    }

    @Test
    void theSocketComponentRoundTripsThroughItsStreamCodec() {
        ItemContainerContents sockets = ItemContainerContents.fromItems(
                List.of(ItemStack.EMPTY, new ItemStack(Items.AMETHYST_SHARD, 1)));
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);

        ItemContainerContents.STREAM_CODEC.encode(buffer, sockets);
        ItemContainerContents decoded = ItemContainerContents.STREAM_CODEC.decode(buffer);

        assertEquals(sockets, decoded, "the socket component must survive the client sync too");
        assertEquals(0, buffer.readableBytes(), "the stream codec must consume exactly what it wrote");
    }

    // ---------------------------------------------------------------- socket arithmetic

    @Test
    void socketsAreTheModifiersLevelAndAbsentMeansNone() {
        ItemStack bare = new ItemStack(Items.STICK);
        assertEquals(0, ApotheosisSockets.socketCount(bare), "no socketed entry means no sockets");
        assertTrue(ApotheosisSockets.gems(bare).isEmpty());
        assertFalse(ApotheosisSockets.hasEmptySocket(bare));

        ItemStack two = socketed(2);
        assertEquals(2, ApotheosisSockets.socketCount(two));
        assertEquals(List.of(ItemStack.EMPTY, ItemStack.EMPTY), ApotheosisSockets.gems(two),
                "two sockets with nothing in them read as two empty sockets, not as no component");
        assertTrue(ApotheosisSockets.hasEmptySocket(two));
    }

    @Test
    void aLevelAboveTheCapStillOnlyReadsThreeSockets() {
        ItemStack overfull = socketed(9);
        assertEquals(ApotheosisSockets.MAX_SOCKETS, ApotheosisSockets.socketCount(overfull),
                "a datapack that raises max_level past the cap must not conjure a fourth socket");
        assertEquals(ApotheosisSockets.MAX_SOCKETS, ApotheosisSockets.gems(overfull).size());
    }

    @Test
    void gemsBeyondTheSocketCountAreNeverRead() {
        ItemStack one = socketed(1);
        one.set(ForgeweaveDataComponents.SOCKETS.get(), ItemContainerContents.fromItems(
                List.of(new ItemStack(Items.DIAMOND), new ItemStack(Items.EMERALD))));

        List<ItemStack> gems = ApotheosisSockets.gems(one);
        assertEquals(1, gems.size(), "a component left over from a wider socket set is clipped, not trusted");
        assertTrue(gems.get(0).is(Items.DIAMOND));
    }

    // ---------------------------------------------------------------- seating

    @Test
    void seatingFillsTheFirstEmptySocketAndSpendsOneGem() {
        ApotheosisSockets.install(new FakeBridge());
        ItemStack tool = socketed(2);

        Outcome first = ApotheosisSockets.seat(tool, List.of(ItemStack.EMPTY, new ItemStack(Items.DIAMOND, 4)));
        assertFalse(first.output().isEmpty(), "a gem beside a socketed tool must seat: " + first.rejection());
        assertEquals(0, first.used(0), "the empty slot is untouched");
        assertEquals(1, first.used(1), "exactly one gem is spent, however many sit in the slot");
        List<ItemStack> gems = ApotheosisSockets.gems(first.output());
        assertTrue(gems.get(0).is(Items.DIAMOND), "the first socket takes it");
        assertEquals(1, gems.get(0).getCount(), "one gem per socket, not the whole stack");
        assertTrue(gems.get(1).isEmpty(), "the second socket is still open");

        Outcome second = ApotheosisSockets.seat(first.output(), List.of(new ItemStack(Items.EMERALD)));
        assertFalse(second.output().isEmpty(), "the second gem fills the second socket");
        assertTrue(ApotheosisSockets.gems(second.output()).get(1).is(Items.EMERALD));

        Outcome third = ApotheosisSockets.seat(second.output(), List.of(new ItemStack(Items.EMERALD)));
        assertTrue(third.output().isEmpty(), "a full tool refuses a third gem");
        assertNotNull(third.rejection(), "and says why");
    }

    @Test
    void seatingRefusesAToolWithNoSocketsAndAGemApotheosisWouldNotTake() {
        ApotheosisSockets.install(new FakeBridge());
        Outcome none = ApotheosisSockets.seat(new ItemStack(Items.STICK), List.of(new ItemStack(Items.DIAMOND)));
        assertTrue(none.output().isEmpty(), "no sockets, no seating");
        assertNotNull(none.rejection());

        ApotheosisSockets.install(new FakeBridge() {
            @Override
            public boolean canSeat(ItemStack target, ItemStack gem) {
                return false;
            }
        });
        Outcome refused = ApotheosisSockets.seat(socketed(1), List.of(new ItemStack(Items.DIAMOND)));
        assertTrue(refused.output().isEmpty(), "a gem this item's category has no bonus for is refused");
        assertNotNull(refused.rejection());
    }

    @Test
    void withNoBridgeNothingSeatsAndEveryBonusIsZero() {
        ItemStack tool = socketed(3);
        assertTrue(ApotheosisSockets.seat(tool, List.of(new ItemStack(Items.DIAMOND))).output().isEmpty(),
                "with no Apotheosis there is no gem to seat");
        assertFalse(ApotheosisSockets.active());
        assertEquals(0.0F, ApotheosisSockets.attackDamageBonus(tool));
        assertEquals(0.0F, ApotheosisSockets.miningSpeedBonus(tool));
        assertEquals(0.0F, ApotheosisSockets.armorBonus(tool));
        assertEquals(0.0F, ApotheosisSockets.armorToughnessBonus(tool));
        assertEquals(0.0F, ApotheosisSockets.knockbackResistanceBonus(tool));
        assertEquals(0, ApotheosisSockets.durabilityBonus(tool, 1000));
    }

    // ---------------------------------------------------------------- the bonus fold

    @Test
    void mappedAttributesLandOnTheirOwnQuantityAndNothingElse() {
        ApotheosisSockets.install(new FakeBridge(
                grant("minecraft:generic.attack_damage", 2.5),
                grant("apothic_attributes:mining_speed", 3.0),
                grant("minecraft:player.mining_efficiency", 1.0),
                grant("minecraft:generic.armor", 4.0),
                grant("minecraft:generic.armor_toughness", 1.5),
                grant("minecraft:generic.knockback_resistance", 0.25),
                // Two attributes the mapping deliberately leaves alone: they must not leak into any
                // Forgeweave quantity rather than being quietly folded into the nearest one.
                grant("apothic_attributes:crit_chance", 100.0),
                grant("minecraft:generic.movement_speed", 100.0)));
        ItemStack tool = socketed(1);

        assertEquals(2.5F, ApotheosisSockets.attackDamageBonus(tool));
        assertEquals(4.0F, ApotheosisSockets.miningSpeedBonus(tool),
                "both mapped dig-speed attributes sum into the one quantity");
        assertEquals(4.0F, ApotheosisSockets.armorBonus(tool));
        assertEquals(1.5F, ApotheosisSockets.armorToughnessBonus(tool));
        assertEquals(0.25F, ApotheosisSockets.knockbackResistanceBonus(tool));
    }

    @Test
    void aDurabilityGemGrowsTheSamePoolEveryModifierGrows() {
        ApotheosisSockets.install(new FakeBridge() {
            @Override
            public float durabilityFraction(ItemStack stack) {
                return 0.15F;
            }
        });
        ItemStack tool = socketed(1);

        assertEquals(150, ApotheosisSockets.durabilityBonus(tool, 1000), "15% of the untouched base");
        assertEquals(0, ApotheosisSockets.durabilityBonus(tool, 6),
                "a fraction too small to buy a whole point buys none");
        assertEquals(1000 + 150, ForgeweaveModifiers.modifiedDurability(tool, 1000),
                "and it folds into modifiedDurability alongside the modifier bonuses");
    }

    @Test
    void aBonusOnAToolWithNoSocketsIsNeverRead() {
        ApotheosisSockets.install(new FakeBridge(grant("minecraft:generic.attack_damage", 99.0)));
        assertEquals(0.0F, ApotheosisSockets.attackDamageBonus(new ItemStack(Items.STICK)),
                "no sockets means the bridge is never asked, whatever it would have said");
    }

    // ---------------------------------------------------------------- the mapping table

    @Test
    void everyMappingRowIsFilledInAndNamedOnce() {
        Set<String> seen = new HashSet<>();
        for (GemEffect effect : ApotheosisSockets.EFFECT_MAP) {
            assertTrue(seen.add(effect.bonus()), "two rows for " + effect.bonus());
            assertFalse(effect.bonus().isBlank(), "a row with no bonus id");
            assertFalse(effect.hook().isBlank(), effect.bonus() + " names no hook");
            assertFalse(effect.note().isBlank(), effect.bonus() + " gives no reason");
        }
        assertEquals(ApotheosisSockets.EFFECT_MAP.size(), ApotheosisSockets.EFFECTS_BY_BONUS.size());
        // Non-vacuity: Apotheosis 8.7.0's GemBonus.initCodecs registers fourteen bonus types, and the
        // table adds one row for the getDamageProtection base hook no shipped type overrides.
        assertEquals(15, ApotheosisSockets.EFFECT_MAP.size(),
                "a bonus type Apotheosis added or dropped needs a row here, mapped or not");
    }

    @Test
    void everyMappedQuantityIsOneThisClassActuallyFeeds() {
        Set<Quantity> reached = EnumSet.noneOf(Quantity.class);
        for (GemEffect effect : ApotheosisSockets.EFFECT_MAP) {
            if (effect.mapped()) {
                reached.addAll(effect.quantities());
            }
        }
        assertEquals(EnumSet.allOf(Quantity.class), reached,
                "a quantity in the enum with no row pointing at it is a seam nothing reaches");
    }

    @Test
    void everyUnmappedEffectIsListedRatherThanSilentlyDropped() {
        List<GemEffect> unmapped = ApotheosisSockets.EFFECT_MAP.stream()
                .filter(effect -> !effect.mapped())
                .toList();
        assertFalse(unmapped.isEmpty(), "some of Apotheosis' bonus types have no Forgeweave seam");
        for (GemEffect effect : unmapped) {
            assertTrue(effect.quantities().isEmpty());
            assertTrue(effect.note().length() > 20,
                    effect.bonus() + " is unmapped without saying why, which D-M8-1 does not allow");
        }
        for (String bonus : List.of("apotheosis:enchantment", "apotheosis:drop_transform",
                "apotheosis:frozen_drops", "apotheosis:leech_block", "apotheosis:bloody_arrow",
                "apotheosis:omnetic", "apotheosis:radial")) {
            GemEffect effect = ApotheosisSockets.EFFECTS_BY_BONUS.get(bonus);
            assertNotNull(effect, bonus + " has no row at all");
            assertFalse(effect.mapped(), bonus + " is recorded as unmapped and must stay that way "
                    + "until a seam for it exists");
        }
    }

    // ---------------------------------------------------------------- the shipped recipes

    /**
     * Read as raw JSON rather than decoded: both shipped recipes name Apotheosis items, which no
     * unit-test item registry holds. In game their {@code neoforge:conditions} gate is evaluated
     * before the entry is decoded, so a Forgeweave-only server never reaches the ingredient either.
     */
    @Test
    void theShippedRecipesCapSocketsAtTheSameNumberTheCodeDoes() {
        JsonObject socketed = shippedJson("socketed");
        assertEquals("forgeweave:socketed", socketed.get("modifier").getAsString());
        assertEquals(ApotheosisSockets.MAX_SOCKETS, socketed.get("max_level").getAsInt(),
                "the recipe's cap and MAX_SOCKETS are the same number stated twice");
        assertTrue(socketed.has("neoforge:conditions"),
                "the socket recipe must drop itself without Apotheosis");

        JsonObject seat = shippedJson("socket_gem");
        assertEquals("forgeweave:socket_gem", seat.get("modifier").getAsString());
        assertTrue(seat.has("neoforge:conditions"), "so must the gem recipe");
    }

    private static JsonObject shippedJson(String name) {
        String path = "/data/forgeweave/forgeweave/modifier_recipe/" + name + ".json";
        try (InputStream in = ApotheosisSocketTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "missing shipped modifier recipe: " + path);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            throw new AssertionError("could not read " + path, e);
        }
    }

    // ---------------------------------------------------------------- fixtures

    /** A stack carrying {@code level} of the socketed modifier and nothing else. */
    private static ItemStack socketed(int level) {
        ItemStack stack = new ItemStack(Items.STICK);
        stack.set(ForgeweaveDataComponents.MODIFIERS.get(),
                List.of(new ModifierEntry(ApotheosisSockets.SOCKETED_ID, level)));
        return stack;
    }

    private static AttributeGrant grant(String attribute, double amount) {
        return new AttributeGrant(ResourceLocation.parse(attribute),
                new AttributeModifier(ResourceLocation.parse("forgeweave:test_gem"), amount,
                        AttributeModifier.Operation.ADD_VALUE));
    }

    /**
     * Answers whatever it was built with and nothing else, so each test above names only the half of
     * the bridge it is about -- the shape {@code DraconicModules.Bridge}'s defaults already give.
     */
    private static class FakeBridge implements ApotheosisSockets.Bridge {

        private final List<AttributeGrant> grants;

        FakeBridge(AttributeGrant... grants) {
            this.grants = List.copyOf(new ArrayList<>(List.of(grants)));
        }

        @Override
        public boolean canSeat(ItemStack target, ItemStack gem) {
            return true;
        }

        @Override
        public List<AttributeGrant> attributeGrants(ItemStack stack) {
            return grants;
        }

        @Override
        public float durabilityFraction(ItemStack stack) {
            return 0.0F;
        }

        @Override
        public float protection(ItemStack stack, DamageSource source) {
            return 0.0F;
        }

        @Override
        public float reduceDamage(ItemStack stack, DamageSource source, LivingEntity defender, float amount) {
            return amount;
        }

        @Override
        public void afterAttack(ItemStack stack, LivingEntity attacker, @Nullable Entity target) {}

        @Override
        public void afterHurt(ItemStack stack, LivingEntity defender, DamageSource source) {}
    }
}
