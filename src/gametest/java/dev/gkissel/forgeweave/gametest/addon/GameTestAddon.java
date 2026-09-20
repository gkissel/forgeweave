package dev.gkissel.forgeweave.gametest.addon;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.api.modifier.Modifier;
import dev.gkissel.forgeweave.api.modifier.ModifierRegistry;
import dev.gkissel.forgeweave.api.tool.ForgeweaveTools;
import dev.gkissel.forgeweave.api.tool.PartDefinition;
import dev.gkissel.forgeweave.api.tool.PartKind;
import dev.gkissel.forgeweave.api.tool.PartRole;
import dev.gkissel.forgeweave.api.tool.ToolDefinition;
import dev.gkissel.forgeweave.api.tool.ToolFamily;
import dev.gkissel.forgeweave.api.trait.Trait;
import dev.gkissel.forgeweave.api.trait.TraitRegistry;
import dev.gkissel.forgeweave.api.upgrade.UpgradeHosts;

/**
 * A stand-in for another mod's addon, and the proof that the entry points #1008 opened are ones
 * another mod can actually build against. It reaches Forgeweave through
 * {@code dev.gkissel.forgeweave.api} and nothing else: every other Forgeweave class it could import
 * is off limits to it on purpose, so a table it reaches has to have been opened rather than merely
 * package-visible.
 *
 * <p>Every Java seam the api package offers is exercised here once, and {@code docs/addons.md}
 * quotes this file for its Java examples, so what the guide shows is what the build compiles.
 * The datapack half lives in {@code src/gametest/resources/data/gametest_addon/}.
 *
 * <p>It lives in the GameTest-only {@code gametest} source set, which is folded into the mod's file
 * list for the {@code gameTestServer} run alone (see build.gradle and
 * {@code src/gametest/resources/README.md}), so nothing here reaches a dev run, a data run or the
 * published jar. {@code RegisteredToolGameTests} is what drives it.
 *
 * <p>A second {@code @Mod} entry point under Forgeweave's own id rather than a mod id of its own:
 * that is all a separate {@code neoforge.mods.toml} would buy, and it would buy it by making the
 * GameTest run load a second mod. Its items are registered in the {@code gametest_addon} namespace
 * anyway, which is the part that matters -- every id the stations carry here is foreign.
 */
@Mod(Forgeweave.MODID)
public final class GameTestAddon {

    /** The namespace the addon's items live in, which is deliberately not Forgeweave's. */
    public static final String NAMESPACE = "gametest_addon";

    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(NAMESPACE);

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(NAMESPACE);

    /** The part's own id, which is what {@link #TOOL_DEFINITION}'s head slot names. */
    public static final ResourceLocation BLADE_ID = ResourceLocation.fromNamespaceAndPath(NAMESPACE, "test_blade");

    /** The tool's id, and so the base of the texture and lang keys an addon would ship art under. */
    public static final ResourceLocation TOOL_ID = ResourceLocation.fromNamespaceAndPath(NAMESPACE, "test_dirk");

    /** The pattern that stamps {@link #BLADE} at the Part Builder and shows at the Stencil Table. */
    public static final DeferredItem<Item> PATTERN = ITEMS.registerItem("pattern_test_blade", Item::new);

    public static final DeferredItem<Item> BLADE =
            ITEMS.registerItem("test_blade", properties -> ForgeweaveTools.partItem(PartKind.HEAD, properties));

