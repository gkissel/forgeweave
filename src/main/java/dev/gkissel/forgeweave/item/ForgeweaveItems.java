package dev.gkissel.forgeweave.item;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.block.SlimeColour;
import dev.gkissel.forgeweave.combat.ForgeweaveInnates;
import dev.gkissel.forgeweave.entity.ForgeweaveEntities;
import dev.gkissel.forgeweave.tool.AoeHarvest;
import dev.gkissel.forgeweave.tool.ToolConstants;
import dev.gkissel.forgeweave.trackb.TrackBAlloy;
import dev.gkissel.forgeweave.trackb.TrackBOre;

/**
 * The blank pattern, the five part patterns, the five part items, and the Part Builder block item
 * (CONTEXT.md glossary; docs/SCOPE.md M1 content manifest). Patterns and parts all stack normally:
 * upstream 1.12's {@code library/tools/Pattern} never calls {@code setMaxStackSize}, so every
 * pattern -- blank and part alike -- stacks to the vanilla 64 there, and neither patterns nor parts
 * carry per-instance state beyond a plain data component (issue #64).
 *
 * <p>Each {@link PartItem} declares the {@link PartItem.Kind} it plays in a build, which is what its
 * tooltip shows stats for -- upstream derives the same thing from the {@code PartMaterialType}s the
 * part appears in, but every Forgeweave part appears in exactly one role.
 */
