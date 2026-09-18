package dev.gkissel.forgeweave.compat.apotheosis;

import net.neoforged.fml.ModList;

import dev.gkissel.forgeweave.config.ForgeweaveConfig;

/**
 * The two toggles behind Apotheosis loot affixes and Apotheosis enchanting on Forgeweave gear
 * (issue #970, docs/SCOPE.md M8, D-M8-1 and D-M8-5).
 *
 * <p>Like {@link ApotheosisSockets} and unlike {@link ApotheosisGemBonuses}, this class names no
 * {@code dev.shadowsoffire} type, so it is classloaded on every install and its callers need no
 * {@code ModList} guard of their own: {@code ToolItem} and {@code ArmorPieceItem} ask
 * {@link #enchantingEnabled()} on every enchantability check, and {@code loot.AssembleTool} asks
 * {@link #affixesEnabled()} on every loot roll. Both answer "on" when the mod that owns the feature
 * is absent, so a toggle for an integration nobody installed never changes base gameplay.
 *
 * <h2>What Forgeweave has to register for affixes: nothing</h2>
 *
 * <p>Verified against Apotheosis {@code 1.21.1-8.7.0}. Affixability is not a tag, a capability or a
 * registration: every entry point ({@code LootController.createLootItem},
 * {@code AffixConvertLootModifier.doApply}, both {@code ReforgingMenu} slot filters) asks
 * {@code LootCategory.forItem(stack).isNone()}, and {@code forItem} consults the
 * {@code apotheosis:loot_category_overrides} item data map and then walks the shipped categories'
 * own {@code Predicate<ItemStack>}s in ascending priority order. A Forgeweave item that satisfies one
 * of those predicates is affixable the moment Apotheosis is installed, with no Forgeweave code
 * involved, and most of them do:
 *
 * <ul>
 *   <li>the pickaxe, hammer, vein hammer, shovel, excavator and mattock answer
 *       {@code ItemAbilities.PICKAXE_DIG} or {@code SHOVEL_DIG}, so they land in
 *       {@code apotheosis:breaker};
 *   <li>every {@code sword_efficient} weapon answers {@code SWORD_DIG}, so it lands in
 *       {@code apotheosis:melee_weapon};
 *   <li>the axes, the hoe-family tools and the three tools with no mineable tag at all (battlesign,
 *       frying pan, warmace) reach {@code melee_weapon} through that category's second test, a
 *       positive attack damage modifier. This is also where a vanilla axe and a vanilla hoe land,
 *       since neither answers a breaker ability either;
 *   <li>{@code ArmorPieceItem} extends {@code ArmorItem}, so all eight armor pieces match their own
 *       slot's category through {@code Equipable.get}.
 * </ul>
 *
 * <p>Two consequences worth stating rather than discovering. A tool that is unassembled or Broken has
 * no attack damage ({@code ToolItem.getDefaultAttributeModifiers} returns
 * {@code ItemAttributeModifiers.EMPTY}) and answers no ability ({@code ToolItem.canPerformAction}
 * refuses outright), so it is {@code apotheosis:none} and cannot be affixed. That is the right
 * answer: a bare Forgeweave tool is a box of parts, not gear. It is also why
 * {@code loot.AssembleTool} exists, because a loot table that hands out the registered item alone
 * hands out something Apotheosis will not touch.
 *
 * <p>The one family the predicate walk gets wrong is the ranged one. Apotheosis' {@code bow}
 * category tests {@code getItem() instanceof BowItem || CrossbowItem}, and Forgeweave's
 * {@code item.BowItem} extends {@code ToolItem} rather than either vanilla class, on purpose and for
 * the reason {@code ForgeweaveItems} gives for every other vanilla tool class it declined. So a
 * Forgeweave bow never matches {@code bow}: assembled it falls through to {@code melee_weapon} on its
 * melee attack damage and would roll sword affixes, and bare it is {@code none}.
 * {@code data/apotheosis/data_maps/item/loot_category_overrides.json} names exactly the shortbow,
 * longbow and crossbow and nothing else, through Apotheosis' own documented override seam. Every
 * other shape is left to the walk, which lands each one where the vanilla tool of the same kind
 * lands: an axe and a hoe are {@code melee_weapon} in a vanilla world too, for the same missing
 * breaker ability.
 *
 * <p>The ammunition, the shuriken and the material arrow, stays {@code none}. Both are
 * {@code AmmoToolItem}, whose attribute modifiers are empty whatever its parts, and Apotheosis leaves
 * a vanilla arrow alone as well: ammunition is not gear.
 *
 * <h2>What the affix toggle can and cannot reach</h2>
 *
 * <p>Apotheosis exposes no runtime seam for affix eligibility. Its only three public events are
 * gem-socket events, nothing under its {@code affix} or {@code loot} packages touches an event bus,
 * and {@code forItem} calls no {@code Item} method a mod could override for the purpose. So the
 * override file above is datapack scope: a server config cannot add or remove it, and a pack that
 * wants it gone overrides or deletes it. What {@link #affixesEnabled()} does reach is the one
 * runtime point Forgeweave owns in the affix path, {@code AssembleTool}: with the toggle off no loot
 * roll assembles a Forgeweave tool, so no newly looted Forgeweave gear is affixable, because a bare
 * tool is {@code apotheosis:none}.
 *
 * <p>Off never destroys anything. Forgeweave neither reads, copies nor writes Apotheosis' affix
 * components (JC-D), so a tool that already carries them keeps every one of them with either toggle
 * in either position. Whether those affixes still <em>apply</em> is Apotheosis' own call and not
 * Forgeweave's to switch off: {@code AffixHelper.getAffixesImpl} re-resolves the loot category on
 * every read and drops affixes whose category no longer matches, which is how Apotheosis makes an
 * affix inert, and it is reachable only through the same datapack override.
 *
 * <h2>Enchanting</h2>
 *
 * <p>Enchanting needs both {@code allowVanillaEnchanting} and {@code apotheosisEnchanting}, which is
 * D-M8-1's rule and not a new one: the integration toggle does not replace the gameplay flag.
 * Forgeweave needs no new code to reach Apotheosis' table. The table is Apothic Enchanting's, a
 * separate optional mod ({@code apothic_enchanting}) that Apotheosis 8.7.0 ships no enchanting module
 * of its own for, and it replaces the vanilla enchanting table block outright
 * ({@code mixin.BlocksMixin.apoth_overrideEnchTableBlock}) and gates its input slot on
 * {@code stack.getItem().isEnchantable(stack)} ({@code table.ApothEnchantmentMenu.slotsChanged},
 * verified against {@code 1.21.1-1.6.2}). That is the same method {@code allowVanillaEnchanting}
 * already gates, so the existing flag already governs Apotheosis' table, and folding
 * {@link #enchantingEnabled()} into the same two overrides is the whole of the integration.
 *
 * <p>Because the mixin replaces the vanilla block, a world with Apothic Enchanting has one
 * enchanting table and it is Apotheosis'. So with {@code apotheosisEnchanting} off a Forgeweave tool
 * is refused there exactly as {@code allowVanillaEnchanting = false} refuses it: no enchantment is
 * stripped, nothing stored changes, and an already enchanted tool keeps its enchantments and keeps
 * applying them. An enchantment costs no modifier slot and never has, so nothing about the slot
 * budget moves either way.
 */