    /** Two slots, one part foreign and one of Forgeweave's own, so the mix is covered too. */
    public static final ToolDefinition TOOL_DEFINITION = ToolDefinition.builder(TOOL_ID, ToolFamily.MELEE)
            .part(PartRole.HEAD, BLADE_ID)
            .part(PartRole.HANDLE, ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "tool_handle"))
            .weapon()
            .attackSpeed(1.6f)
            .flatAttackBonus(2.0f)
            .build();

    public static final DeferredItem<Item> TOOL =
            ITEMS.registerItem("test_dirk", properties -> ForgeweaveTools.toolItem(TOOL_DEFINITION, properties));

    /** What one blade costs, in the units {@link ForgeweaveTools#INGOT_VALUE} denominates. */
    public static final int BLADE_COST = 2 * ForgeweaveTools.INGOT_VALUE;

    /**
     * A block the addon owns and Forgeweave has never heard of. The addon's own tag file puts it in
     * {@code forgeweave:smeltery/wall_addon}, which the shipped {@code smeltery/wall} tag references,
     * so the smeltery scan takes it as a wall (issue #1067).
     */
    public static final DeferredBlock<Block> WALL_BLOCK = BLOCKS.registerSimpleBlock("addon_wall",
            BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(3.0F).sound(SoundType.STONE));

    /** The trait behavior an addon ships so packs can parameterize it from a definition file. */
    public static final ResourceLocation TRAIT_BEHAVIOR_ID =
            ResourceLocation.fromNamespaceAndPath(NAMESPACE, "flat_attack_bonus");

    /** The modifier behavior, the same idea one registry over. */
    public static final ResourceLocation MODIFIER_BEHAVIOR_ID =
            ResourceLocation.fromNamespaceAndPath(NAMESPACE, "flat_attack_bonus");

    /** A finished modifier, registered from Java rather than parameterized from a file. */
    public static final ResourceLocation MODIFIER_ID = ResourceLocation.fromNamespaceAndPath(NAMESPACE, "whetted");

    /** How much attack damage {@link #MODIFIER_ID} adds per level. */
    public static final float WHETTED_PER_LEVEL = 3.0F;

    /**
     * A trait whose one number a {@code trait_definition} file supplies. The behavior type is Java
     * and the parameters are data, which is the split {@code TraitRegistry#registerBehavior} exists
     * for: one registration serves every pack that installs the addon.
     */
    public record FlatAttackBonus(float amount) implements Trait {

        public static final MapCodec<FlatAttackBonus> CODEC =
                Codec.FLOAT.fieldOf("amount").xmap(FlatAttackBonus::new, FlatAttackBonus::amount);

        @Override
        public float attackDamageBonus(ItemStack stack) {
            return amount;
        }
    }

    /** The modifier-side twin of {@link FlatAttackBonus}, over {@code modifier_definition}. */
    public record FlatAttackModifier(float amount) implements Modifier {

        public static final MapCodec<FlatAttackModifier> CODEC =
                Codec.FLOAT.fieldOf("amount").xmap(FlatAttackModifier::new, FlatAttackModifier::amount);

        @Override
        public float attackDamage(int level, float attackDamage, float baseAttackDamage) {
            return attackDamage + amount * level;
        }
    }

    /** {@link #MODIFIER_ID}'s behavior: all Java, nothing to parameterize. */
    public static final Modifier WHETTED = new Modifier() {
        @Override
        public float attackDamage(int level, float attackDamage, float baseAttackDamage) {
            return attackDamage + WHETTED_PER_LEVEL * level;
        }
    };

    /**
     * The upgrade host, which the stations ask on every part swap. This one is inert for every swap
     * that can really happen -- a swap rebuilds the same tool item, so {@code replacement} is the
     * addon's tool whenever {@code original} is -- and only answers the direct call the GameTest
     * makes. A real integration would key it on its own module component instead.
     */
    public static UpgradeHosts.Host host() {
        return (original, replacement) -> original.is(TOOL.get()) && !replacement.is(TOOL.get())
                ? List.of(new ItemStack(PATTERN.get()))
                : List.of();
    }

    public GameTestAddon(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        BLOCKS.register(modEventBus);
        ForgeweaveTools.registerPart(PartDefinition.stamped(BLADE_ID, PartKind.HEAD, BLADE, PATTERN, BLADE_COST));
        ForgeweaveTools.registerTool(TOOL_DEFINITION, TOOL);
        TraitRegistry.registerBehavior(TRAIT_BEHAVIOR_ID, FlatAttackBonus.CODEC);
        ModifierRegistry.registerBehavior(MODIFIER_BEHAVIOR_ID, FlatAttackModifier.CODEC);
        ModifierRegistry.register(MODIFIER_ID, WHETTED);
        UpgradeHosts.register(host());
    }
}
