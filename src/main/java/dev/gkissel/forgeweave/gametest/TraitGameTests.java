package dev.gkissel.forgeweave.gametest;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.GameType;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ToolStationBlockEntity;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ToolItem;
import dev.gkissel.forgeweave.menu.ToolStationMenu;

/**
 * docs/SCOPE.md M1 issue #12's verification: one test per M1 trait, each proving the behavior that
 * distinguishes that trait, on a tool assembled through the real Tool Station
 * ({@link ToolAssembly#pickaxe}) so the material -&gt; trait assignment in the shipped JSON is part of
 * what is under test.
 *
 * <p>Magnitudes are upstream 1.12's, cited per trait in {@code ForgeweaveTraits}.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class TraitGameTests {

    /**
     * Wood -&gt; {@code forgeweave:ecological}: a damaged tool carried in an inventory slowly regains
     * durability on its own (1 per ~40 s), and a Broken one does not.
     *
     * <p>The trait is a 1-in-800 roll per tick, so this drives {@code inventoryTick} directly for
     * many ticks rather than waiting on world time: over 20&#160;000 ticks the chance of not a single
     * heal is about 1e-11, which is not a flake worth designing around.
     */
    @GameTest(template = "empty")
    public static void ecologicalRegeneratesDurabilityWhileCarried(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, pos, "wood", "wood", "wood");

        helper.assertTrue(List.of(traitId("ecological")).equals(pickaxe.get(ForgeweaveDataComponents.TRAITS.get())),
                "an all-wood tool should carry ecological exactly once, got "
                        + pickaxe.get(ForgeweaveDataComponents.TRAITS.get()));

        pickaxe.setDamageValue(40);
        tick(helper, player, pickaxe, 20_000);
        helper.assertTrue(pickaxe.getDamageValue() < 40,
                "ecological should have regenerated durability over 20000 ticks, still at " + pickaxe.getDamageValue());

        // Broken tools are inert: upstream's healing path returns early on one.
        pickaxe.hurtAndBreak(1_000, helper.getLevel(), player, brokenItem -> {});
        helper.assertTrue(ToolItem.isBroken(pickaxe), "the test needs a Broken tool here");
        int brokenDamage = pickaxe.getDamageValue();
        tick(helper, player, pickaxe, 20_000);
        helper.assertTrue(pickaxe.getDamageValue() == brokenDamage,
                "a Broken tool must not regenerate, went from " + brokenDamage + " to " + pickaxe.getDamageValue());

        helper.succeed();
    }

    /**
     * Stone -&gt; {@code forgeweave:cheap}: 5% more durability per repair item, and (upstream's
     * head-only {@code cheapskate}, folded into the same id) 20% off the assembled durability pool.
     * The stone-headed pickaxe's pool is 128 rather than 160, and it repairs for its head material's
     * 120 durability plus {@code 120 * 5 / 100 = 6}, so one cobblestone takes 126 off instead of 120.
     */
    @GameTest(template = "empty")
    public static void cheapRepairsMoreThanTheBaseAmount(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, pos, "stone", "wood", "wood");
        pickaxe.hurtAndBreak(1_000, helper.getLevel(), player, brokenItem -> {});
        helper.assertTrue(pickaxe.getDamageValue() == 127, "expected the 128-durability pickaxe Broken at 127 damage");

        ToolStationBlockEntity blockEntity = helper.getBlockEntity(pos);
        blockEntity.container().setItem(0, pickaxe);
        blockEntity.container().setItem(1, new ItemStack(Items.COBBLESTONE, 1));
        blockEntity.container().setItem(2, ItemStack.EMPTY);

        ToolStationMenu menu = ToolAssembly.menu(helper, player, pos, blockEntity);
        menu.broadcastChanges();

        helper.assertTrue(menu.getSlot(3).getItem().getDamageValue() == 1,
                "expected 127 - (120 + 5%) = 1 damage left with cheap, got "
                        + menu.getSlot(3).getItem().getDamageValue());

        helper.succeed();
    }

    /**
     * Flint -&gt; {@code forgeweave:crude}: 5% bonus damage, but only against a target with no armor.
     * Both comparisons hit an otherwise identical target with an otherwise identical tool (stone's
     * {@code cheap} touches nothing about damage), so the difference is the trait and nothing else.
     */
    @GameTest(template = "empty")
    public static void crudeAddsDamageOnlyAgainstUnarmoredTargets(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack flint = ToolAssembly.pickaxe(helper, player, pos, "flint", "flint", "flint");
        ItemStack stone = ToolAssembly.pickaxe(helper, player, pos, "stone", "stone", "stone");

        float unarmoredWithCrude = hit(helper, player, flint, false);
        float unarmoredWithout = hit(helper, player, stone, false);
        helper.assertTrue(Math.abs(unarmoredWithCrude - unarmoredWithout * 1.05F) < 0.01F,
                "crude should deal 5% more to an unarmored target: " + unarmoredWithCrude + " vs "
                        + unarmoredWithout);

        float armoredWithCrude = hit(helper, player, flint, true);
        float armoredWithout = hit(helper, player, stone, true);
        helper.assertTrue(Math.abs(armoredWithCrude - armoredWithout) < 0.01F,
                "crude must do nothing against an armored target: " + armoredWithCrude + " vs " + armoredWithout);

        helper.succeed();
    }

    /**
     * Bone -&gt; {@code forgeweave:fractured}: a flat +1.5 attack damage on the tool itself. Bone's head
     * gives 2.5 attack damage and the pickaxe's damage potential is 1.0, so the assembled tool's one
     * attack damage modifier reads 4.0 rather than 2.5.
     */
    @GameTest(template = "empty")
    public static void fracturedAddsFlatAttackDamage(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        ItemStack bone = ToolAssembly.pickaxe(helper, player, pos, "bone", "wood", "wood");
        helper.assertTrue(Math.abs(attackDamage(bone) - 4.0) < 0.001,
                "expected bone's 2.5 attack damage + fractured's 1.5 = 4.0, got " + attackDamage(bone));

        double withoutFractured = attackDamage(ToolAssembly.pickaxe(helper, player, pos, "stone", "wood", "wood"));
        helper.assertTrue(Math.abs(withoutFractured - 3.0) < 0.001,
                "a tool without fractured should read its head material's attack damage unchanged, got "
                        + withoutFractured);

        // A Broken tool has no attack modifiers at all, so the bonus cannot leak through one.
        bone.hurtAndBreak(10_000, helper.getLevel(), player, brokenItem -> {});
        helper.assertTrue(bone.getAttributeModifiers().modifiers().isEmpty(),
                "a Broken tool should carry no attack modifiers, trait or otherwise");

        helper.succeed();
    }

    /**
     * Hits a fresh pig with {@code tool} in hand and returns how much health it lost. A pig because
     * it has no armor of its own and, unlike a zombie, no randomised spawn equipment; armor is set on
     * the attribute rather than by equipping a chestplate, since equipment only reaches the attribute
     * on the entity's next tick.
     */
    private static float hit(GameTestHelper helper, Player player, ItemStack tool, boolean armored) {
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(2, 2, 2));
        if (armored) {
            pig.getAttribute(Attributes.ARMOR).setBaseValue(6.0);
        }
        player.setItemInHand(InteractionHand.MAIN_HAND, tool);

        float before = pig.getHealth();
        DamageSource source = helper.getLevel().damageSources().playerAttack(player);
        helper.assertTrue(source.getWeaponItem() == tool, "the attack must be attributed to the tool being tested");
        pig.hurt(source, 4.0F);
        float lost = before - pig.getHealth();

        pig.discard();
        return lost;
    }

    private static double attackDamage(ItemStack stack) {
        for (ItemAttributeModifiers.Entry entry : stack.getAttributeModifiers().modifiers()) {
            if (entry.attribute().is(Attributes.ATTACK_DAMAGE.getKey())
                    && entry.modifier().operation() == AttributeModifier.Operation.ADD_VALUE) {
                return entry.modifier().amount();
            }
        }
        throw new AssertionError("no attack damage modifier on " + stack);
    }

    private static void tick(GameTestHelper helper, LivingEntity holder, ItemStack stack, int ticks) {
        for (int i = 0; i < ticks; i++) {
            stack.getItem().inventoryTick(stack, helper.getLevel(), holder, 0, false);
        }
    }

    private static ResourceLocation traitId(String path) {
        return ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, path);
    }
}
