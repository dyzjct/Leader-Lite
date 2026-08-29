package leader.module.modules.combat;

import leader.event.EventTarget;
import leader.event.types.EventType;
import leader.events.AttackEvent;
import leader.events.MoveInputEvent;
import leader.events.UpdateEvent;
import leader.module.Module;
import leader.property.properties.BooleanProperty;
import leader.property.properties.FloatProperty;
import leader.property.properties.IntProperty;
import leader.property.properties.ModeProperty;
import leader.util.RandomUtil;
import leader.util.RotationUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;

public class KeepDistance extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final ModeProperty mode = new ModeProperty("mode", 0, new String[]{"STap", "ADTap"});
    public final FloatProperty distance = new FloatProperty("distance", 2.5F, 1.0F, 6.0F);
    public final BooleanProperty combosOnly = new BooleanProperty("combos-only", true);
    public final IntProperty combos = new IntProperty("combos", 3, 1, 10, this.combosOnly::getValue);

    private EntityLivingBase target;
    private int comboCount;
    private boolean keepDistanceActive;
    private int strafeDirection = 1;

    public KeepDistance() {
        super("KeepDistance", false);
    }

    @EventTarget
    public void onAttack(AttackEvent event) {
        if (!this.isEnabled()) return;
        if (event.getTarget() instanceof EntityLivingBase) {
            EntityLivingBase newTarget = (EntityLivingBase) event.getTarget();
            if (newTarget != this.target) {
                this.target = newTarget;
                this.comboCount = 0;
                this.keepDistanceActive = false;
                this.strafeDirection = RandomUtil.nextFloat(0.0F, 1.0F) < 0.5F ? 1 : -1;
            }
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE) return;
        if (this.target == null || this.target.isDead || this.target.deathTime > 0) {
            this.resetState();
            return;
        }
        if (this.combosOnly.getValue() && this.target.hurtTime == 1) {
            this.comboCount++;
        }
        double dist = RotationUtil.distanceToEntity(this.target);
        if (this.combosOnly.getValue()) {
            if (this.comboCount > this.combos.getValue()) {
                this.keepDistanceActive = true;
            }
        } else {
            this.keepDistanceActive = true;
        }
        if (this.keepDistanceActive && dist > this.distance.getValue()) {
            this.keepDistanceActive = false;
        }
        if (this.keepDistanceActive && mc.thePlayer.hurtTime > 0){
            this.keepDistanceActive = false;
        }
    }

    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        if (!this.isEnabled() || !this.keepDistanceActive || this.target == null) return;
        double dist = RotationUtil.distanceToEntity(this.target);
        if (dist >= this.distance.getValue()) return;
        if (this.mode.getValue() == 0) {
            mc.thePlayer.movementInput.moveForward = -1.0F;
        } else {
            mc.thePlayer.movementInput.moveStrafe = this.strafeDirection;
            mc.thePlayer.movementInput.moveForward = 0F;
        }
    }

    private void resetState() {
        this.target = null;
        this.comboCount = 0;
        this.keepDistanceActive = false;
    }

    @Override
    public void onDisabled() {
        this.resetState();
    }
}
