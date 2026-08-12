package dev.gkissel.forgeweave.combat;

import java.util.function.Consumer;

import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.modifier.ForgeweaveModifiers;
import dev.gkissel.forgeweave.modifier.ModifierEntry;

/**
 * Beheading (docs/SCOPE.md M3 issue #158): a killing blow struck with a beheaded tool has a chance to
 * drop the victim's head. Ported from upstream 1.12's {@code tools/modifiers/ModBeheading} (NOTICE.md),
 * with its two event handlers collapsed onto the one post-kill seam ADR-0005 decision 3 names as the
 * attachment point for exactly this ({@link CombatSeam#postKill}'s own javadoc calls out beheading).
 *
 * <h2>Clone constants</h2>
 *
 * <ul>
 *   <li>{@link #CHANCE_DENOMINATOR} -- upstream {@code ModBeheading#shouldDropHead}:
 *       {@code level > 0 && level > random.nextInt(10)}, i.e. {@code level/10}, certain from level 10.
 *   <li>{@link #MAX_MODIFIER_LEVEL} -- upstream's {@code new ModifierAspect.LevelAspect(this, 10)}.
 *       Lives in {@code beheading.json} as the actual cap (ADR-0004 decision 1: costs and caps are
 *       data); this constant is the clone number that JSON reproduces.
 *   <li>{@link #CLEAVER_INNATE_LEVELS} -- upstream {@code Cleaver#addMaterialTraits} applies
 *       {@code CLEAVER_BEHEADING_MOD} twice, so every cleaver starts at 2 levels.
 * </ul>
 *
 * <h2>Innate + modifier stack</h2>
 *
 * <p>Upstream keeps the cleaver's innate levels under a <em>second</em> modifier id
 * ({@code beheading_cleaver}) and then has the real modifier's {@code applyEffect} absorb that id's
 * level into its own the first time a player applies ender pearls to a cleaver -- an NBT dance whose
 * only observable result is that the two add up ({@code getBeheadingLevel} falls back to the cleaver
 * id when the modifier isn't there, and the absorb makes the sum apply when it is). {@link
 * #effectiveLevel} is that observable result stated directly: innate plus applied, no second id and no
 * migration step. The cap only ever bound the applied half upstream too, since the absorb runs after
 * {@code LevelAspect}'s check.
 *
 * <h2>Which entities have a head</h2>
 *
 * <p>{@link #headDropFor} is the whole table, and its default is "nothing" -- docs/SCOPE.md M3:
 * headless mobs drop nothing. Upstream registers four ({@code TinkerModifiers#registerMobHeadDrops}:
 * skeleton, wither skeleton, zombie, creeper) plus the player; piglin and dragon heads are vanilla
 * items 1.12 either didn't have (piglin) or never wired up (dragon), and docs/SCOPE.md M3 names all
 * six, so they are added here on the same table.
 *
 * <p>ponytail: a switch on {@code EntityType}, not upstream's open registry of
 * {@code Class -> Function<Entity, ItemStack>}. That registry exists so other mods can add head drops
 * over IMC ({@code IMCIntegration}); nothing in Forgeweave asks for that yet, and a mod that wants it
 * can listen to the same death event this rides. Six entries do not need an extension point.
 */
public final class Beheading implements CombatSeam {

