package dev.gkissel.forgeweave.client.book;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.item.PartItem;
import dev.gkissel.forgeweave.material.Material;
import dev.gkissel.forgeweave.menu.ToolAssemblyRecipes;
import dev.gkissel.forgeweave.trackb.TrackBOre;

/**
 * The diagram under the Materials chapter's "The Mining Ladder" page: one rung per tool tier, the
 * head materials that first reach it, and the ores a tool on that rung opens. Read from a
 * {@code book/ladder/*.json} file named by a text page's {@code data} field; {@link BookScreen}
 * draws it, this class supplies the stacks and the hover text.
 *
 * <p>The heads are a curated "first way up" list rather than every material on the rung, which
 * is what the material grids already show. {@code BookLadderTest} keeps each head on the rung it
 * is listed under.
 */
public record MiningLadder(List<Rung> rungs) {

    /** One rung: its tier name ({@code tooltip.forgeweave.tier.<tier>}), its heads, its ores. */
    public record Rung(String tier, List<ResourceLocation> heads, List<Ore> ores) {}

    /**
     * One ore the rung opens. {@code where} names the dimension for an ore that is not a
     * {@link TrackBOre}, whose dimension and height band are read off the ore table instead.
     */
    public record Ore(ResourceLocation item, @Nullable String where) {}