public final class ForgeweaveItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Forgeweave.MODID);

    public static final DeferredItem<Item> PATTERN_BLANK = ITEMS.registerSimpleItem("pattern_blank");

    // The workshop guide book (issue #273), upstream 1.12's `tconstruct:book` "Materials and You";
    // crafted from a vanilla book plus a blank pattern (recipes/tools/book.json, NOTICE.md).
    public static final DeferredItem<GuideBookItem> GUIDE_BOOK = ITEMS.registerItem("guide_book", GuideBookItem::new);
    public static final DeferredItem<Item> PATTERN_PICKAXE_HEAD = pattern("pattern_pickaxe_head");
    public static final DeferredItem<Item> PATTERN_SHOVEL_HEAD = pattern("pattern_shovel_head");
    public static final DeferredItem<Item> PATTERN_AXE_HEAD = pattern("pattern_axe_head");
    public static final DeferredItem<Item> PATTERN_TOOL_BINDING = pattern("pattern_tool_binding");
    public static final DeferredItem<Item> PATTERN_TOOL_HANDLE = pattern("pattern_tool_handle");

    // M3 tool part patterns (docs/SCOPE.md M3 issue #151), one per part below.
    public static final DeferredItem<Item> PATTERN_SWORD_BLADE = pattern("pattern_sword_blade");
    public static final DeferredItem<Item> PATTERN_WIDE_GUARD = pattern("pattern_wide_guard");
    public static final DeferredItem<Item> PATTERN_HAND_GUARD = pattern("pattern_hand_guard");
    public static final DeferredItem<Item> PATTERN_CROSS_GUARD = pattern("pattern_cross_guard");
    public static final DeferredItem<Item> PATTERN_SIGN_PLATE = pattern("pattern_sign_plate");
    public static final DeferredItem<Item> PATTERN_PAN = pattern("pattern_pan");
    public static final DeferredItem<Item> PATTERN_KNIFE_BLADE = pattern("pattern_knife_blade");
    public static final DeferredItem<Item> PATTERN_LARGE_SWORD_BLADE = pattern("pattern_large_sword_blade");
    public static final DeferredItem<Item> PATTERN_TOUGH_TOOL_ROD = pattern("pattern_tough_tool_rod");
    public static final DeferredItem<Item> PATTERN_TOUGH_BINDING = pattern("pattern_tough_binding");
    public static final DeferredItem<Item> PATTERN_LARGE_PLATE = pattern("pattern_large_plate");
    public static final DeferredItem<Item> PATTERN_HAMMER_HEAD = pattern("pattern_hammer_head");
    public static final DeferredItem<Item> PATTERN_EXCAVATOR_HEAD = pattern("pattern_excavator_head");
    public static final DeferredItem<Item> PATTERN_SCYTHE_HEAD = pattern("pattern_scythe_head");
    public static final DeferredItem<Item> PATTERN_KAMA_HEAD = pattern("pattern_kama_head");
    public static final DeferredItem<Item> PATTERN_BROAD_AXE_HEAD = pattern("pattern_broad_axe_head");
    public static final DeferredItem<Item> PATTERN_VEIN_HAMMER_HEAD = pattern("pattern_vein_hammer_head");
    // #161's own new-shape head part; see PART_WAR_MACE_HEAD below.
    public static final DeferredItem<Item> PATTERN_WAR_MACE_HEAD = pattern("pattern_war_mace_head");
    // #159: the scimitar's own head part. See PART_CURVED_BLADE below for why it lands here rather
    // than with #151's batch.
    public static final DeferredItem<Item> PATTERN_CURVED_BLADE = pattern("pattern_curved_blade");
    // #160's katana blade -- see PART_KATANA_BLADE below for why it lands here rather than in #151.
    public static final DeferredItem<Item> PATTERN_KATANA_BLADE = pattern("pattern_katana_blade");
    // M3.5 (docs/SCOPE.md M3.5, issue #393): the bow's two parts, patterned like every other.
    public static final DeferredItem<Item> PATTERN_BOW_LIMB = pattern("pattern_bow_limb");
    public static final DeferredItem<Item> PATTERN_BOW_STRING = pattern("pattern_bow_string");
    // #626 (parity audit T17): the arrow's three parts, stencilled like every other
    // (upstream TinkerTools#registerItems registers all three right after the bow pair's).
    public static final DeferredItem<Item> PATTERN_ARROW_HEAD = pattern("pattern_arrow_head");
    public static final DeferredItem<Item> PATTERN_ARROW_SHAFT = pattern("pattern_arrow_shaft");
    public static final DeferredItem<Item> PATTERN_FLETCHING = pattern("pattern_fletching");
    // #677 (M4-2): the four platings and the maille, patterned like every other part.
    public static final DeferredItem<Item> PATTERN_PLATING_HELMET = pattern("pattern_plating_helmet");
    public static final DeferredItem<Item> PATTERN_PLATING_CHESTPLATE = pattern("pattern_plating_chestplate");
    public static final DeferredItem<Item> PATTERN_PLATING_LEGGINGS = pattern("pattern_plating_leggings");
    public static final DeferredItem<Item> PATTERN_PLATING_BOOTS = pattern("pattern_plating_boots");
    public static final DeferredItem<Item> PATTERN_MAILLE = pattern("pattern_maille");
    // #271's sharpening kit. Upstream stencils it like any other part
    // (TinkerTools#registerItems: registerStencilTableCrafting(Pattern.setTagForPart(pattern, sharpeningKit))).
    public static final DeferredItem<Item> PATTERN_SHARPENING_KIT = pattern("pattern_sharpening_kit");
    // #605: the shard's own pattern. Upstream stencils it on the very next line after the sharpening
    // kit's (TinkerTools#registerItems:154, registerStencilTableCrafting(... shard)), and Shard
    // extends ToolPart with a VALUE_Shard cost -- the only part in the roster that costs less than a
    // whole ingot, and so the only one at which a plain ingot leaves change.
    public static final DeferredItem<Item> PATTERN_SHARD = pattern("pattern_shard");

    public static final DeferredItem<PartItem> PART_PICKAXE_HEAD = part("pickaxe_head", PartItem.Kind.HEAD);
    public static final DeferredItem<PartItem> PART_SHOVEL_HEAD = part("shovel_head", PartItem.Kind.HEAD);
    public static final DeferredItem<PartItem> PART_AXE_HEAD = part("axe_head", PartItem.Kind.HEAD);
    public static final DeferredItem<PartItem> PART_TOOL_BINDING = part("tool_binding", PartItem.Kind.EXTRA);
    public static final DeferredItem<PartItem> PART_TOOL_HANDLE = part("tool_handle", PartItem.Kind.HANDLE);

    // M3 tool parts (docs/SCOPE.md M3 issue #151): the roster's part list, exactly as read off the
    // clone's per-tool constructors (tools/melee/item/*, tools/tools/*) plus the two modern-branch
    // shapes docs/SCOPE.md authorizes (knife blade for the dagger, vein hammer head for the vein
    // hammer -- see NOTICE.md and ForgeweaveItemModelProvider for which of these have upstream art
    // and which don't). Not registered: distinct heads for scimitar/katana/warmace -- those three are
    // Forgeweave's own new-shape tools (docs/SCOPE.md: "new modern-era shapes, ours") with no clone
    // part list to read and no design decided yet; their own issues (#159/#160/#161) are where the
    // maintainer picks their part composition, so inventing parts for them here would be speculative.
    public static final DeferredItem<PartItem> PART_SWORD_BLADE = part("sword_blade", PartItem.Kind.HEAD);
    public static final DeferredItem<PartItem> PART_WIDE_GUARD = part("wide_guard", PartItem.Kind.EXTRA);
    public static final DeferredItem<PartItem> PART_HAND_GUARD = part("hand_guard", PartItem.Kind.EXTRA);
    public static final DeferredItem<PartItem> PART_CROSS_GUARD = part("cross_guard", PartItem.Kind.EXTRA);
    public static final DeferredItem<PartItem> PART_SIGN_PLATE = part("sign_plate", PartItem.Kind.HEAD);
    public static final DeferredItem<PartItem> PART_PAN = part("pan", PartItem.Kind.HEAD);
    public static final DeferredItem<PartItem> PART_KNIFE_BLADE = part("knife_blade", PartItem.Kind.HEAD);
    public static final DeferredItem<PartItem> PART_LARGE_SWORD_BLADE = part("large_sword_blade", PartItem.Kind.HEAD);
    public static final DeferredItem<PartItem> PART_TOUGH_TOOL_ROD = part("tough_tool_rod", PartItem.Kind.HANDLE);
    public static final DeferredItem<PartItem> PART_TOUGH_BINDING = part("tough_binding", PartItem.Kind.EXTRA);
    public static final DeferredItem<PartItem> PART_LARGE_PLATE = part("large_plate", PartItem.Kind.HEAD);
    public static final DeferredItem<PartItem> PART_HAMMER_HEAD = part("hammer_head", PartItem.Kind.HEAD);
    public static final DeferredItem<PartItem> PART_EXCAVATOR_HEAD = part("excavator_head", PartItem.Kind.HEAD);
    public static final DeferredItem<PartItem> PART_SCYTHE_HEAD = part("scythe_head", PartItem.Kind.HEAD);
    public static final DeferredItem<PartItem> PART_KAMA_HEAD = part("kama_head", PartItem.Kind.HEAD);
    public static final DeferredItem<PartItem> PART_BROAD_AXE_HEAD = part("broad_axe_head", PartItem.Kind.HEAD);
    // No 1.12 or 1.20 counterpart (1.20's vein hammer reuses the plain hammer_head part). Issue #198
    // replaced this part's once-freshly-authored icon with the already-derived vein hammer tool-layer
    // pixels (derived/tools/vein_hammer_head.png, issue #151/#157, NOTICE.md) -- see
    // scripts/derive_m3_weapon_art.py.
    public static final DeferredItem<PartItem> PART_VEIN_HAMMER_HEAD = part("vein_hammer_head", PartItem.Kind.HEAD);
    // #161: the warmace's head, one of the three new-shape heads #151 deliberately left
    // unregistered ("their own issues are where the maintainer picks their part composition").
    // The composition is ToolConstants#WARMACE's -- tough tool rod, this head, tough binding.
    // Neither clone has a mace-alike, so per issue #198's decision the art is derived from the
    // closest upstream equivalent -- the 1.12 hammer's head, minimally reshaped into a round knob
    // (scripts/derive_warmace_art.py, NOTICE.md) -- rather than freshly authored.
    public static final DeferredItem<PartItem> PART_WAR_MACE_HEAD = part("war_mace_head", PartItem.Kind.HEAD);
    // #159: the scimitar's head part -- the "distinct head for scimitar/katana/warmace" the comment
    // above deferred to those tools' own issues. ToolConstants#SCIMITAR (issue #153) already names it
    // `curved_blade`, and #159 is the issue that decides the scimitar, so it lands here. Neither
    // clone has a scimitar/curved-blade shape, so per issue #198's decision the art derives from the
    // closest upstream equivalent -- the 1.12 sword blade, widened toward the tip by a minimal
    // reshape (scripts/derive_m3_weapon_art.py, NOTICE.md) -- rather than freshly authored.
    public static final DeferredItem<PartItem> PART_CURVED_BLADE = part("curved_blade", PartItem.Kind.HEAD);
    // The katana's head part (docs/SCOPE.md M3 issue #160). #151 deliberately left the three
    // new-shape tools' heads unregistered until their own issues decided a part composition;
    // ToolConstants#KATANA names `katana_blade` + the existing hand_guard + tool_handle, so this is
    // the one part that issue adds. No 1.12 or 1.20 katana exists either, so per issue #198's
    // decision this reuses the 1.12 large sword blade unmodified -- the closest upstream equivalent's
    // silhouette already reads as a long straight blade (scripts/derive_m3_weapon_art.py, NOTICE.md).
    public static final DeferredItem<PartItem> PART_KATANA_BLADE = part("katana_blade", PartItem.Kind.HEAD);

    /**
     * The bow's limb (docs/SCOPE.md M3.5, issue #393), upstream's {@code TinkerTools#bowLimb}
     * ({@code TinkerTools.java:210}, {@code new ToolPart(Material.VALUE_Ingot * 3)}). {@link
     * PartItem.Kind#BOW} is the stat block upstream's {@code PartMaterialType.bow(bowLimb)} reads
     * for it in all three of its bows ({@code ShortBow}, {@code LongBow}, {@code CrossBow}); issue
     * #392 added that block to the material model. The bows themselves are M3.5-3's issue -- this
     * part exists before them the same way #151's roster preceded its tools.
     */
    public static final DeferredItem<PartItem> PART_BOW_LIMB = part("bow_limb", PartItem.Kind.BOW);

    /**
     * The bow's string (issue #393), upstream's {@code TinkerTools#bowString}
     * ({@code TinkerTools.java:211}, {@code new ToolPart(Material.VALUE_Ingot)}), read through
     * {@code PartMaterialType.bowstring(bowString)} -- {@link PartItem.Kind#BOWSTRING}.
     *
     * <p>The one part with no cast: see {@code CAST_BOW_LIMB} below.
     */
    public static final DeferredItem<PartItem> PART_BOW_STRING = part("bow_string", PartItem.Kind.BOWSTRING);

    /**
     * The arrow's head (issue #626, parity audit T17), upstream's {@code TinkerTools#arrowHead}
     * ({@code TinkerTools.java:213}, {@code new ToolPart(Material.VALUE_Ingot * 2)}). {@link
     * PartItem.Kind#HEAD}, not a kind of its own: upstream's {@code PartMaterialType.arrowHead}
     * names {@code HEAD, PROJECTILE} as its stat types, and PROJECTILE is a stat every HEAD
     * material carries for free ({@code TinkerRegistry#addMaterialStats:261} auto-adds it to any
     * material given head stats), so "has HEAD stats" is the whole gate -- the same one every other
     * head part asks. The arrow itself is #626's follow-up, riding #448's {@code ProjectileCore}
     * layer; this part exists before it the same way #393's bow parts preceded the bows.
     */
    public static final DeferredItem<PartItem> PART_ARROW_HEAD = part("arrow_head", PartItem.Kind.HEAD);

    /**
     * The arrow's shaft (issue #626), upstream's {@code TinkerTools#arrowShaft}
     * ({@code TinkerTools.java:214}, also {@code VALUE_Ingot * 2}), read through
     * {@code PartMaterialType.arrowShaft} -- {@link PartItem.Kind#SHAFT}, the stat block issue #626
     * added to the material model ({@code ArrowShaftMaterialStats}).
     */
    public static final DeferredItem<PartItem> PART_ARROW_SHAFT = part("arrow_shaft", PartItem.Kind.SHAFT);

    /**
     * The arrow's fletching (issue #626), upstream's {@code TinkerTools#fletching}
     * ({@code TinkerTools.java:215}, also {@code VALUE_Ingot * 2}), read through
     * {@code PartMaterialType.fletching} -- {@link PartItem.Kind#FLETCHING}
     * ({@code FletchingMaterialStats}).
     *
     * <p>Like the bow string, the shaft and fletching have no cast: no molten material carries a
     * SHAFT or FLETCHING stat block, so upstream's {@code registerToolpartMeltingCasting} loop
     * (see {@code CAST_BOW_LIMB}) never registers one -- only {@code CAST_ARROW_HEAD} exists.
     */
    public static final DeferredItem<PartItem> PART_FLETCHING = part("fletching", PartItem.Kind.FLETCHING);

    /**
     * The four armor platings and the maille (issue #677, M4-2; docs/SCOPE.md D9/D12), the 1.20
     * clone's {@code TinkerToolParts#plating} ({@code EnumObject<ArmorItem.Type, ToolPartItem>},
     * one item per piece) and {@code TinkerToolParts#maille}. A plating is the piece's whole stat
     * block ({@link PartItem.Kind#PLATING}); the maille is statless ({@link PartItem.Kind#MAILLE}),
     * traits and the inner texture layer only. Assembly into armor is #678.
     */
    public static final DeferredItem<PartItem> PART_PLATING_HELMET = part("plating_helmet", PartItem.Kind.PLATING);
    public static final DeferredItem<PartItem> PART_PLATING_CHESTPLATE = part("plating_chestplate", PartItem.Kind.PLATING);
    public static final DeferredItem<PartItem> PART_PLATING_LEGGINGS = part("plating_leggings", PartItem.Kind.PLATING);
    public static final DeferredItem<PartItem> PART_PLATING_BOOTS = part("plating_boots", PartItem.Kind.PLATING);
    public static final DeferredItem<PartItem> PART_MAILLE = part("maille", PartItem.Kind.MAILLE);

    /**
     * The sharpening kit (issue #271): the one part that belongs to no tool. Upstream's
     * {@code SharpeningKit} is a {@code ToolPart} registered like any other
     * ({@code TinkerRegistry.registerToolPart}), so it stencils, builds and casts like any other --
     * but no {@code ToolCore}'s required components ever list it, so it can never be assembled into
     * anything. Its only use is spending it (plus a flint) on {@code Fortification}.
     *
     * <p>{@link PartItem.Kind#HEAD} because upstream's {@code canUseMaterial} is
     * {@code mat.hasStats(MaterialTypes.HEAD)} and its tooltip shows the head harvest level -- the
     * kit's whole point is the head-stat tier it carries. That it is <em>not</em> one of
     * {@code ToolConstants}' parts is what {@code Embossing#isDonorPart} keys off to keep it from
     * being an embossing donor, which upstream also forbids (its extra-trait modifiers are generated
     * only over {@code tool.getRequiredComponents()}).
     */
    public static final DeferredItem<PartItem> PART_SHARPENING_KIT = part("sharpening_kit", PartItem.Kind.HEAD);

    /**
     * A part pattern: a plain item apart from {@link PatternItem}'s cost tooltip (issue #379).
     * {@link #PATTERN_BLANK} stays a simple item -- it stamps no part, so it has no cost to quote.
     */
    private static DeferredItem<Item> pattern(String name) {
        return ITEMS.registerItem(name, PatternItem::new);
    }

    private static DeferredItem<PartItem> part(String name, PartItem.Kind kind) {
        return ITEMS.registerItem(name, properties -> new PartItem(properties, kind));
    }

    // The Part Builder's crafting change (issue #45): leftover material value below a part's cost,
    // paid out as shards. One item id shared by every material -- like the parts above, per-material
    // rendering is the MATERIAL data component plus a client-side tint, not a distinct item/texture
    // per material (PartItem already provides that machinery, so this just reuses it).
    public static final DeferredItem<PartItem> SHARD = ITEMS.registerItem("shard", PartItem::new);

    public static final DeferredItem<BlockItem> PART_BUILDER = ITEMS.registerSimpleBlockItem("part_builder", ForgeweaveBlocks.PART_BUILDER);

    // Assembled tools (docs/SCOPE.md M1 issues #10/#11). Deliberately not DiggerItem/TieredItem:
    // a Tier is a fixed table of durability/speed/tier-tag, and a Forgeweave tool's are all derived
    // per stack from its parts' materials, so the vanilla `tool` and `max_damage` components are
    // written at assembly time instead (ToolAssemblyRecipes). What stays fixed per tool type lives
    // here: the mineable/* tag it is meant for, upstream 1.12's attack speed and damage potential
    // (ToolCore#attackSpeed/#damagePotential in tools/tools/{Pickaxe,Shovel,Hatchet}), and whether
    // that class adds Category.WEAPON -- only Hatchet does, and it halves what a hit costs the tool
    // (ToolCore#reduceDurabilityOnHit, see ToolItem#postHurtEnemy).
    // stacksTo(1) like every other Forgeweave equipment item.
    //
    // AoeHarvest.Shape.SINGLE, not NONE (issue #438): upstream's Pickaxe/Shovel/Hatchet all extend
    // AoeToolCore, whose own getAOEBlocks is calcAOEBlocks(..., 1, 1, 1) -- one block, i.e. no extra
    // blocks at all until a Width++/Height++ expander widens that box. SINGLE is that 1x1x1 base, and
    // it is also what makes these Category.AOE tools, the ones upstream's aoeOnly aspect lets the
    // expanders onto.
    public static final DeferredItem<ToolItem> TOOL_PICKAXE = ITEMS.registerItem("pickaxe",
            properties -> new ToolItem(properties, List.of(BlockTags.MINEABLE_WITH_PICKAXE), 1.2f, 1.0f, 1.0f,
                    false, null, AoeHarvest.Shape.SINGLE),
            new Item.Properties().stacksTo(1));
    public static final DeferredItem<ToolItem> TOOL_SHOVEL = ITEMS.registerItem("shovel",
            properties -> new ToolItem(properties, List.of(BlockTags.MINEABLE_WITH_SHOVEL), 1.0f, 0.9f, 1.0f,
                    false, null, AoeHarvest.Shape.SINGLE),
            new Item.Properties().stacksTo(1));
    // HatchetItem, not plain ToolItem: the parity audit's T65 (issue #496) leaf carve-out --
    // full-speed, no-durability-cost leaf digging -- needs a per-block override plain ToolItem can't
    // express. Its own constructor repeats the tag/attack-speed/damage-potential/weapon constants
    // this used to pass here.
    public static final DeferredItem<HatchetItem> TOOL_HATCHET = ITEMS.registerItem("hatchet",
            HatchetItem::new, new Item.Properties().stacksTo(1));

    // M3 station tools (docs/SCOPE.md issue #156): mattock (axe+shovel dual tool, tills soil) and
    // kama (shears, right-click crop harvest). Both take their constants off ToolConstants and their
    // combat rider off ForgeweaveInnates like every other M3 tool; the subclasses exist only for the
    // block/entity right-click behaviors, which no innate hook covers (upstream
    // tools/tools/Mattock.java's Category.HARVEST -> weapon=false; Kama.java's Category.HARVEST +
    // Category.WEAPON -> weapon=true).
    public static final DeferredItem<MattockItem> TOOL_MATTOCK = ITEMS.registerItem("mattock",
            MattockItem::new, new Item.Properties().stacksTo(1));
    public static final DeferredItem<KamaItem> TOOL_KAMA = ITEMS.registerItem("kama",
            KamaItem::new, new Item.Properties().stacksTo(1));

    // M3 Tool Station weapons (docs/SCOPE.md M3 issue #155). Attack speed and damage potential come
    // from ToolConstants (issue #153) rather than being repeated here, so the numbers the station's
    // stat formula uses and the ones the attribute modifiers use are the same numbers. Every one of
    // these is Category.WEAPON upstream (each constructor calls addCategory(Category.WEAPON)), which
    // is what halves the durability a hit costs -- see ToolItem#postHurtEnemy.
    //
    // None of them is a mining tool, and issue #437 (parity audit T5) is what they stopped being one
    // for: they used to carry mineable/axe at full speed, which made a broadsword a better logging
    // tool than the hatchet. Upstream splits them two ways -- the sword shapes are SwordCore
    // (#minecraft:sword_efficient plus cobweb, at ToolConstants' 0.5 mining modifier), and the frying
    // pan and battlesign are plain TinkerToolCores whose ToolCore#isEffective default is false, i.e.
    // they mine nothing at tool speed at all. Both halves refuse to break blocks in creative, which
    // is what MeleeWeaponItem carries.
    public static final DeferredItem<ToolItem> TOOL_BROADSWORD =
            sword("broadsword", ToolConstants.BROADSWORD, ForgeweaveInnates.PARRY);
    public static final DeferredItem<ToolItem> TOOL_LONGSWORD =
            sword("longsword", ToolConstants.LONGSWORD, ForgeweaveInnates.CHARGED_LEAP);
    public static final DeferredItem<ToolItem> TOOL_RAPIER =
            sword("rapier", ToolConstants.RAPIER, ForgeweaveInnates.VITAL_THRUST);
    public static final DeferredItem<ToolItem> TOOL_BATTLESIGN =
            bludgeon("battlesign", ToolConstants.BATTLESIGN, ForgeweaveInnates.DEFLECT);
    public static final DeferredItem<ToolItem> TOOL_FRYING_PAN =
            bludgeon("frying_pan", ToolConstants.FRYING_PAN, ForgeweaveInnates.HEAVY_SWING);
    // No 1.12 counterpart; a knife is a sword shape, so it takes the sword family's tag and modifier
    // rather than 1.20's own MINABLE_WITH_DAGGER (sword + hoe) at 0.75 -- see the PR for #437.
    public static final DeferredItem<ToolItem> TOOL_DAGGER =
            sword("dagger", ToolConstants.DAGGER, ForgeweaveInnates.BACKSTAB);

    // #161: the warmace, the Tool Forge tier's smash weapon (docs/SCOPE.md M3). Registered here
    // rather than through the helpers above because its innate is not a ForgeweaveInnates seam at
    // all: the smash is vanilla 1.21's mace, called through rather than copied -- see WarmaceItem.
    // It mines nothing for the same reason: vanilla's own mace is new Tool(List.of(), 1.0F, 2).
    public static final DeferredItem<ToolItem> TOOL_WARMACE = ITEMS.registerItem("warmace",
            properties -> new WarmaceItem(properties, ToolConstants.WARMACE, List.of(), true, null),
            new Item.Properties().stacksTo(1));

    // M3.5's shortbow (docs/SCOPE.md M3.5 issue #394): upstream tools/ranged/item/ShortBow.java --
    // getDrawTime() = 12 (BowCore's default is 20; the shortbow overrides it), baseProjectileSpeed()
    // = 3f (BowCore's default), baseInaccuracy() = 1f, and preventSlowDown(0.5f) -- "shortbows are
    // more mobile than other bows" (#400). Its stat constants are ToolConstants#SHORTBOW; the
    // draw/release cycle is BowItem.
    // #653: baseProjectileDamage() = 0f, projectileDamageModifier() = 0.8f (ShortBow.java) -- the
    // launcher-side damage constants a fired material arrow folds in.
    public static final DeferredItem<BowItem> TOOL_SHORTBOW = ITEMS.registerItem("shortbow",
            properties -> new BowItem(properties, ToolConstants.SHORTBOW, 12, 3.0F, 1.0F, 0.0F, 0.8F, 0.5F),
            new Item.Properties().stacksTo(1));

    // #448 (parity audit T17): the shuriken, upstream tools/ranged/item/Shuriken.java -- four knife
    // blades at the Tool Forge (TinkerRegistry.registerToolForgeCrafting), thrown on right-click,
    // ammo abstracted over durability (ProjectileCore). Constants live on ShurikenItem and
    // ToolConstants#SHURIKEN.
    public static final DeferredItem<ShurikenItem> TOOL_SHURIKEN = ITEMS.registerItem("shuriken",
            properties -> new ShurikenItem(properties, ToolConstants.SHURIKEN),
            new Item.Properties().stacksTo(1));

    // M3.5's Tool Forge-tier bows (docs/SCOPE.md M3.5 issue #395), both
    // TinkerRegistry.registerToolForgeCrafting upstream.
    //
    // LongBow.java: getDrawTime() = 30, baseProjectileSpeed() = 5.5f, baseInaccuracy() = 1.2f, and
    // an onUpdate that overrides the shortbow's preventSlowDown away -- "no speedup on charging",
    // so it draws at vanilla's own 0.2 (#400).
    // #653: baseProjectileDamage() = 2.5f, projectileDamageModifier() = 1.25f (LongBow.java).
    public static final DeferredItem<BowItem> TOOL_LONGBOW = ITEMS.registerItem("longbow",
            properties -> new BowItem(properties, ToolConstants.LONGBOW, 30, 5.5F, 1.2F, 2.5F, 1.25F),
            new Item.Properties().stacksTo(1));

    // CrossBow.java: getDrawTime() = 45, baseProjectileSpeed() = 7f, and no baseInaccuracy()
    // override at all -- so BowCore's own 0f default, unlike either bow. preventSlowDown(0.195f):
    // barely faster than vanilla's 0.2, i.e. cranking one all but stops you (#400).
    // #653: baseProjectileDamage() = 3f, projectileDamageModifier() = 1.3f (CrossBow.java).
    public static final DeferredItem<CrossbowItem> TOOL_CROSSBOW = ITEMS.registerItem("crossbow",
            properties -> new CrossbowItem(properties, ToolConstants.CROSSBOW, 45, 7.0F, 0.0F, 3.0F, 1.3F, 0.195F),
            new Item.Properties().stacksTo(1));

    /**
     * #653 (parity audit T17): the material arrow, upstream {@code tools/ranged/item/Arrow.java} --
     * shaft, head, fletching at the Tool Station ({@code TinkerRegistry.registerToolCrafting}),
     * ammo abstracted over durability ({@code AmmoToolItem}), fired by Forgeweave's bows ahead of
     * vanilla arrows ({@code BowItem#findAmmo}). Constants live on {@code ToolConstants#ARROW}.
     */
    public static final DeferredItem<MaterialArrowItem> TOOL_ARROW = ITEMS.registerItem("arrow",
            properties -> new MaterialArrowItem(properties, ToolConstants.ARROW),
            new Item.Properties().stacksTo(1));

    // M4 plate armor (issue #678, SCOPE.md D3): the four pieces, assembled from plating + maille at
    // either station (ToolConstants#ARMOR, ToolAssemblyRecipes#ENTRIES). Vanilla ArmorItems so the
    // slot, equip and armor-damage paths are vanilla's -- see ArmorPieceItem.
    /** The two-layer worn render (#679): see {@link ArmorPieceItem#plateMaterial}. */
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> PLATE_ARMOR_MATERIAL =
            SlimeBootsItem.ARMOR_MATERIALS.register("plate", ArmorPieceItem::plateMaterial);
    public static final DeferredItem<ArmorPieceItem> ARMOR_HELMET = armor("helmet", ArmorItem.Type.HELMET);
    public static final DeferredItem<ArmorPieceItem> ARMOR_CHESTPLATE = armor("chestplate", ArmorItem.Type.CHESTPLATE);
    public static final DeferredItem<ArmorPieceItem> ARMOR_LEGGINGS = armor("leggings", ArmorItem.Type.LEGGINGS);
    public static final DeferredItem<ArmorPieceItem> ARMOR_BOOTS = armor("boots", ArmorItem.Type.BOOTS);

    // #735 (epic #730): the heavy set -- plating + maille + large plate (ToolConstants#HEAVY_ARMOR).
    // Own ids so M9's designer art can differ; the same ArmorPieceItem, flagged heavy for the speed
    // debuff, and the plate set's models/sprites/worn layers until then (ToolArt#baseTool).
    public static final DeferredItem<ArmorPieceItem> ARMOR_HEAVY_HELMET = heavyArmor("helmet", ArmorItem.Type.HELMET);
    public static final DeferredItem<ArmorPieceItem> ARMOR_HEAVY_CHESTPLATE = heavyArmor("chestplate", ArmorItem.Type.CHESTPLATE);
    public static final DeferredItem<ArmorPieceItem> ARMOR_HEAVY_LEGGINGS = heavyArmor("leggings", ArmorItem.Type.LEGGINGS);
    public static final DeferredItem<ArmorPieceItem> ARMOR_HEAVY_BOOTS = heavyArmor("boots", ArmorItem.Type.BOOTS);

    private static DeferredItem<ArmorPieceItem> armor(String name, ArmorItem.Type type) {
        return ITEMS.registerItem(name, properties -> new ArmorPieceItem(type, false, properties),
                new Item.Properties().stacksTo(1));
    }

    private static DeferredItem<ArmorPieceItem> heavyArmor(String name, ArmorItem.Type type) {
        return ITEMS.registerItem(ToolConstants.HEAVY_PREFIX + name, properties -> new ArmorPieceItem(type, true, properties),
                new Item.Properties().stacksTo(1));
    }

    /** A sword-family weapon: upstream {@code SwordCore}'s effective set -- see the block above. */
    private static DeferredItem<ToolItem> sword(String name, ToolConstants.Entry constants,
            ForgeweaveInnates.Innate innate) {
        return ITEMS.registerItem(name,
                properties -> new MeleeWeaponItem(properties, constants, BlockTags.SWORD_EFFICIENT, true, innate),
                new Item.Properties().stacksTo(1));
    }

    /** A weapon that mines nothing: upstream's plain {@code TinkerToolCore} melee shape. */
    private static DeferredItem<ToolItem> bludgeon(String name, ToolConstants.Entry constants,
            ForgeweaveInnates.Innate innate) {
        return ITEMS.registerItem(name,
                properties -> new MeleeWeaponItem(properties, constants, List.of(), true, innate),
                new Item.Properties().stacksTo(1));
    }

    // M3 station-tier weapons (docs/SCOPE.md M3 issue #159). Same ToolItem shape as the M1 three:
    // the per-type constants come from ToolConstants (issue #153), which is where their provenance
    // and the maintainer's rebalance decision are documented. Both are Category.WEAPON upstream-style
    // (`weapon = true`) -- upstream's BattleAxe calls addCategory(Category.WEAPON), and the scimitar
    // is a sword by construction -- so a hit costs them half the durability a harvest tool pays and
    // haste's attack-speed bonus applies (ToolItem#effectiveAttackSpeed).
    public static final DeferredItem<ToolItem> TOOL_BATTLEAXE = ITEMS.registerItem("battleaxe",
            properties -> new ToolItem(properties, ToolConstants.BATTLEAXE, BlockTags.MINEABLE_WITH_AXE,
                    true, ForgeweaveInnates.SWEEPING_BLOW),
            new Item.Properties().stacksTo(1));
    // SWORD_EFFICIENT rather than a mineable/* tag: the scimitar is a pure weapon, and that is the
    // tag vanilla's own swords carry (cobwebs, bamboo, plants) -- there is no mining role to gate.
    public static final DeferredItem<ToolItem> TOOL_SCIMITAR = ITEMS.registerItem("scimitar",
            properties -> new MeleeWeaponItem(properties, ToolConstants.SCIMITAR, BlockTags.SWORD_EFFICIENT,
                    true, ForgeweaveInnates.LACERATE),
            new Item.Properties().stacksTo(1));
    // The katana (docs/SCOPE.md M3 issue #160): attack speed and damage potential from
    // ToolConstants#KATANA (issue #153's decision comment), Category.WEAPON like every sword-family
    // shape, and vanilla's SWORD_EFFICIENT as its mineable tag -- a katana is a weapon, so it gets
    // the cobweb/plant set vanilla swords get rather than a mining tag. Its innate, the in-combat
    // damage ramp, is a DamageRamp seam carried on ForgeweaveInnates like every other M3 tool's
    // (ADR-0005 decision 3), not behavior on this class.
    public static final DeferredItem<ToolItem> TOOL_KATANA = ITEMS.registerItem("katana",
            properties -> new MeleeWeaponItem(properties, ToolConstants.KATANA, BlockTags.SWORD_EFFICIENT,
                    true, ForgeweaveInnates.DAMAGE_RAMP),
            new Item.Properties().stacksTo(1));

    /**
     * The cleaver (docs/SCOPE.md M3 issue #158): a Tool Forge-tier weapon whose whole point is its
     * innate beheading levels ({@code combat.Beheading#CLEAVER_INNATE_LEVELS}). Numbers are
     * {@code ToolConstants#CLEAVER}, i.e. upstream {@code tools/melee/item/Cleaver.java}'s
     * {@code attackSpeed() = 0.7} / {@code damagePotential() = 1.2}, and {@code Category.WEAPON} from
     * its {@code SwordCore} base.
     *
     * <p>Its beheading innate is not an {@code Innate} seam: the chance is rolled once off the
     * innate's two levels <em>plus</em> whatever the beheading modifier added, so a per-tool seam
     * would roll it a second time. {@code Beheading} owns both halves and reads the level off the
     * stack; {@link dev.gkissel.forgeweave.combat.ForgeweaveInnates#innateId} still names it, so the
     * tooltip line is there.
     *
     * <p>Its own item class, {@link CleaverItem}, rather than the shared {@link MeleeWeaponItem}
     * (issue #498, parity audit T67): upstream's {@code onItemRightClick} swallows the right-click
     * outright, and with a {@code null} innate here there is no {@code ToolUseAction} to carry that.
     */
    public static final DeferredItem<ToolItem> TOOL_CLEAVER = ITEMS.registerItem("cleaver",
            properties -> new CleaverItem(properties, ToolConstants.CLEAVER, BlockTags.SWORD_EFFICIENT, true, null),
            new Item.Properties().stacksTo(1));

    // The large harvest tools (docs/SCOPE.md M3 issue #157): Tool Forge tier, four parts each, and
    // the only tools that break more than one block at a time. Everything numeric comes from their
    // ToolConstants.Entry (issue #153) rather than being repeated here; what stays per registration
    // is the mineable/* tag upstream's tool class sits on, whether that class adds Category.WEAPON,
    // and which extra blocks a break takes along (AoeHarvest.Shape).
    //
    // Tag choices, from each upstream class's own harvest type: Hammer extends Pickaxe, Excavator
    // extends Shovel, LumberAxe sets `setHarvestLevel("axe", 0)`, and Kama/Scythe register as
    // "shears" -- whose 1.21 counterpart for blocks is mineable/hoe (leaves, plants, sculk), since
    // vanilla has no shears mining tag. The vein hammer has no 1.12 class; it is a hammer variant,
    // so it takes the hammer's pickaxe tag and weapon category (docs/SCOPE.md M3 sources table).
    public static final DeferredItem<ToolItem> TOOL_HAMMER = ITEMS.registerItem("hammer",
            properties -> new ToolItem(properties, ToolConstants.HAMMER, BlockTags.MINEABLE_WITH_PICKAXE, true,
                    ForgeweaveInnates.CONCUSSION, AoeHarvest.Shape.PLANE_3X3),
            new Item.Properties().stacksTo(1));
    public static final DeferredItem<ToolItem> TOOL_EXCAVATOR = ITEMS.registerItem("excavator",
            properties -> new ToolItem(properties, ToolConstants.EXCAVATOR, BlockTags.MINEABLE_WITH_SHOVEL, false,
                    ForgeweaveInnates.FLAT_SMACK, AoeHarvest.Shape.PLANE_3X3),
            new Item.Properties().stacksTo(1));
    public static final DeferredItem<ToolItem> TOOL_LUMBERAXE = ITEMS.registerItem("lumberaxe",
            properties -> new ToolItem(properties, ToolConstants.LUMBERAXE, BlockTags.MINEABLE_WITH_AXE, false,
                    ForgeweaveInnates.TIMBER, AoeHarvest.Shape.TREE_FELL),
            new Item.Properties().stacksTo(1));
    public static final DeferredItem<ToolItem> TOOL_SCYTHE = ITEMS.registerItem("scythe",
            properties -> new ToolItem(properties, ToolConstants.SCYTHE, BlockTags.MINEABLE_WITH_HOE, true,
                    ForgeweaveInnates.SWEEP, AoeHarvest.Shape.CUBE_3X3X3),
            new Item.Properties().stacksTo(1));
    public static final DeferredItem<ToolItem> TOOL_VEIN_HAMMER = ITEMS.registerItem("vein_hammer",
            properties -> new ToolItem(properties, ToolConstants.VEIN_HAMMER, BlockTags.MINEABLE_WITH_PICKAXE, true,
                    ForgeweaveInnates.CRUSHING_BLOW, AoeHarvest.Shape.VEIN),
            new Item.Properties().stacksTo(1));

    public static final DeferredItem<BlockItem> TOOL_STATION = ITEMS.registerSimpleBlockItem("tool_station", ForgeweaveBlocks.TOOL_STATION);

    // The Armor Station (docs/SCOPE.md M4 issue #782): same plain block-item shape as the Tool
    // Station above (its recipe never sets a TEXTURE component -- see ArmorStationBlock).
    public static final DeferredItem<BlockItem> ARMOR_STATION = ITEMS.registerSimpleBlockItem("armor_station", ForgeweaveBlocks.ARMOR_STATION);

    // The Crafting Station (docs/SCOPE.md M1 issue #40): same retextured-table item shape as the two
    // blocks above (ForgeweaveDataComponents#TEXTURE carries the crafting wood).
    public static final DeferredItem<BlockItem> CRAFTING_STATION = ITEMS.registerSimpleBlockItem("crafting_station", ForgeweaveBlocks.CRAFTING_STATION);

    // The Stencil Table (docs/SCOPE.md M1 issue #44): same retextured-table item shape as the three
    // stations above.
    public static final DeferredItem<BlockItem> TOOL_FORGE = ITEMS.registerSimpleBlockItem("tool_forge", ForgeweaveBlocks.TOOL_FORGE);
    public static final DeferredItem<BlockItem> STENCIL_TABLE = ITEMS.registerSimpleBlockItem("stencil_table", ForgeweaveBlocks.STENCIL_TABLE);

    // The Pattern Chest and Part Chest (docs/SCOPE.md M1 issue #66): plain block items, not
    // retextured-table items -- neither chest carries a TEXTURE component (ChestBlock javadoc).
    public static final DeferredItem<BlockItem> PATTERN_CHEST = ITEMS.registerSimpleBlockItem("pattern_chest", ForgeweaveBlocks.PATTERN_CHEST);
    public static final DeferredItem<BlockItem> PART_CHEST = ITEMS.registerSimpleBlockItem("part_chest", ForgeweaveBlocks.PART_CHEST);

    // The Wooden Hopper (docs/SCOPE.md M5, issue #822): its item model is a flat derived sprite, not
    // the block model, matching both vanilla's own hopper item and upstream 1.12's
    // items/wooden_hopper.png (see ForgeweaveItemModelProvider).
    public static final DeferredItem<BlockItem> WOODEN_HOPPER = ITEMS.registerSimpleBlockItem("wooden_hopper", ForgeweaveBlocks.WOODEN_HOPPER);

    // Grout (docs/SCOPE.md M2 issue #93; placeable block per issue #129, overruling PR #115's
    // "plain item" deviation). Upstream 1.12 ships grout as one state of a multi-purpose "soil"
    // block shared with graveyard/consecrated soil and slimy mud (BlockSoil.SoilTypes, NOTICE.md) --
    // none of those other states are in Forgeweave's scope (no world-content milestone yet), but
    // grout itself is still a placeable block upstream, so it gets a real ForgeweaveBlocks.GROUT
    // block instead of being folded into a plain item. Registering the BlockItem under the same id
    // "grout" keeps existing inventories' stacks decoding fine (save compat).
    public static final DeferredItem<BlockItem> GROUT = ITEMS.registerSimpleBlockItem("grout", ForgeweaveBlocks.GROUT);

    // #339 -- green and magma slimy mud, two more placeable states of the same upstream BlockSoil as
    // grout (NOTICE.md). Crafted at a table and furnace-smelted into their slime crystals.
    public static final DeferredItem<BlockItem> SLIMY_MUD_GREEN =
            ITEMS.registerSimpleBlockItem("slimy_mud_green", ForgeweaveBlocks.SLIMY_MUD_GREEN);
    public static final DeferredItem<BlockItem> SLIMY_MUD_MAGMA =
            ITEMS.registerSimpleBlockItem("slimy_mud_magma", ForgeweaveBlocks.SLIMY_MUD_MAGMA);
    /** #635 (parity audit T57): blue slimy mud, now that blue slime balls exist to craft it. */
    public static final DeferredItem<BlockItem> SLIMY_MUD_BLUE =
            ITEMS.registerSimpleBlockItem("slimy_mud_blue", ForgeweaveBlocks.SLIMY_MUD_BLUE);

    // #429 -- graveyard soil and consecrated soil, two more placeable BlockSoil states (NOTICE.md).
    // Graveyard soil is crafted at a table and furnace-smelts into consecrated soil, which is
    // smite's upstream reagent (modifier_recipe/smite.json).
    public static final DeferredItem<BlockItem> GRAVEYARD_SOIL =
            ITEMS.registerSimpleBlockItem("graveyard_soil", ForgeweaveBlocks.GRAVEYARD_SOIL);
    public static final DeferredItem<BlockItem> CONSECRATED_SOIL =
            ITEMS.registerSimpleBlockItem("consecrated_soil", ForgeweaveBlocks.CONSECRATED_SOIL);

    // #449 (T18 parity audit): the slime island's world blocks. Walked off ForgeweaveBlocks' own
    // rosters rather than listed one by one, so a colour added there cannot lose its block item --
    // the same anti-drift shape ForgeweaveBlocks#clearStainedGlassColors already uses. The order is
    // the one the World creative tab shows them in.
    private static final List<DeferredItem<BlockItem>> SLIME_WORLD_BLOCKS = registerSlimeWorldBlocks();

    /** Every slime island block item, in creative-tab order (#449). */
    public static List<DeferredItem<BlockItem>> slimeWorldBlocks() {
        return SLIME_WORLD_BLOCKS;
    }

    private static List<DeferredItem<BlockItem>> registerSlimeWorldBlocks() {
        List<DeferredItem<BlockItem>> items = new java.util.ArrayList<>();
        for (ForgeweaveBlocks.SlimeSoil soil : ForgeweaveBlocks.slimeSoils()) {
            items.add(blockItem(soil.dirt()));
            items.add(blockItem(soil.grass()));
        }
        // All six congealed colours and the five coloured slime blocks (#635), green congealed
        // (#449), blue/purple congealed (#625) and magma congealed (#450) among them.
        for (ForgeweaveBlocks.SlimeFamily family : ForgeweaveBlocks.slimeFamilies()) {
            items.add(blockItem(family.congealed()));
            if (family.slimeBlock() != null) {
                items.add(blockItem(family.slimeBlock()));
            }
        }
        for (ForgeweaveBlocks.SlimePlants plants : ForgeweaveBlocks.slimePlants()) {
            items.add(blockItem(plants.leaves()));
            items.add(blockItem(plants.sapling())); // #488 (T57)
            items.add(blockItem(plants.tallGrass()));
            items.add(blockItem(plants.fern()));
            plants.vines().forEach(vine -> items.add(blockItem(vine))); // #488 (T57)
        }
        return Collections.unmodifiableList(items);
    }

    /** Registers a block item under the block's own registry id -- the convention every block here follows. */
    private static DeferredItem<BlockItem> blockItem(DeferredBlock<? extends Block> block) {
        return ITEMS.registerSimpleBlockItem(block.getId().getPath(), block);
    }

    // #502 (T71 parity audit): mud brick, upstream's second "materials" meta item alongside seared
    // brick (TinkerCommons#mudBrick, "materials" item meta 1, NOTICE.md) -- cast at a Casting Table
    // from molten dirt, and crafted 2x2 into the mud brick block below.
    public static final DeferredItem<Item> MUD_BRICK = ITEMS.registerSimpleItem("mud_brick");

    // #727: nahuatl board -- molten obsidian poured over any planks at a Casting Table or Basin
    // (the 1.20 clone's obsidian/nahuatl basin recipe, SmelteryRecipeProvider:1403-1406, as an item
    // instead of a planks block). Nahuatl's only Part Builder crafting item, one ingot of value each
    // (MaterialRecipeProvider:170), so nahuatl plating and maille become obtainable.
    // Issue #783: the Part Builder's crafting material for nahuatl (like the slime crystals'), cast
    // from obsidian poured over planks rather than mined -- unfamiliar enough as a source that it
    // had no hover text of its own before this audit.
    public static final DeferredItem<Item> NAHUATL_BOARD = ITEMS.registerItem("nahuatl_board",
            p -> new DescribedItem(p, "tooltip.forgeweave.nahuatl_board"));
    public static final DeferredItem<BlockItem> MUD_BRICK_BLOCK =
            ITEMS.registerSimpleBlockItem("mud_brick_block", ForgeweaveBlocks.MUD_BRICK_BLOCK);

    // Seared brick (docs/SCOPE.md M2 issue #93): upstream 1.12's plain crafting-material item
    // (TinkerCommons#searedBrick, "materials" item meta 0, NOTICE.md) -- produced by furnace-smelting
    // grout, and itself crafted 2x2 into the Seared Bricks block below.
    public static final DeferredItem<Item> SEARED_BRICK = ITEMS.registerSimpleItem("seared_brick");

    public static final DeferredItem<BlockItem> SEARED_STONE = ITEMS.registerSimpleBlockItem("seared_stone", ForgeweaveBlocks.SEARED_STONE);
    public static final DeferredItem<BlockItem> SEARED_COBBLESTONE = ITEMS.registerSimpleBlockItem("seared_cobblestone", ForgeweaveBlocks.SEARED_COBBLESTONE);
    public static final DeferredItem<BlockItem> SEARED_PAVER = ITEMS.registerSimpleBlockItem("seared_paver", ForgeweaveBlocks.SEARED_PAVER);
    public static final DeferredItem<BlockItem> SEARED_BRICKS = ITEMS.registerSimpleBlockItem("seared_bricks", ForgeweaveBlocks.SEARED_BRICKS);
    public static final DeferredItem<BlockItem> SEARED_CRACKED_BRICKS = ITEMS.registerSimpleBlockItem("seared_cracked_bricks", ForgeweaveBlocks.SEARED_CRACKED_BRICKS);
    public static final DeferredItem<BlockItem> SEARED_FANCY_BRICKS = ITEMS.registerSimpleBlockItem("seared_fancy_bricks", ForgeweaveBlocks.SEARED_FANCY_BRICKS);
    public static final DeferredItem<BlockItem> SEARED_SQUARE_BRICKS = ITEMS.registerSimpleBlockItem("seared_square_bricks", ForgeweaveBlocks.SEARED_SQUARE_BRICKS);
    public static final DeferredItem<BlockItem> SEARED_TRIANGLE_BRICKS = ITEMS.registerSimpleBlockItem("seared_triangle_bricks", ForgeweaveBlocks.SEARED_TRIANGLE_BRICKS);
    public static final DeferredItem<BlockItem> SEARED_SMALL_BRICKS = ITEMS.registerSimpleBlockItem("seared_small_bricks", ForgeweaveBlocks.SEARED_SMALL_BRICKS);
    public static final DeferredItem<BlockItem> SEARED_ROAD = ITEMS.registerSimpleBlockItem("seared_road", ForgeweaveBlocks.SEARED_ROAD);
    public static final DeferredItem<BlockItem> SEARED_TILE = ITEMS.registerSimpleBlockItem("seared_tile", ForgeweaveBlocks.SEARED_TILE);
    public static final DeferredItem<BlockItem> SEARED_CREEPER = ITEMS.registerSimpleBlockItem("seared_creeper", ForgeweaveBlocks.SEARED_CREEPER);

    // Seared stairs + slabs (docs/SCOPE.md M3.4-5 issue #274) -- see ForgeweaveBlocks for the parity
    // notes and the flagged smeltery-structure deviation.
    public static final DeferredItem<BlockItem> SEARED_STAIRS_STONE = ITEMS.registerSimpleBlockItem("seared_stairs_stone", ForgeweaveBlocks.SEARED_STAIRS_STONE);
    public static final DeferredItem<BlockItem> SEARED_STAIRS_COBBLESTONE = ITEMS.registerSimpleBlockItem("seared_stairs_cobblestone", ForgeweaveBlocks.SEARED_STAIRS_COBBLESTONE);
    public static final DeferredItem<BlockItem> SEARED_STAIRS_PAVER = ITEMS.registerSimpleBlockItem("seared_stairs_paver", ForgeweaveBlocks.SEARED_STAIRS_PAVER);
    public static final DeferredItem<BlockItem> SEARED_STAIRS_BRICKS = ITEMS.registerSimpleBlockItem("seared_stairs_bricks", ForgeweaveBlocks.SEARED_STAIRS_BRICKS);
    public static final DeferredItem<BlockItem> SEARED_STAIRS_CRACKED_BRICKS = ITEMS.registerSimpleBlockItem("seared_stairs_cracked_bricks", ForgeweaveBlocks.SEARED_STAIRS_CRACKED_BRICKS);
    public static final DeferredItem<BlockItem> SEARED_STAIRS_FANCY_BRICKS = ITEMS.registerSimpleBlockItem("seared_stairs_fancy_bricks", ForgeweaveBlocks.SEARED_STAIRS_FANCY_BRICKS);
    public static final DeferredItem<BlockItem> SEARED_STAIRS_SQUARE_BRICKS = ITEMS.registerSimpleBlockItem("seared_stairs_square_bricks", ForgeweaveBlocks.SEARED_STAIRS_SQUARE_BRICKS);
    public static final DeferredItem<BlockItem> SEARED_STAIRS_TRIANGLE_BRICKS = ITEMS.registerSimpleBlockItem("seared_stairs_triangle_bricks", ForgeweaveBlocks.SEARED_STAIRS_TRIANGLE_BRICKS);
    public static final DeferredItem<BlockItem> SEARED_STAIRS_SMALL_BRICKS = ITEMS.registerSimpleBlockItem("seared_stairs_small_bricks", ForgeweaveBlocks.SEARED_STAIRS_SMALL_BRICKS);
    public static final DeferredItem<BlockItem> SEARED_STAIRS_ROAD = ITEMS.registerSimpleBlockItem("seared_stairs_road", ForgeweaveBlocks.SEARED_STAIRS_ROAD);
    public static final DeferredItem<BlockItem> SEARED_STAIRS_TILE = ITEMS.registerSimpleBlockItem("seared_stairs_tile", ForgeweaveBlocks.SEARED_STAIRS_TILE);
    public static final DeferredItem<BlockItem> SEARED_STAIRS_CREEPER = ITEMS.registerSimpleBlockItem("seared_stairs_creeper", ForgeweaveBlocks.SEARED_STAIRS_CREEPER);

    public static final DeferredItem<BlockItem> SEARED_SLAB_STONE = ITEMS.registerSimpleBlockItem("seared_slab_stone", ForgeweaveBlocks.SEARED_SLAB_STONE);
    public static final DeferredItem<BlockItem> SEARED_SLAB_COBBLESTONE = ITEMS.registerSimpleBlockItem("seared_slab_cobblestone", ForgeweaveBlocks.SEARED_SLAB_COBBLESTONE);
    public static final DeferredItem<BlockItem> SEARED_SLAB_PAVER = ITEMS.registerSimpleBlockItem("seared_slab_paver", ForgeweaveBlocks.SEARED_SLAB_PAVER);
    public static final DeferredItem<BlockItem> SEARED_SLAB_BRICKS = ITEMS.registerSimpleBlockItem("seared_slab_bricks", ForgeweaveBlocks.SEARED_SLAB_BRICKS);
    public static final DeferredItem<BlockItem> SEARED_SLAB_CRACKED_BRICKS = ITEMS.registerSimpleBlockItem("seared_slab_cracked_bricks", ForgeweaveBlocks.SEARED_SLAB_CRACKED_BRICKS);
    public static final DeferredItem<BlockItem> SEARED_SLAB_FANCY_BRICKS = ITEMS.registerSimpleBlockItem("seared_slab_fancy_bricks", ForgeweaveBlocks.SEARED_SLAB_FANCY_BRICKS);
    public static final DeferredItem<BlockItem> SEARED_SLAB_SQUARE_BRICKS = ITEMS.registerSimpleBlockItem("seared_slab_square_bricks", ForgeweaveBlocks.SEARED_SLAB_SQUARE_BRICKS);
    public static final DeferredItem<BlockItem> SEARED_SLAB_TRIANGLE_BRICKS = ITEMS.registerSimpleBlockItem("seared_slab_triangle_bricks", ForgeweaveBlocks.SEARED_SLAB_TRIANGLE_BRICKS);
    public static final DeferredItem<BlockItem> SEARED_SLAB_SMALL_BRICKS = ITEMS.registerSimpleBlockItem("seared_slab_small_bricks", ForgeweaveBlocks.SEARED_SLAB_SMALL_BRICKS);
    public static final DeferredItem<BlockItem> SEARED_SLAB_ROAD = ITEMS.registerSimpleBlockItem("seared_slab_road", ForgeweaveBlocks.SEARED_SLAB_ROAD);
    public static final DeferredItem<BlockItem> SEARED_SLAB_TILE = ITEMS.registerSimpleBlockItem("seared_slab_tile", ForgeweaveBlocks.SEARED_SLAB_TILE);
    public static final DeferredItem<BlockItem> SEARED_SLAB_CREEPER = ITEMS.registerSimpleBlockItem("seared_slab_creeper", ForgeweaveBlocks.SEARED_SLAB_CREEPER);

    // The smeltery multiblock's blocks (docs/SCOPE.md M2 issue #95).
    public static final DeferredItem<BlockItem> STANDARD_CORE = ITEMS.registerSimpleBlockItem("standard_core", ForgeweaveBlocks.STANDARD_CORE);
    // #442 -- the seared furnace controller.
    public static final DeferredItem<BlockItem> SEARED_FURNACE_CONTROLLER =
            ITEMS.registerSimpleBlockItem("seared_furnace_controller", ForgeweaveBlocks.SEARED_FURNACE_CONTROLLER);
    public static final DeferredItem<BlockItem> NETHER_CORE = ITEMS.registerSimpleBlockItem("nether_core", ForgeweaveBlocks.NETHER_CORE);
    // #845 -- the End and Deep Core. Block items only: neither is obtainable via a crafting recipe,
    // just pour-to-transform (CoreTransformRecipe), so there is nothing in ForgeweaveRecipeProvider
    // for them.
    public static final DeferredItem<BlockItem> END_CORE = ITEMS.registerSimpleBlockItem("end_core", ForgeweaveBlocks.END_CORE);
    public static final DeferredItem<BlockItem> DEEP_CORE = ITEMS.registerSimpleBlockItem("deep_core", ForgeweaveBlocks.DEEP_CORE);
    // T44/#475 -- the seared reservoir controller.
    public static final DeferredItem<BlockItem> SEARED_RESERVOIR_CONTROLLER =
            ITEMS.registerSimpleBlockItem("seared_reservoir_controller", ForgeweaveBlocks.SEARED_RESERVOIR_CONTROLLER);
    // The three tank blocks keep their contents on the dropped stack (SearedTankBlockEntity's
    // FLUID_CONTENT component, copied by the loot table), so their item shows what is inside --
    // upstream's ItemTank, see SearedTankItem.
    public static final DeferredItem<BlockItem> SEARED_TANK = tankItem(ForgeweaveBlocks.SEARED_TANK);
    public static final DeferredItem<BlockItem> SEARED_GAUGE = tankItem(ForgeweaveBlocks.SEARED_GAUGE);
    public static final DeferredItem<BlockItem> SEARED_WINDOW = tankItem(ForgeweaveBlocks.SEARED_WINDOW);
    public static final DeferredItem<BlockItem> SEARED_DRAIN = ITEMS.registerSimpleBlockItem("seared_drain", ForgeweaveBlocks.SEARED_DRAIN);

    // #277 -- filtered fluid I/O and item I/O for the smeltery (docs/SCOPE.md M3.4).
    public static final DeferredItem<BlockItem> SEARED_DUCT = ITEMS.registerSimpleBlockItem("seared_duct", ForgeweaveBlocks.SEARED_DUCT);
    public static final DeferredItem<BlockItem> SEARED_CHUTE = ITEMS.registerSimpleBlockItem("seared_chute", ForgeweaveBlocks.SEARED_CHUTE);

    // #441 (parity audit T9) -- the channel needs upstream's own ItemChannel behaviour on placement,
    // see SearedChannelBlockItem.
    public static final DeferredItem<BlockItem> SEARED_CHANNEL = ITEMS.register("seared_channel",
            () -> new SearedChannelBlockItem(ForgeweaveBlocks.SEARED_CHANNEL.get(), new Item.Properties()));

    // Plain seared glass (docs/SCOPE.md M3.3 issue #289).
    public static final DeferredItem<BlockItem> SEARED_GLASS = ITEMS.registerSimpleBlockItem("seared_glass", ForgeweaveBlocks.SEARED_GLASS);

    // #107 batch: modifier reagent items (docs/SCOPE.md M2 issue #107) -- silky jewel, reinforced
    // plate, mending moss (plus its "moss" precursor), and the extra-slot item. Soulbound reuses the
    // vanilla nether star (modifier.ForgeweaveModifiers) so it needs no item of its own here.
    //
    // Issue #783: every reagent below was a plain Item with no hover text at all -- PR #775 gave
    // Mending Moss a JEI ingredient-info page, but the in-inventory tooltip was never touched, and
    // the audit that issue asked for found the same gap on its siblings. Each modifier reagent shows
    // its own modifier's existing name/description lines (no new text to keep in sync, the repo's
    // own anti-drift rule, issue #79) plus a shared line naming where it's used; "moss" itself is not
    // a modifier reagent, so it reuses #752's already-shipped bookshelf-conversion line instead.
    public static final DeferredItem<Item> MOSS = ITEMS.registerItem("moss",
            p -> new DescribedItem(p, "tooltip.forgeweave.mending_moss.source"));
    public static final DeferredItem<Item> MENDING_MOSS = ITEMS.registerItem("mending_moss",
            p -> new DescribedItem(p, modifierReagentTooltip("mending_moss")));
    public static final DeferredItem<Item> REINFORCED_PLATE = ITEMS.registerItem("reinforced_plate",
            p -> new DescribedItem(p, modifierReagentTooltip("reinforced")));
    // Silky cloth is Silky Jewel's crafting precursor (see ForgeweaveRecipeProvider), not a modifier
    // reagent of its own -- it names what it's for instead of quoting a modifier.
    public static final DeferredItem<Item> SILKY_CLOTH = ITEMS.registerItem("silky_cloth",
            p -> new DescribedItem(p, "tooltip.forgeweave.silky_cloth"));
    public static final DeferredItem<Item> SILKY_JEWEL = ITEMS.registerItem("silky_jewel",
            p -> new DescribedItem(p, modifierReagentTooltip("silky")));
    public static final DeferredItem<Item> EXTRA_MODIFIER = ITEMS.registerItem("extra_modifier",
            p -> new DescribedItem(p, modifierReagentTooltip("extra_slot")));

    // #429 -- the necrotic bone, upstream's own necrotic reagent (TinkerCommons#matNecroticBone,
    // "materials" meta 17). It has no recipe upstream and none here: wither skeletons drop it
    // (data/forgeweave/loot_modifiers/necrotic_bone.json).
    public static final DeferredItem<Item> NECROTIC_BONE = ITEMS.registerItem("necrotic_bone",
            p -> new DescribedItem(p, modifierReagentTooltip("necrotic")));
    // #438 -- the Width++/Height++ reagents (upstream TinkerCommons' matExpanderW/matExpanderH,
    // materials sheet meta 12 and 13). Registry paths kept as upstream's own, which carry no
    // avoided-vocabulary problem; the player-facing names are "Expander (Horizontal)"/"(Vertical)",
    // upstream's own item.materials.expander_*.name.
    public static final DeferredItem<Item> EXPANDER_W = ITEMS.registerItem("expander_w",
            p -> new DescribedItem(p, modifierReagentTooltip("harvest_width")));
    public static final DeferredItem<Item> EXPANDER_H = ITEMS.registerItem("expander_h",
            p -> new DescribedItem(p, modifierReagentTooltip("harvest_height")));

    /**
     * Issue #783: the three-line hover text every modifier reagent above shares -- the modifier's
     * own {@code .name}/{@code .description} (already guarded by {@code ModifierLangCoverageTest})
     * plus one shared line saying where the reagent is spent, so the player never has to guess it's
     * a Tool Station ingredient and not, say, a crafting one.
     */
    private static String[] modifierReagentTooltip(String modifierId) {
        return new String[] {
                "modifier.forgeweave." + modifierId + ".name",
                "modifier.forgeweave." + modifierId + ".description",
                "tooltip.forgeweave.reagent.tool_station"
        };
    }

    // #100 -- casting (docs/SCOPE.md M2 issue #100). The two casting blocks and the faucet, plus the
    // seven casts. Upstream 1.12 ships one `cast` item whose NBT names the part it was moulded around
    // and whose texture is generated at load time by compositing the blank cast with that part's
    // sprite (CustomTextureCreator); Forgeweave registers one item per cast instead -- the same
    // one-block-per-variant split issue #93 made for the seared bricks -- so a cast is a plain item
    // with a plain two-layer model and an Ingredient can match it without NBT.
    //
    // These casts are gold-only and reusable, which is upstream parity; their single-use clay
    // counterparts are CLAY_CASTS below (issue #292). No sand casts (docs/SCOPE.md M2 non-goals).
    public static final DeferredItem<Item> CAST_INGOT = ITEMS.registerItem("cast_ingot", p -> new DescribedItem(p, "tooltip.forgeweave.cast"));
    public static final DeferredItem<Item> CAST_NUGGET = ITEMS.registerItem("cast_nugget", p -> new DescribedItem(p, "tooltip.forgeweave.cast"));
    public static final DeferredItem<Item> CAST_PICKAXE_HEAD = ITEMS.registerItem("cast_pickaxe_head", p -> new DescribedItem(p, "tooltip.forgeweave.cast"));
    public static final DeferredItem<Item> CAST_SHOVEL_HEAD = ITEMS.registerItem("cast_shovel_head", p -> new DescribedItem(p, "tooltip.forgeweave.cast"));
    public static final DeferredItem<Item> CAST_AXE_HEAD = ITEMS.registerItem("cast_axe_head", p -> new DescribedItem(p, "tooltip.forgeweave.cast"));
    public static final DeferredItem<Item> CAST_TOOL_BINDING = ITEMS.registerItem("cast_tool_binding", p -> new DescribedItem(p, "tooltip.forgeweave.cast"));
    public static final DeferredItem<Item> CAST_TOOL_HANDLE = ITEMS.registerItem("cast_tool_handle", p -> new DescribedItem(p, "tooltip.forgeweave.cast"));

    // #222 -- casts for every M3 part (docs/SCOPE.md M3 issue #151/#159/#160/#161's roster), the
    // same gold-only reusable idiom as the five above: pour molten gold over the crafted part at the
    // casting table to mould one, then cast any castable metal into that part's shape.
    public static final DeferredItem<Item> CAST_SWORD_BLADE = ITEMS.registerItem("cast_sword_blade", p -> new DescribedItem(p, "tooltip.forgeweave.cast"));
    public static final DeferredItem<Item> CAST_WIDE_GUARD = ITEMS.registerItem("cast_wide_guard", p -> new DescribedItem(p, "tooltip.forgeweave.cast"));
    public static final DeferredItem<Item> CAST_HAND_GUARD = ITEMS.registerItem("cast_hand_guard", p -> new DescribedItem(p, "tooltip.forgeweave.cast"));
    public static final DeferredItem<Item> CAST_CROSS_GUARD = ITEMS.registerItem("cast_cross_guard", p -> new DescribedItem(p, "tooltip.forgeweave.cast"));
    public static final DeferredItem<Item> CAST_SIGN_PLATE = ITEMS.registerItem("cast_sign_plate", p -> new DescribedItem(p, "tooltip.forgeweave.cast"));
    public static final DeferredItem<Item> CAST_PAN = ITEMS.registerItem("cast_pan", p -> new DescribedItem(p, "tooltip.forgeweave.cast"));
    public static final DeferredItem<Item> CAST_KNIFE_BLADE = ITEMS.registerItem("cast_knife_blade", p -> new DescribedItem(p, "tooltip.forgeweave.cast"));
    public static final DeferredItem<Item> CAST_LARGE_SWORD_BLADE = ITEMS.registerItem("cast_large_sword_blade", p -> new DescribedItem(p, "tooltip.forgeweave.cast"));
    public static final DeferredItem<Item> CAST_TOUGH_TOOL_ROD = ITEMS.registerItem("cast_tough_tool_rod", p -> new DescribedItem(p, "tooltip.forgeweave.cast"));
    public static final DeferredItem<Item> CAST_TOUGH_BINDING = ITEMS.registerItem("cast_tough_binding", p -> new DescribedItem(p, "tooltip.forgeweave.cast"));
    public static final DeferredItem<Item> CAST_LARGE_PLATE = ITEMS.registerItem("cast_large_plate", p -> new DescribedItem(p, "tooltip.forgeweave.cast"));
    public static final DeferredItem<Item> CAST_HAMMER_HEAD = ITEMS.registerItem("cast_hammer_head", p -> new DescribedItem(p, "tooltip.forgeweave.cast"));
    public static final DeferredItem<Item> CAST_EXCAVATOR_HEAD = ITEMS.registerItem("cast_excavator_head", p -> new DescribedItem(p, "tooltip.forgeweave.cast"));
    public static final DeferredItem<Item> CAST_SCYTHE_HEAD = ITEMS.registerItem("cast_scythe_head", p -> new DescribedItem(p, "tooltip.forgeweave.cast"));
    public static final DeferredItem<Item> CAST_KAMA_HEAD = ITEMS.registerItem("cast_kama_head", p -> new DescribedItem(p, "tooltip.forgeweave.cast"));
    public static final DeferredItem<Item> CAST_BROAD_AXE_HEAD = ITEMS.registerItem("cast_broad_axe_head", p -> new DescribedItem(p, "tooltip.forgeweave.cast"));
    public static final DeferredItem<Item> CAST_VEIN_HAMMER_HEAD = ITEMS.registerItem("cast_vein_hammer_head", p -> new DescribedItem(p, "tooltip.forgeweave.cast"));
    public static final DeferredItem<Item> CAST_WAR_MACE_HEAD = ITEMS.registerItem("cast_war_mace_head", p -> new DescribedItem(p, "tooltip.forgeweave.cast"));
    public static final DeferredItem<Item> CAST_CURVED_BLADE = ITEMS.registerItem("cast_curved_blade", p -> new DescribedItem(p, "tooltip.forgeweave.cast"));
    public static final DeferredItem<Item> CAST_KATANA_BLADE = ITEMS.registerItem("cast_katana_blade", p -> new DescribedItem(p, "tooltip.forgeweave.cast"));
    // #393: the bow limb casts like any other part. Its string does not, and deliberately so --
    // upstream only ever reaches registerToolpartMeltingCasting through a MaterialIntegration (a
    // material with a molten fluid), and skips any part whose canUseMaterial rejects that material.
    // The only BOWSTRING materials are string and vine (issue #392), neither of which melts, so
    // upstream registers no bow_string cast at all; a cast no fluid could fill is not worth adding.
    public static final DeferredItem<Item> CAST_BOW_LIMB = ITEMS.registerItem("cast_bow_limb", p -> new DescribedItem(p, "tooltip.forgeweave.cast"));
    // #626: the arrow head casts like any other head part -- every castable metal has HEAD stats
    // (and the PROJECTILE stat upstream auto-adds beside them), so canUseMaterial holds and the
    // registerToolpartMeltingCasting loop reaches it. The shaft and fletching do not: no molten
    // material carries a SHAFT or FLETCHING block, the same reason the bow string has no cast.
    public static final DeferredItem<Item> CAST_ARROW_HEAD = ITEMS.registerItem("cast_arrow_head", p -> new DescribedItem(p, "tooltip.forgeweave.cast"));
    // #677: the 1.20 clone's {helmet,chestplate,leggings,boots}PlatingCast and mailleCast
    // (TinkerSmeltery). The first plating cast is moulded from a Part Builder plating (obsidian) --
    // no crafting-table bootstrap (docs/SCOPE.md D12).
    public static final DeferredItem<Item> CAST_PLATING_HELMET = ITEMS.registerItem("cast_plating_helmet", p -> new DescribedItem(p, "tooltip.forgeweave.cast"));
    public static final DeferredItem<Item> CAST_PLATING_CHESTPLATE = ITEMS.registerItem("cast_plating_chestplate", p -> new DescribedItem(p, "tooltip.forgeweave.cast"));
    public static final DeferredItem<Item> CAST_PLATING_LEGGINGS = ITEMS.registerItem("cast_plating_leggings", p -> new DescribedItem(p, "tooltip.forgeweave.cast"));
    public static final DeferredItem<Item> CAST_PLATING_BOOTS = ITEMS.registerItem("cast_plating_boots", p -> new DescribedItem(p, "tooltip.forgeweave.cast"));
    public static final DeferredItem<Item> CAST_MAILLE = ITEMS.registerItem("cast_maille", p -> new DescribedItem(p, "tooltip.forgeweave.cast"));
    // #271: upstream casts the sharpening kit like any other tool part -- TinkerSmeltery's
    // registerToolpartMeltingCasting loops every registered IToolPart whose canBeCasted() holds, and
    // SharpeningKit never overrides it.
    public static final DeferredItem<Item> CAST_SHARPENING_KIT = ITEMS.registerItem("cast_sharpening_kit", p -> new DescribedItem(p, "tooltip.forgeweave.cast"));
    // #471/T40: the shard, like the sharpening kit above, casts like any other tool part --
    // TinkerSmeltery's Shard extends ToolPart (itself a MaterialItem), and Shard#canUseMaterial is
    // unconditionally true (no HEAD-stat gate the way the creative-tab listing has), so every
    // material with a molten fluid gets registered through the generic
    // registerToolpartMeltingCasting loop, not just materials with head stats.
    public static final DeferredItem<Item> CAST_SHARD = ITEMS.registerItem("cast_shard", p -> new DescribedItem(p, "tooltip.forgeweave.cast"));

    // #272 (M3.4-3) -- the three CastCustom metas upstream ships beyond ingot/nugget (TinkerSmeltery
    // castGem/castPlate/castGear). Same gold-only reusable idiom, straight-ported upstream sprites
    // (like cast_ingot/cast_nugget above, not the compositing script -- upstream ships these three as
    // their own dedicated textures too, NOTICE.md).
    public static final DeferredItem<Item> CAST_GEM = ITEMS.registerItem("cast_gem", p -> new DescribedItem(p, "tooltip.forgeweave.cast"));
    public static final DeferredItem<Item> CAST_PLATE = ITEMS.registerItem("cast_plate", p -> new DescribedItem(p, "tooltip.forgeweave.cast"));
    public static final DeferredItem<Item> CAST_GEAR = ITEMS.registerItem("cast_gear", p -> new DescribedItem(p, "tooltip.forgeweave.cast"));

    // #292 (M3.4-12) -- one single-use clay counterpart per cast above, keyed by the cast it copies.
    // Upstream ships a second NBT cast item (TinkerSmeltery `clayCast`) moulded from molten clay
    // instead of gold and consumed by the pour that uses it, gated behind its `enableClayCasts`
    // config; the same one-item-per-cast split the gold set made applies, so this is a loop over
    // their names rather than 30 more fields -- every consumer (models, lang, creative tab) walks
    // this map. Upstream registers clay creation for tool parts only; issue #292 asks for the
    // ingot/nugget forms too, so every cast gets one (see the PR body).
    public static final Map<String, DeferredItem<Item>> CLAY_CASTS = clayCasts(
            "cast_ingot", "cast_nugget", "cast_gem", "cast_plate", "cast_gear",
            "cast_pickaxe_head", "cast_shovel_head", "cast_axe_head", "cast_tool_binding", "cast_tool_handle",
            "cast_sword_blade", "cast_wide_guard", "cast_hand_guard", "cast_cross_guard", "cast_sign_plate",
            "cast_pan", "cast_knife_blade", "cast_large_sword_blade", "cast_tough_tool_rod", "cast_tough_binding",
            "cast_large_plate", "cast_hammer_head", "cast_excavator_head", "cast_scythe_head", "cast_kama_head",
            "cast_broad_axe_head", "cast_vein_hammer_head", "cast_war_mace_head", "cast_curved_blade",
            "cast_katana_blade", "cast_sharpening_kit", "cast_bow_limb", "cast_shard", "cast_arrow_head",
            "cast_plating_helmet", "cast_plating_chestplate", "cast_plating_leggings", "cast_plating_boots",
            "cast_maille");

    public static final DeferredItem<BlockItem> CASTING_TABLE = ITEMS.registerSimpleBlockItem("casting_table", ForgeweaveBlocks.CASTING_TABLE);
    public static final DeferredItem<BlockItem> CASTING_BASIN = ITEMS.registerSimpleBlockItem("casting_basin", ForgeweaveBlocks.CASTING_BASIN);
    public static final DeferredItem<BlockItem> FAUCET = ITEMS.registerSimpleBlockItem("faucet", ForgeweaveBlocks.FAUCET);

    // #103 -- the four metal materials with no vanilla item forms yet (docs/SCOPE.md M2 issue #103):
    // cobalt, ardite, manyullyn (upstream 1.12 ingot/nugget art, NOTICE.md) and rose gold (no 1.12
    // counterpart -- its ingot/nugget are a recoloured derivation of upstream's copper ones, NOTICE.md).
    // Iron/copper/gold/netherite are vanilla items already; netherite scrap is vanilla too. Raw forms
    // have no 1.12 counterpart at all (1.12 predates the raw-ore item split). Raw cobalt/ardite are
    // maintainer-specified vanilla recolors (issue #140, NOTICE.md: raw_gold hue-shifted blue, and
    // netherite_scrap recoloured yellowish-orange, both preserving source shading -- see
    // scripts/recolor_raw_ore.py). Raw manyullyn/rose gold have no ore block source in this PR's
    // scope either, so they stay freshly authored placeholders, not derived (CLAUDE.md).
    public static final DeferredItem<Item> INGOT_COBALT = ITEMS.registerSimpleItem("cobalt_ingot");
    public static final DeferredItem<Item> NUGGET_COBALT = ITEMS.registerSimpleItem("cobalt_nugget");
    public static final DeferredItem<Item> RAW_COBALT = ITEMS.registerSimpleItem("raw_cobalt");
    public static final DeferredItem<Item> INGOT_ARDITE = ITEMS.registerSimpleItem("ardite_ingot");
    public static final DeferredItem<Item> NUGGET_ARDITE = ITEMS.registerSimpleItem("ardite_nugget");
    public static final DeferredItem<Item> RAW_ARDITE = ITEMS.registerSimpleItem("raw_ardite");
    public static final DeferredItem<Item> INGOT_MANYULLYN = ITEMS.registerSimpleItem("manyullyn_ingot");
    public static final DeferredItem<Item> NUGGET_MANYULLYN = ITEMS.registerSimpleItem("manyullyn_nugget");
    public static final DeferredItem<Item> RAW_MANYULLYN = ITEMS.registerSimpleItem("raw_manyullyn");
    public static final DeferredItem<Item> INGOT_ROSE_GOLD = ITEMS.registerSimpleItem("rose_gold_ingot");
    public static final DeferredItem<Item> NUGGET_ROSE_GOLD = ITEMS.registerSimpleItem("rose_gold_nugget");
    public static final DeferredItem<Item> RAW_ROSE_GOLD = ITEMS.registerSimpleItem("raw_rose_gold");

    // #234 -- steel (M3.2): FW-native ingot/nugget, alloyed from molten iron + carbon rather than
    // mined, so no raw form and no ore. Textures are the same recolor-of-manyullyn derivation as
    // rose gold's, at steel's own upstream color 0xa7a7a7 (NOTICE.md).
    public static final DeferredItem<Item> INGOT_STEEL = ITEMS.registerSimpleItem("steel_ingot");
    public static final DeferredItem<Item> NUGGET_STEEL = ITEMS.registerSimpleItem("steel_nugget");
    // #232 -- the three slime crystals (docs/SCOPE.md M3.2), the slime-family materials' part-crafting
    // items, textures ported from upstream 1.12's slimecrystal_{green,blue,magma} (NOTICE.md). Green
    // and magma are furnace-smelted from their vanilla blocks like upstream's congealed-slime smelts;
    // blue is crafted from green + lapis (maintainer decision on #232 -- no blue slime world source
    // until the world-content milestone).
    public static final DeferredItem<Item> GREEN_SLIME_CRYSTAL = ITEMS.registerSimpleItem("green_slime_crystal");
    public static final DeferredItem<Item> BLUE_SLIME_CRYSTAL = ITEMS.registerSimpleItem("blue_slime_crystal");
    public static final DeferredItem<Item> MAGMA_SLIME_CRYSTAL = ITEMS.registerSimpleItem("magma_slime_crystal");

    /**
     * The five coloured slime balls (issue #635, parity audit T57): upstream 1.12's
     * {@code TinkerCommons#matSlimeBall*}, five metas of its {@code ItemEdible} (NOTICE.md). Every
     * one is a food with upstream's own nutrition, saturation and potion effects, all of them
     * always-edible because upstream's {@code ItemEdible#addFood} sets {@code alwaysEdible} whenever
     * a food carries effects -- which all five do.
     *
     * <p>Green is deliberately absent: vanilla's {@code minecraft:slime_ball} is upstream's
     * {@code slimeballGreen} ore-dict entry, and every recipe here keys off it directly.
     */
    private static final List<SlimeBall> SLIME_BALLS = registerSlimeBalls();

    /** One colour's slime ball. */
    public record SlimeBall(SlimeColour colour, DeferredItem<Item> item) {}

    /** Every coloured slime ball, in declaration order -- datagen, tags and the creative tab walk this. */
    public static List<SlimeBall> slimeBalls() {
        return SLIME_BALLS;
    }

    /** One colour's slime ball as a crafting ingredient; green is vanilla's own slime ball. */
    public static net.minecraft.world.level.ItemLike slimeBall(SlimeColour colour) {
        return colour == SlimeColour.GREEN ? net.minecraft.world.item.Items.SLIME_BALL : slimeBallItem(colour).get();
    }

    /** One coloured slime ball's registry entry. Never green -- vanilla's slime ball is that colour. */
    public static DeferredItem<Item> slimeBallItem(SlimeColour colour) {
        return SLIME_BALLS.stream().filter(ball -> ball.colour() == colour).findFirst()
                .orElseThrow(() -> new IllegalStateException("no slime ball registered for " + colour))
                .item();
    }

    private static List<SlimeBall> registerSlimeBalls() {
        List<SlimeBall> balls = new java.util.ArrayList<>();
        // Upstream TinkerCommons:140-144 -- addFood(meta, hunger, saturation, name, effects...).
        // Durations are upstream's own tick counts (20 ticks to the second) and every effect is
        // applied with certainty, which is what ItemEdible#onFoodEaten does.
        balls.add(slimeBall(SlimeColour.BLUE, 1, 1f,
                new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20 * 45, 2),
                new MobEffectInstance(MobEffects.JUMP, 20 * 60, 2)));
        balls.add(slimeBall(SlimeColour.PURPLE, 1, 2f,
                new MobEffectInstance(MobEffects.UNLUCK, 20 * 45),
                new MobEffectInstance(MobEffects.LUCK, 20 * 60)));
        balls.add(slimeBall(SlimeColour.BLOOD, 1, 1.5f,
                new MobEffectInstance(MobEffects.POISON, 20 * 45, 2),
                new MobEffectInstance(MobEffects.HEALTH_BOOST, 20 * 60)));
        balls.add(slimeBall(SlimeColour.MAGMA, 2, 1f,
                new MobEffectInstance(MobEffects.WEAKNESS, 20 * 45),
                new MobEffectInstance(MobEffects.WITHER, 20 * 15),
                new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 20 * 60)));
        balls.add(slimeBall(SlimeColour.PINK, 1, 1f,
                new MobEffectInstance(MobEffects.CONFUSION, 20 * 10, 2)));
        return Collections.unmodifiableList(balls);
    }

    private static SlimeBall slimeBall(SlimeColour colour, int nutrition, float saturation, MobEffectInstance... effects) {
        return new SlimeBall(colour, ITEMS.registerItem(colour.id() + "_slime_ball", SlimeFoodItem::new,
                new Item.Properties().food(slimeFood(nutrition, saturation, effects))));
    }

    /**
     * The five slime drops (issue #649, parity audit T57): upstream 1.12's
     * {@code TinkerCommons#slimedrop*} ({@code TinkerCommons:373-377}), five more metas of its
     * {@code ItemEdible} registered behind the Gadgets pulse (NOTICE.md). Each is a food with
     * upstream's nutrition, saturation and single potion effect, always-edible for the same
     * {@code addFood} reason as the balls.
     *
     * <p>Green gets an item of its own here, unlike the slime balls -- vanilla has no slime drop.
     *
     * <p>Upstream's only source for these is its drying rack ({@code TinkerGadgets}'
     * {@code registerDryingRecipes}, slime ball in, drop out); Forgeweave has no drying rack yet
     * (the unplanned-gadget roster, T56/#487), so until one lands the drops are obtainable only in
     * creative -- registering a substitute source would be exactly the "close enough" deviation the
     * parity directive forbids.
     */
    private static final List<SlimeDrop> SLIME_DROPS = registerSlimeDrops();

    /** One colour's slime drop. */
    public record SlimeDrop(SlimeColour colour, DeferredItem<Item> item) {}

    /** Every slime drop, in declaration order -- datagen and the creative tab walk this. */
    public static List<SlimeDrop> slimeDrops() {
        return SLIME_DROPS;
    }

    /** One colour's slime drop registry entry. Pink has none -- upstream registers five drops. */
    public static DeferredItem<Item> slimeDrop(SlimeColour colour) {
        return SLIME_DROPS.stream().filter(drop -> drop.colour() == colour).findFirst()
                .orElseThrow(() -> new IllegalStateException("no slime drop registered for " + colour))
                .item();
    }

    private static List<SlimeDrop> registerSlimeDrops() {
        List<SlimeDrop> drops = new java.util.ArrayList<>();
        // Upstream TinkerCommons:373-377 -- addFood(meta, hunger, saturation, name, effect), one
        // 90-second effect each, durations in upstream's own tick counts.
        drops.add(slimeDrop(SlimeColour.GREEN, 1, 1f, new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20 * 90, 2)));
        drops.add(slimeDrop(SlimeColour.BLUE, 3, 1f, new MobEffectInstance(MobEffects.JUMP, 20 * 90, 2)));
        drops.add(slimeDrop(SlimeColour.PURPLE, 3, 2f, new MobEffectInstance(MobEffects.LUCK, 20 * 90)));
        drops.add(slimeDrop(SlimeColour.BLOOD, 3, 1.5f, new MobEffectInstance(MobEffects.HEALTH_BOOST, 20 * 90)));
        drops.add(slimeDrop(SlimeColour.MAGMA, 6, 1f, new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 20 * 90)));
        return Collections.unmodifiableList(drops);
    }

    private static SlimeDrop slimeDrop(SlimeColour colour, int nutrition, float saturation, MobEffectInstance... effects) {
        return new SlimeDrop(colour, ITEMS.registerItem(colour.id() + "_slime_drop", SlimeFoodItem::new,
                new Item.Properties().food(slimeFood(nutrition, saturation, effects))));
    }

    /** Mantle's {@code ItemEdible#addFood} shape shared by the balls and the drops. */
    private static FoodProperties slimeFood(int nutrition, float saturation, MobEffectInstance... effects) {
        FoodProperties.Builder food = new FoodProperties.Builder().nutrition(nutrition).saturationModifier(saturation).alwaysEdible();
        for (MobEffectInstance effect : effects) {
            food.effect(() -> new MobEffectInstance(effect), 1.0F);
        }
        return food.build();
    }

    // #232 -- knightslime's item forms (docs/SCOPE.md M3.2), alloy-only like manyullyn: no ore, no
    // raw form (upstream 1.12 has none either -- addCommonItems("Knightslime") is ingot/nugget/block).
    public static final DeferredItem<Item> INGOT_KNIGHTSLIME = ITEMS.registerSimpleItem("knightslime_ingot");
    public static final DeferredItem<Item> NUGGET_KNIGHTSLIME = ITEMS.registerSimpleItem("knightslime_nugget");

    // #235 -- amethyst bronze (M3.2): FW-native ingot/nugget, alloyed from molten copper +
    // amethyst, so no raw form and no ore. Textures are the 1.20 clone's own amethyst bronze art,
    // copied byte-for-byte (NOTICE.md) -- the first metal here whose upstream generation ships art.
    public static final DeferredItem<Item> INGOT_AMETHYST_BRONZE = ITEMS.registerSimpleItem("amethyst_bronze_ingot");
    public static final DeferredItem<Item> NUGGET_AMETHYST_BRONZE = ITEMS.registerSimpleItem("amethyst_bronze_nugget");

    // #843 -- queen's slime and hepatizon (closes #180, the 1.20-branch material gap): FW-native
    // ingot/nugget, alloyed at the smeltery (queen's slime from molten cobalt + gold + magma cream;
    // hepatizon from molten copper + cobalt + quartz), so no raw form and no ore, same shape as
    // amethyst bronze above. Textures are recolors of manyullyn's own art (no 1.20-generation art
    // exists for either), scripts/recolor_raw_ore.py.
    public static final DeferredItem<Item> INGOT_QUEENS_SLIME = ITEMS.registerSimpleItem("queens_slime_ingot");
    public static final DeferredItem<Item> NUGGET_QUEENS_SLIME = ITEMS.registerSimpleItem("queens_slime_nugget");
    public static final DeferredItem<Item> INGOT_HEPATIZON = ITEMS.registerSimpleItem("hepatizon_ingot");
    public static final DeferredItem<Item> NUGGET_HEPATIZON = ITEMS.registerSimpleItem("hepatizon_nugget");

    // #104 -- cobalt + ardite nether ore block items (docs/SCOPE.md M2 issue #104).
    public static final DeferredItem<BlockItem> COBALT_ORE = ITEMS.registerSimpleBlockItem("cobalt_ore", ForgeweaveBlocks.COBALT_ORE);
    public static final DeferredItem<BlockItem> ARDITE_ORE = ITEMS.registerSimpleBlockItem("ardite_ore", ForgeweaveBlocks.ARDITE_ORE);

    // #206 -- storage blocks for cobalt, ardite, manyullyn and rose gold: the basin refused every
    // metal but iron/copper/gold/netherite for lack of a block to cast (docs/SCOPE.md M2 metals).
    public static final DeferredItem<BlockItem> COBALT_BLOCK = ITEMS.registerSimpleBlockItem("cobalt_block", ForgeweaveBlocks.COBALT_BLOCK);
    public static final DeferredItem<BlockItem> ARDITE_BLOCK = ITEMS.registerSimpleBlockItem("ardite_block", ForgeweaveBlocks.ARDITE_BLOCK);
    public static final DeferredItem<BlockItem> MANYULLYN_BLOCK = ITEMS.registerSimpleBlockItem("manyullyn_block", ForgeweaveBlocks.MANYULLYN_BLOCK);
    public static final DeferredItem<BlockItem> ROSE_GOLD_BLOCK = ITEMS.registerSimpleBlockItem("rose_gold_block", ForgeweaveBlocks.ROSE_GOLD_BLOCK);
    public static final DeferredItem<BlockItem> STEEL_BLOCK = ITEMS.registerSimpleBlockItem("steel_block", ForgeweaveBlocks.STEEL_BLOCK);
    public static final DeferredItem<BlockItem> AMETHYST_BRONZE_BLOCK = ITEMS.registerSimpleBlockItem("amethyst_bronze_block", ForgeweaveBlocks.AMETHYST_BRONZE_BLOCK);

    // #843 -- queen's slime and hepatizon storage block items (closes #180).
    public static final DeferredItem<BlockItem> QUEENS_SLIME_BLOCK = ITEMS.registerSimpleBlockItem("queens_slime_block", ForgeweaveBlocks.QUEENS_SLIME_BLOCK);
    public static final DeferredItem<BlockItem> HEPATIZON_BLOCK = ITEMS.registerSimpleBlockItem("hepatizon_block", ForgeweaveBlocks.HEPATIZON_BLOCK);

    // #232 -- knightslime's storage block item (docs/SCOPE.md M3.2).
    public static final DeferredItem<BlockItem> KNIGHTSLIME_BLOCK = ITEMS.registerSimpleBlockItem("knightslime_block", ForgeweaveBlocks.KNIGHTSLIME_BLOCK);

    // #839 -- Track B's ore family (M6 epic #824). See dev.gkissel.forgeweave.trackb.TrackBOre for
    // the 12-material roster. Six registrations per material, same shape as cobalt/ardite/manyullyn's
    // own ingot/nugget/raw + ore-block-item/storage-block-item pattern above; no material.json exists
    // yet (that is #841's deliverable), so these are plain items with no stat/trait linkage.
    private static final Map<String, DeferredItem<Item>> TRACK_B_INGOTS = new LinkedHashMap<>();
    private static final Map<String, DeferredItem<Item>> TRACK_B_NUGGETS = new LinkedHashMap<>();
    private static final Map<String, DeferredItem<Item>> TRACK_B_RAW_ITEMS = new LinkedHashMap<>();
    private static final Map<String, DeferredItem<BlockItem>> TRACK_B_ORE_ITEMS = new LinkedHashMap<>();
    private static final Map<String, DeferredItem<BlockItem>> TRACK_B_STORAGE_BLOCK_ITEMS = new LinkedHashMap<>();
    private static final Map<String, DeferredItem<BlockItem>> TRACK_B_RAW_BLOCK_ITEMS = new LinkedHashMap<>();

    static {
        for (TrackBOre ore : TrackBOre.ALL) {
            TRACK_B_INGOTS.put(ore.id(), ITEMS.registerSimpleItem(ore.ingotId()));
            TRACK_B_NUGGETS.put(ore.id(), ITEMS.registerSimpleItem(ore.nuggetId()));
            TRACK_B_RAW_ITEMS.put(ore.id(), ITEMS.registerSimpleItem(ore.rawItemId()));
            TRACK_B_ORE_ITEMS.put(ore.id(), ITEMS.registerSimpleBlockItem(ore.oreBlockId(), ForgeweaveBlocks.trackBOre(ore.id())));
            TRACK_B_STORAGE_BLOCK_ITEMS.put(ore.id(), ITEMS.registerSimpleBlockItem(ore.storageBlockId(), ForgeweaveBlocks.trackBStorageBlock(ore.id())));
            TRACK_B_RAW_BLOCK_ITEMS.put(ore.id(), ITEMS.registerSimpleBlockItem(ore.rawBlockId(), ForgeweaveBlocks.trackBRawBlock(ore.id())));
        }
    }

    public static DeferredItem<Item> trackBIngot(String id) {
        return TRACK_B_INGOTS.get(id);
    }

    public static DeferredItem<Item> trackBNugget(String id) {
        return TRACK_B_NUGGETS.get(id);
    }

    public static DeferredItem<Item> trackBRawItem(String id) {
        return TRACK_B_RAW_ITEMS.get(id);
    }

    public static DeferredItem<BlockItem> trackBOreItem(String id) {
        return TRACK_B_ORE_ITEMS.get(id);
    }

    public static DeferredItem<BlockItem> trackBStorageBlockItem(String id) {
        return TRACK_B_STORAGE_BLOCK_ITEMS.get(id);
    }

    public static DeferredItem<BlockItem> trackBRawBlockItem(String id) {
        return TRACK_B_RAW_BLOCK_ITEMS.get(id);
    }

    // #840 -- Track B's 18 alloy tool materials (M6 epic #824). See
    // dev.gkissel.forgeweave.trackb.TrackBAlloy for the roster. Alloy-only, same "ingot/nugget/block
    // item, no raw form" shape pig iron and knightslime already use above -- no material.json yet
    // either (that is #841's deliverable), same as the ore family's own items.
    private static final Map<String, DeferredItem<Item>> TRACK_B_ALLOY_INGOTS = new LinkedHashMap<>();
    private static final Map<String, DeferredItem<Item>> TRACK_B_ALLOY_NUGGETS = new LinkedHashMap<>();
    private static final Map<String, DeferredItem<BlockItem>> TRACK_B_ALLOY_BLOCK_ITEMS = new LinkedHashMap<>();

    static {
        for (TrackBAlloy alloy : TrackBAlloy.ALL) {
            TRACK_B_ALLOY_INGOTS.put(alloy.id(), ITEMS.registerSimpleItem(alloy.ingotId()));
            TRACK_B_ALLOY_NUGGETS.put(alloy.id(), ITEMS.registerSimpleItem(alloy.nuggetId()));
            TRACK_B_ALLOY_BLOCK_ITEMS.put(alloy.id(), ITEMS.registerSimpleBlockItem(alloy.blockId(), ForgeweaveBlocks.trackBAlloyBlock(alloy.id())));
        }
    }

    public static DeferredItem<Item> trackBAlloyIngot(String id) {
        return TRACK_B_ALLOY_INGOTS.get(id);
    }

    public static DeferredItem<Item> trackBAlloyNugget(String id) {
        return TRACK_B_ALLOY_NUGGETS.get(id);
    }

    public static DeferredItem<BlockItem> trackBAlloyBlockItem(String id) {
        return TRACK_B_ALLOY_BLOCK_ITEMS.get(id);
    }

    // #452 -- the slime boots (parity audit T21), upstream 1.12's `gadgets/item/ItemSlimeBoots`.
    // Wearable in the boots slot, no armour of their own; see SlimeBootsItem for the bounce.
    public static final DeferredItem<SlimeBootsItem> SLIME_BOOTS = ITEMS.registerItem("slime_boots", SlimeBootsItem::new);

    // #233 -- pig iron (docs/SCOPE.md M3.2): ingot/nugget with upstream 1.12 art
    // (ingot_pigiron.png/nugget_pigiron.png, NOTICE.md) plus its storage block. No raw form: pig
    // iron is alloy-only, there is no ore to drop one (same reason netherite has none).
    public static final DeferredItem<Item> INGOT_PIG_IRON = ITEMS.registerSimpleItem("pig_iron_ingot");
    public static final DeferredItem<Item> NUGGET_PIG_IRON = ITEMS.registerSimpleItem("pig_iron_nugget");
    public static final DeferredItem<BlockItem> PIG_IRON_BLOCK = ITEMS.registerSimpleBlockItem("pig_iron_block", ForgeweaveBlocks.PIG_IRON_BLOCK);

    // #233 -- firewood (docs/SCOPE.md M3.2), the block item of ForgeweaveBlocks.FIREWOOD.
    public static final DeferredItem<BlockItem> FIREWOOD = ITEMS.registerSimpleBlockItem("firewood", ForgeweaveBlocks.FIREWOOD);

    // #275 -- clear glass and its 16 clear stained glass colors, the block items of
    // ForgeweaveBlocks.CLEAR_GLASS/CLEAR_STAINED_GLASS_*.
    public static final DeferredItem<BlockItem> CLEAR_GLASS = ITEMS.registerSimpleBlockItem("clear_glass", ForgeweaveBlocks.CLEAR_GLASS);
    public static final DeferredItem<BlockItem> CLEAR_STAINED_GLASS_WHITE = stainedGlassItem(ForgeweaveBlocks.CLEAR_STAINED_GLASS_WHITE);
    public static final DeferredItem<BlockItem> CLEAR_STAINED_GLASS_ORANGE = stainedGlassItem(ForgeweaveBlocks.CLEAR_STAINED_GLASS_ORANGE);
    public static final DeferredItem<BlockItem> CLEAR_STAINED_GLASS_MAGENTA = stainedGlassItem(ForgeweaveBlocks.CLEAR_STAINED_GLASS_MAGENTA);
    public static final DeferredItem<BlockItem> CLEAR_STAINED_GLASS_LIGHT_BLUE = stainedGlassItem(ForgeweaveBlocks.CLEAR_STAINED_GLASS_LIGHT_BLUE);
    public static final DeferredItem<BlockItem> CLEAR_STAINED_GLASS_YELLOW = stainedGlassItem(ForgeweaveBlocks.CLEAR_STAINED_GLASS_YELLOW);
    public static final DeferredItem<BlockItem> CLEAR_STAINED_GLASS_LIME = stainedGlassItem(ForgeweaveBlocks.CLEAR_STAINED_GLASS_LIME);
    public static final DeferredItem<BlockItem> CLEAR_STAINED_GLASS_PINK = stainedGlassItem(ForgeweaveBlocks.CLEAR_STAINED_GLASS_PINK);
    public static final DeferredItem<BlockItem> CLEAR_STAINED_GLASS_GRAY = stainedGlassItem(ForgeweaveBlocks.CLEAR_STAINED_GLASS_GRAY);
    public static final DeferredItem<BlockItem> CLEAR_STAINED_GLASS_LIGHT_GRAY = stainedGlassItem(ForgeweaveBlocks.CLEAR_STAINED_GLASS_LIGHT_GRAY);
    public static final DeferredItem<BlockItem> CLEAR_STAINED_GLASS_CYAN = stainedGlassItem(ForgeweaveBlocks.CLEAR_STAINED_GLASS_CYAN);
    public static final DeferredItem<BlockItem> CLEAR_STAINED_GLASS_PURPLE = stainedGlassItem(ForgeweaveBlocks.CLEAR_STAINED_GLASS_PURPLE);
    public static final DeferredItem<BlockItem> CLEAR_STAINED_GLASS_BLUE = stainedGlassItem(ForgeweaveBlocks.CLEAR_STAINED_GLASS_BLUE);
    public static final DeferredItem<BlockItem> CLEAR_STAINED_GLASS_BROWN = stainedGlassItem(ForgeweaveBlocks.CLEAR_STAINED_GLASS_BROWN);
    public static final DeferredItem<BlockItem> CLEAR_STAINED_GLASS_GREEN = stainedGlassItem(ForgeweaveBlocks.CLEAR_STAINED_GLASS_GREEN);
    public static final DeferredItem<BlockItem> CLEAR_STAINED_GLASS_RED = stainedGlassItem(ForgeweaveBlocks.CLEAR_STAINED_GLASS_RED);
    public static final DeferredItem<BlockItem> CLEAR_STAINED_GLASS_BLACK = stainedGlassItem(ForgeweaveBlocks.CLEAR_STAINED_GLASS_BLACK);

    // T22 (issue #453) -- the Slimesling, upstream 1.12's `tconstruct:slimesling` and the first of
    // its Gadgets content to land here. See SlimeSlingItem. This id is the green sling: upstream's
    // meta 0 is `SlimeType.GREEN`, and `forgeweave:slime_sling` is in the wild from the 0.3.x
    // alphas, so the five-colour split (#649) keeps green here rather than renaming it.
    public static final DeferredItem<SlimeSlingItem> SLIME_SLING = ITEMS.registerItem("slime_sling", SlimeSlingItem::new);

    /**
     * The six coloured Slimeslings (issue #649, parity audit T57): upstream 1.12 hangs a
     * {@code SlimeType} metadata subtype per colour off its one {@code ItemSlimeSling} -- the five
     * {@code VISIBLE_COLORS} it lists in creative plus the pink one its
     * {@code recipes/gadgets/slimesling/fallback.json} crafts from mixed slime (NOTICE.md).
     * Behaviour is identical across colours (upstream's item never reads its own meta outside
     * naming); only the recipe, name and tinted sprite differ, so every entry here is the same
     * {@link SlimeSlingItem}. Green is {@link #SLIME_SLING}, keeping the pre-split id.
     */
    private static final List<SlimeSling> SLIME_SLINGS = registerSlimeSlings();

    /** One colour's Slimesling. */
    public record SlimeSling(SlimeColour colour, DeferredItem<SlimeSlingItem> item) {}

    /** Every coloured sling, in {@link SlimeColour} order -- datagen and the creative tab walk this. */
    public static List<SlimeSling> slimeSlings() {
        return SLIME_SLINGS;
    }

    /** One colour's Slimesling registry entry; green is the pre-split {@link #SLIME_SLING}. */
    public static DeferredItem<SlimeSlingItem> slimeSling(SlimeColour colour) {
        return SLIME_SLINGS.stream().filter(sling -> sling.colour() == colour).findFirst()
                .orElseThrow(() -> new IllegalStateException("no sling registered for " + colour))
                .item();
    }

    private static List<SlimeSling> registerSlimeSlings() {
        List<SlimeSling> slings = new java.util.ArrayList<>();
        slings.add(new SlimeSling(SlimeColour.GREEN, SLIME_SLING));
        for (SlimeColour colour : SlimeColour.values()) {
            if (colour != SlimeColour.GREEN) {
                slings.add(new SlimeSling(colour,
                        ITEMS.registerItem(colour.id() + "_slime_sling", SlimeSlingItem::new)));
            }
        }
        return Collections.unmodifiableList(slings);
    }

    /**
     * T20 (issue #451) -- the blue slime's spawn egg. Upstream 1.12 asks for one in the same call
     * that registers the entity ({@code TinkerWorld#registerEntities} passes {@code hasEgg = true}),
     * with the two colours below; 1.21 makes a spawn egg an item of its own, so it is one here.
     * {@code DeferredSpawnEggItem} is NeoForge's deferred-safe form -- the plain vanilla
     * {@code SpawnEggItem} resolves its entity type at construction, which is too early for a
     * {@code DeferredRegister}.
     */
    public static final DeferredItem<DeferredSpawnEggItem> BLUE_SLIME_SPAWN_EGG = ITEMS.registerItem("blue_slime_spawn_egg",
            properties -> new DeferredSpawnEggItem(ForgeweaveEntities.BLUE_SLIME, 0x47eff5, 0xacfff4, properties));

    /**
     * The Dusk Cage (issue #886) -- what murkiron's {@code dusksnare} handle trait snares a beaten,
     * non-boss mob into, and the only item in the mod with no recipe and no creative-tab entry: it
     * only ever exists filled, made by a capture and spent by a release (see {@link DuskCageItem}).
     */
    public static final DeferredItem<DuskCageItem> DUSK_CAGE = ITEMS.registerItem("dusk_cage", DuskCageItem::new);

    private static DeferredItem<BlockItem> tankItem(DeferredBlock<? extends Block> block) {
        return ITEMS.registerItem(block.getId().getPath(),
                properties -> new SearedTankItem(block.get(), properties));
    }

    private static DeferredItem<BlockItem> stainedGlassItem(DeferredBlock<Block> block) {
        return ITEMS.registerSimpleBlockItem(block.getId().getPath(), block);
    }

    /** Registers {@code clay_<cast>} for every cast name given, keeping the order they arrive in. */
    private static Map<String, DeferredItem<Item>> clayCasts(String... casts) {
        Map<String, DeferredItem<Item>> clay = new LinkedHashMap<>();
        for (String cast : casts) {
            clay.put(cast, ITEMS.registerItem("clay_" + cast, ClayCastItem::new));
        }
        return Collections.unmodifiableMap(clay);
    }

    private ForgeweaveItems() {}
}