public final class ApotheosisAffixes {

    /**
     * The mod that owns the enchanting table, which is not Apotheosis. Apotheosis 8.7.0 declares it
     * an optional {@code AFTER} dependency and ships no enchanting module itself, so this is the id
     * to ask about before letting {@code apotheosisEnchanting} refuse an enchant.
     */
    public static final String ENCHANTING_MODID = "apothic_enchanting";

    private ApotheosisAffixes() {
    }

    /**
     * Whether Forgeweave assembles a tool for a loot roll, and so whether newly looted Forgeweave
     * gear can carry an affix. Answers "on" with Apotheosis absent, so a Forgeweave-only pack using
     * {@code forgeweave:assemble_tool} for its own loot is never affected by this toggle.
     */
    public static boolean affixesEnabled() {
        return !ModList.get().isLoaded(ApotheosisSockets.MODID) || affixesConfigured();
    }

    /**
     * Whether an enchanting table or an anvil may take Forgeweave gear at all, the
     * {@code apotheosisEnchanting} half of the pair. The {@code allowVanillaEnchanting} half stays
     * where it is, in the two {@code isEnchantable} overrides that read this. Answers "on" with
     * Apothic Enchanting absent, because there is no Apotheosis table to refuse the item at.
     */
    public static boolean enchantingEnabled() {
        return !ModList.get().isLoaded(ENCHANTING_MODID) || enchantingConfigured();
    }

    /**
     * The {@code apotheosisAffixes} toggle on its own, without the mod-absence guard above.
     *
     * <p>Split out because the guard is exactly what makes the toggle untestable in a GameTest: no
     * compat mod is on that classpath (JC-B), so {@link #affixesEnabled()} answers "on" there
     * whatever the toggle says, which is the behaviour it is supposed to have. The config-off test
     * D-M8-5 asks for drives this method and asserts the guard separately, rather than pretending to
     * exercise a bridge that is not installed.
     */
    public static boolean affixesConfigured() {
        return ForgeweaveConfig.enabled(ForgeweaveConfig.APOTHEOSIS_AFFIXES);
    }

    /** The {@code apotheosisEnchanting} toggle on its own -- see {@link #affixesConfigured()}. */
    public static boolean enchantingConfigured() {
        return ForgeweaveConfig.enabled(ForgeweaveConfig.APOTHEOSIS_ENCHANTING);
    }
}
