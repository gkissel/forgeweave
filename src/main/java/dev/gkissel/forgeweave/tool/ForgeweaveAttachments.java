package dev.gkissel.forgeweave.tool;

import java.util.function.Supplier;

import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import dev.gkissel.forgeweave.Forgeweave;

/** Data attachments Forgeweave hangs off entities. Registered in {@code Forgeweave}. */
public final class ForgeweaveAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Forgeweave.MODID);

    /**
     * The melee damage ledger every damaged {@code LivingEntity} carries (docs/SCOPE.md M7, D-M7-4;
     * issue #919) -- see {@link DamageXpLedger}. Serialized with the entity, so the mob may be saved,
     * unloaded and reloaded between the hit and the death without losing what it owes; empty ledgers
     * are skipped, which is every entity in the world that has never been hit with a Forgeweave tool.
     */
    public static final Supplier<AttachmentType<DamageXpLedger>> DAMAGE_XP =
            ATTACHMENT_TYPES.register("damage_xp", () -> AttachmentType.builder(DamageXpLedger::new)
                    .serialize(DamageXpLedger.CODEC, ledger -> !ledger.isEmpty())
                    .build());

    private ForgeweaveAttachments() {}
}
