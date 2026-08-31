package leader.module.modules.combat;

import leader.event.EventTarget;
import leader.event.types.EventType;
import leader.events.AttackEvent;
import leader.events.PacketEvent;
import leader.module.Module;
import leader.property.properties.BooleanProperty;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.item.ItemTool;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.client.C02PacketUseEntity;

import java.util.Optional;

import static leader.config.Config.mc;

public class AutoWeapon extends Module {
    private final BooleanProperty axe = new BooleanProperty("Axe", true);
    private boolean attackEnemy = false;

    public AutoWeapon() {
        super("AutoWeapon", false);
    }

    @EventTarget
    public void onAttack(AttackEvent event) {
        this.attackEnemy = true;
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (event.getType() != EventType.SEND || !isEnabled()) return;
        if (event.getPacket() instanceof C02PacketUseEntity
                && ((C02PacketUseEntity) event.getPacket()).getAction() == C02PacketUseEntity.Action.ATTACK
                && this.attackEnemy) {
            this.attackEnemy = false;
            int slot = -1;
            double maxDamage = 0.0;
            for (int i = 0; i < 9; ++i) {
                ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
                if (stack == null) continue;
                if (!(stack.getItem() instanceof ItemSword)
                        && (!(stack.getItem() instanceof ItemTool) || !this.axe.getValue())) {
                    continue;
                }
                double damage = getDamage(stack);
                if (!(damage > maxDamage)) continue;
                maxDamage = damage;
                slot = i;
            }
            if (slot == mc.thePlayer.inventory.currentItem || slot == -1) {
                return;
            }
            mc.thePlayer.inventory.currentItem = slot;
            mc.playerController.updateController();
            mc.getNetHandler().addToSendQueue(event.getPacket());
            event.setCancelled(true);
        }
    }

    private static double getDamage(ItemStack stack) {
        double base = 0.0;
        if (stack.getAttributeModifiers().get("generic.attackDamage") != null) {
            Optional<AttributeModifier> modifier = stack.getAttributeModifiers()
                    .get("generic.attackDamage").stream().findFirst();
            if (modifier.isPresent()) {
                base = modifier.get().getAmount();
            }
        }
        return base + 1.25 * getEnchantment(stack, Enchantment.sharpness);
    }

    public static int getEnchantment(ItemStack itemStack, Enchantment enchantment) {
        if (itemStack == null || itemStack.getEnchantmentTagList() == null || itemStack.getEnchantmentTagList().hasNoTags()) {
            return 0;
        }
        for (int i = 0; i < itemStack.getEnchantmentTagList().tagCount(); ++i) {
            final NBTTagCompound tagCompound = itemStack.getEnchantmentTagList().getCompoundTagAt(i);
            if (tagCompound.getShort("ench") == enchantment.effectId || tagCompound.getShort("id") == enchantment.effectId) {
                return tagCompound.getShort("lvl");
            }
        }
        return 0;
    }
}