    public static final String WHERE = "book.forgeweave.ladder.where.";
    public static final String Y_RANGE = "book.forgeweave.ladder.y_range";
    public static final String OPEN_PAGE = "book.forgeweave.ladder.open_page";
    public static final String MINES_BELOW = "book.forgeweave.ladder.mines_below";
    private static final ResourceLocation FALLBACK_MATERIAL =
            ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "wood");

    public static MiningLadder load(String path) {
        return parse(BookStructure.read(path).getAsJsonObject());
    }

    /** The raw-JSON seam, so the parse contract is unit-testable without a data file. */
    public static MiningLadder parse(String json) {
        return parse(JsonParser.parseString(json).getAsJsonObject());
    }

    private static MiningLadder parse(JsonObject json) {
        List<Rung> rungs = new ArrayList<>();
        for (JsonElement element : json.getAsJsonArray("rungs")) {
            JsonObject rung = element.getAsJsonObject();
            List<ResourceLocation> heads = new ArrayList<>();
            for (JsonElement head : rung.getAsJsonArray("heads")) {
                heads.add(ResourceLocation.parse(head.getAsString()));
            }
            List<Ore> ores = new ArrayList<>();
            for (JsonElement ore : rung.getAsJsonArray("mines")) {
                JsonObject entry = ore.getAsJsonObject();
                ores.add(new Ore(ResourceLocation.parse(entry.get("item").getAsString()),
                        entry.has("where") ? entry.get("where").getAsString() : null));
            }
            rungs.add(new Rung(rung.get("tier").getAsString(), List.copyOf(heads), List.copyOf(ores)));
        }
        return new MiningLadder(List.copyOf(rungs));
    }

    public static Component tierName(String tier) {
        return Component.translatable("tooltip.forgeweave.tier." + tier);
    }

    public static Optional<Material> material(HolderLookup.Provider registries, ResourceLocation id) {
        return registries.lookupOrThrow(Material.REGISTRY).get(ResourceKey.create(Material.REGISTRY, id))
                .map(holder -> holder.value());
    }

    /** What a head draws as: the material's own item, the one its grid cell already shows. */
    public static ItemStack headIcon(HolderLookup.Provider registries, ResourceLocation id) {
        return material(registries, id).map(MaterialPageContent::representativeItem).orElse(ItemStack.EMPTY);
    }

    /**
     * A head's hover: its name in its colour, the rung it reaches, how it is made when that is
     * more than the Part Builder (an alloy's inputs, or what melts into it), and that a click
     * opens its page.
     */
    public static List<Component> headTooltip(HolderLookup.Provider registries, ResourceLocation id, String tier) {
        List<Component> lines = new ArrayList<>();
        Optional<Material> material = material(registries, id);
        Component name = Component.translatable("material." + id.getNamespace() + "." + id.getPath());
        lines.add(material.map(m -> name.copy().withStyle(Style.EMPTY.withColor(m.color()))).orElse(name.copy()));
        lines.add(tierLine(tier));
        material.flatMap(m -> MaterialPageContent.madeFrom(registries, id, m)).ifPresent(made -> {
            lines.add(Component.translatable(made.headerKey()).withStyle(ChatFormatting.GRAY));
            for (MaterialPageContent.Source source : made.sources()) {
                lines.add(Component.literal("  ").append(source.label()).withStyle(ChatFormatting.GRAY));
            }
        });
        lines.add(Component.translatable(OPEN_PAGE).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        return List.copyOf(lines);
    }

    /**
     * The rung's pickaxe, built from its first head. A part the head material has no stats for
     * (a gem with no handle block, say) falls back to wood, which only changes the handle's look:
     * the head alone decides the tier, which is what the icon is there to show.
     */
    public static ItemStack pickaxe(HolderLookup.Provider registries, Rung rung) {
        if (rung.heads().isEmpty()) {
            return ItemStack.EMPTY;
        }
        ResourceLocation head = rung.heads().get(0);
        Optional<Material> material = material(registries, head);
        return ToolAssemblyRecipes.ENTRIES.stream()
                .filter(entry -> entry.tool().get() == ForgeweaveItems.TOOL_PICKAXE.get())
                .findFirst()
                .flatMap(entry -> {
                    List<ResourceLocation> materials = new ArrayList<>();
                    for (PartItem part : entry.parts()) {
                        boolean usable = part.kind() == PartItem.Kind.HEAD
                                || material.map(m -> m.hasStatsFor(part.kind())).orElse(false);
                        materials.add(usable ? head : FALLBACK_MATERIAL);
                    }
                    return ToolAssemblyRecipes.assemble(registries, entry, materials);
                })
                .orElse(ItemStack.EMPTY);
    }

    public static List<Component> pickaxeTooltip(ItemStack pickaxe, String tier) {
        return List.of(pickaxe.getHoverName(), tierLine(tier),
                Component.translatable(MINES_BELOW).withStyle(ChatFormatting.GRAY));
    }

    public static ItemStack oreIcon(Ore ore) {
        return new ItemStack(BuiltInRegistries.ITEM.get(ore.item()));
    }

    /** An ore's hover: its name, then where it generates. */
    public static List<Component> oreTooltip(Ore ore, ItemStack stack) {
        List<Component> lines = new ArrayList<>();
        lines.add(stack.getHoverName());
        Optional<TrackBOre> track = ore.item().getNamespace().equals(Forgeweave.MODID)
                ? TrackBOre.ALL.stream().filter(t -> t.oreBlockId().equals(ore.item().getPath())).findFirst()
                : Optional.empty();
        if (track.isPresent()) {
            lines.add(Component.translatable(WHERE + where(track.get().host())).append(", ")
                    .append(Component.translatable(Y_RANGE, track.get().minY(), track.get().maxY()))
                    .withStyle(ChatFormatting.GRAY));
        } else if (ore.where() != null) {
            lines.add(Component.translatable(WHERE + ore.where()).withStyle(ChatFormatting.GRAY));
        }
        return List.copyOf(lines);
    }

    private static String where(TrackBOre.Host host) {
        return switch (host) {
            case OVERWORLD_STONE, OVERWORLD_DEEPSLATE -> "overworld";
            case NETHER -> "nether";
            case END -> "end";
        };
    }

    private static Component tierLine(String tier) {
        return Component.translatable("tooltip.forgeweave.tool_tier").append(": ").append(tierName(tier))
                .withStyle(ChatFormatting.GRAY);
    }
}