    /** The modifier id, and the reagent/cap live in {@code modifier_recipe/beheading.json}. */
    public static final ResourceLocation MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "beheading");

    /** Upstream {@code ModBeheading#shouldDropHead}'s {@code random.nextInt(10)}. */
    public static final int CHANCE_DENOMINATOR = 10;

    /** Upstream {@code new ModifierAspect.LevelAspect(this, 10)}; {@code beheading.json} is the real cap. */
    public static final int MAX_MODIFIER_LEVEL = 10;

    /** Upstream {@code Cleaver#addMaterialTraits} applies the cleaver's beheading modifier twice. */
    public static final int CLEAVER_INNATE_LEVELS = 2;

    private final int level;

    private Beheading(int level) {
        this.level = level;
    }

    /**
     * The {@link CombatSeams.Provider} for this behavior, registered in {@code Forgeweave}. Resolves
     * the tool's effective level per hit and hands back a seam already carrying it, which is the shape
     * {@link CombatSeam}'s javadoc asks of a parameterized behavior.
     */
    public static void collect(ItemStack weapon, Consumer<CombatSeam> out) {
        int level = effectiveLevel(weapon);
        if (level > 0) {
            out.accept(new Beheading(level));
        }
    }

    /**
     * This tool's beheading level: the cleaver's innate {@link #CLEAVER_INNATE_LEVELS} plus whatever
     * the beheading modifier has been applied to it, as the class javadoc explains. Public so a
     * GameTest can assert the stack directly rather than only through a drop roll.
     *
     * <p>ponytail: the innate is a plain "is this the cleaver" check here rather than a field on
     * {@code ToolItem}, because {@link CombatSeams}'s contract already puts "does this seam apply to
     * this tool, and with what magnitude" in the provider. One tool carries this innate; when a second
     * one does, this becomes a map.
     */
    public static int effectiveLevel(ItemStack weapon) {
        int innate = weapon.is(ForgeweaveItems.TOOL_CLEAVER.get()) ? CLEAVER_INNATE_LEVELS : 0;
        ModifierEntry applied = ForgeweaveModifiers.entry(weapon, MODIFIER_ID);
        return innate + (applied == null ? 0 : applied.level());
    }

    /**
     * Upstream {@code ModBeheading#shouldDropHead}, ported whole. Pure and public so the "a higher
     * level drops more heads" test can drive it off a seeded {@link RandomSource} instead of counting
     * corpses.
     */
    public static boolean rollsHead(int level, RandomSource random) {
        return level > 0 && level > random.nextInt(CHANCE_DENOMINATOR);
    }

    /**
     * The head {@code target} drops when beheaded, or {@link ItemStack#EMPTY} for anything headless.
     * A player's head carries their game profile, so it renders as their skin and stacks separately --
     * upstream writes the same {@code SkullOwner} NBT, which 1.21 models as the
     * {@code minecraft:profile} component.
     */
    public static ItemStack headDrop(LivingEntity target) {
        if (target instanceof Player player) {
            ItemStack head = new ItemStack(Items.PLAYER_HEAD);
            head.set(DataComponents.PROFILE, new ResolvableProfile(player.getGameProfile()));
            return head;
        }
        return headDropFor(target.getType());
    }

    /**
     * The head table itself -- see the class javadoc. Split from {@link #headDrop} so the six vanilla
     * types can be asserted without spawning one of each (the ender dragon in particular is a boss
     * entity a GameTest structure has no room for).
     */
    public static ItemStack headDropFor(EntityType<?> type) {
        if (type == EntityType.SKELETON) {
            return new ItemStack(Items.SKELETON_SKULL);
        }
        if (type == EntityType.WITHER_SKELETON) {
            return new ItemStack(Items.WITHER_SKELETON_SKULL);
        }
        if (type == EntityType.ZOMBIE) {
            return new ItemStack(Items.ZOMBIE_HEAD);
        }
        if (type == EntityType.CREEPER) {
            return new ItemStack(Items.CREEPER_HEAD);
        }
        if (type == EntityType.PIGLIN) {
            return new ItemStack(Items.PIGLIN_HEAD);
        }
        if (type == EntityType.ENDER_DRAGON) {
            return new ItemStack(Items.DRAGON_HEAD);
        }
        return ItemStack.EMPTY;
    }

    /**
     * Rolls the chance and, on a hit, drops the head where the victim fell.
     *
     * <p>Upstream needs two handlers for this -- {@code LivingDropsEvent} to slot the head in among a
     * mob's loot, and a lowest-priority {@code LivingDeathEvent} for the {@code keepInventory} case
     * where a dying player never produces a drops list at all. Spawning the item directly from the one
     * death seam covers both: it is not gated on {@code doMobLoot} or {@code keepInventory}, so a
     * beheaded victim yields its head under every rule combination. The cost is that upstream's
     * {@code alreadyContainsDrop} de-duplication has nothing to inspect yet (loot has not rolled), so a
     * wither skeleton that also rolled its own 2.5% skull can drop two -- flagged in the PR; upstream
     * suppressed that, and suppressing it here means moving to the drops event and losing the
     * {@code keepInventory} case.
     */
    @Override
    public void postKill(CombatHit hit) {
        if (!rollsHead(level, hit.level().getRandom())) {
            return;
        }
        ItemStack head = headDrop(hit.target());
        if (!head.isEmpty()) {
            hit.target().spawnAtLocation(head);
        }
    }
}
