package leader.module.modules.legit;

import leader.Leader;
import leader.event.EventTarget;
import leader.event.types.EventType;
import leader.events.KeyEvent;
import leader.events.TickEvent;
import leader.module.Module;
import leader.module.modules.player.Reach;
import leader.util.*;
import leader.property.properties.BooleanProperty;
import leader.property.properties.FloatProperty;
import leader.property.properties.PercentProperty;
import leader.property.properties.IntProperty;
import leader.property.properties.ModeProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import net.minecraft.util.Vec3;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class AimAssist extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final TimerUtil timer = new TimerUtil();
    public final ModeProperty rotationMode = new ModeProperty("rotation-mode", 0, new String[]{"Normal", "LockBox"});
    public final FloatProperty hSpeed = new FloatProperty("horizontal-speed", 3.0F, 0.0F, 10.0F);
    public final FloatProperty vSpeed = new FloatProperty("vertical-speed", 0.0F, 0.0F, 10.0F);
    public final PercentProperty smoothing = new PercentProperty("smoothing", 50);
    public final FloatProperty range = new FloatProperty("range", 4.5F, 3.0F, 8.0F);
    public final IntProperty fov = new IntProperty("fov", 90, 30, 360);
    public final BooleanProperty weaponOnly = new BooleanProperty("weapons-only", true);
    public final BooleanProperty allowTools = new BooleanProperty("allow-tools", false, this.weaponOnly::getValue);
    public final BooleanProperty botChecks = new BooleanProperty("bot-check", true);
    public final BooleanProperty team = new BooleanProperty("teams", true);

    private boolean isValidTarget(EntityPlayer entityPlayer) {
        if (entityPlayer != mc.thePlayer && entityPlayer != mc.thePlayer.ridingEntity) {
            if (entityPlayer == mc.getRenderViewEntity() || entityPlayer == mc.getRenderViewEntity().ridingEntity) {
                return false;
            } else if (entityPlayer.deathTime > 0) {
                return false;
            } else if (RotationUtil.distanceToEntity(entityPlayer) > (double) this.range.getValue()) {
                return false;
            } else if (RotationUtil.angleToEntity(entityPlayer) > (float) this.fov.getValue()) {
                return false;
            } else if (RotationUtil.rayTrace(entityPlayer) != null) {
                return false;
            } else if (TeamUtil.isFriend(entityPlayer)) {
                return false;
            } else {
                return (!this.team.getValue() || !TeamUtil.isSameTeam(entityPlayer)) && (!this.botChecks.getValue() || !TeamUtil.isBot(entityPlayer));
            }
        } else {
            return false;
        }
    }

    private boolean isInReach(EntityPlayer entityPlayer) {
        Reach reach = (Reach) Leader.moduleManager.modules.get(Reach.class);
        double distance = reach.isEnabled() ? (double) reach.range.getValue() : 3.0;
        return RotationUtil.distanceToEntity(entityPlayer) <= distance;
    }

    private boolean isLookingAtBlock() {
        return mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == MovingObjectType.BLOCK;
    }

    private float[] getLockBoxRotation(EntityPlayer player) {
        Vec3 eyePos = mc.thePlayer.getPositionEyes(1.0f);
        double headY = player.posY + player.getEyeHeight();
        double halfSize = 0.2;
        AxisAlignedBB headBox = new AxisAlignedBB(
                player.posX - halfSize, headY - halfSize, player.posZ - halfSize,
                player.posX + halfSize, headY + halfSize, player.posZ + halfSize
        );
        Vec3 lookVec = mc.thePlayer.getLookVec();
        double reach = this.range.getValue() * 2.0;
        if (headBox.calculateIntercept(eyePos, eyePos.addVector(lookVec.xCoord * reach, lookVec.yCoord * reach, lookVec.zCoord * reach)) != null) {
            return null;
        }
        Vec3 probe = eyePos.addVector(lookVec.xCoord * reach, lookVec.yCoord * reach, lookVec.zCoord * reach);
        Vec3 target = RotationUtil.getClosestPointOnBox(probe, headBox);
        float[] desired = RotationUtil.getRotations(target.xCoord, target.yCoord, target.zCoord, eyePos.xCoord, eyePos.yCoord, eyePos.zCoord);
        float yaw = Math.min(Math.abs(this.hSpeed.getValue()), 10.0F);
        float pitch = Math.min(Math.abs(this.vSpeed.getValue()), 10.0F);
        return new float[]{
                mc.thePlayer.rotationYaw + (desired[0] - mc.thePlayer.rotationYaw) * 0.1F * yaw,
                mc.thePlayer.rotationPitch + (desired[1] - mc.thePlayer.rotationPitch) * 0.1F * pitch
        };
    }

    public AimAssist() {
        super("AimAssist", false);
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (this.isEnabled() && event.getType() == EventType.POST && mc.currentScreen == null) {
            if (!(Boolean) this.weaponOnly.getValue()
                    || ItemUtil.hasRawUnbreakingEnchant()
                    || this.allowTools.getValue() && ItemUtil.isHoldingTool()) {
                boolean attacking = PlayerUtil.isAttacking();
                if (!attacking || !this.isLookingAtBlock()) {
                    if (attacking || !this.timer.hasTimeElapsed(350L)) {
                        List<EntityPlayer> inRange = mc.theWorld
                                .loadedEntityList
                                .stream()
                                .filter(entity -> entity instanceof EntityPlayer)
                                .map(entity -> (EntityPlayer) entity)
                                .filter(this::isValidTarget)
                                .sorted(Comparator.comparingDouble(RotationUtil::distanceToEntity))
                                .collect(Collectors.toList());
                        if (!inRange.isEmpty()) {
                            if (inRange.stream().anyMatch(this::isInReach)) {
                                inRange.removeIf(entityPlayer -> !this.isInReach(entityPlayer));
                            }
                            EntityPlayer player = inRange.get(0);
                            if (!(RotationUtil.distanceToEntity(player) <= 0.0)) {
                                if (this.rotationMode.getValue() == 1) {
                                    float[] rotation = this.getLockBoxRotation(player);
                                    if (rotation == null) return;
                                    Leader.rotationManager.setRotation(rotation[0], rotation[1], 0, false);
                                } else {
                                    AxisAlignedBB axisAlignedBB = player.getEntityBoundingBox();
                                    double collisionBorderSize = player.getCollisionBorderSize();
                                    float[] rotation = RotationUtil.getRotationsToBox(
                                            axisAlignedBB.expand(collisionBorderSize, collisionBorderSize, collisionBorderSize),
                                            mc.thePlayer.rotationYaw,
                                            mc.thePlayer.rotationPitch,
                                            180.0F,
                                            (float) this.smoothing.getValue() / 100.0F
                                    );
                                    float yaw = Math.min(Math.abs(this.hSpeed.getValue()), 10.0F);
                                    float pitch = Math.min(Math.abs(this.vSpeed.getValue()), 10.0F);
                                    Leader.rotationManager
                                            .setRotation(
                                                    mc.thePlayer.rotationYaw + (rotation[0] - mc.thePlayer.rotationYaw) * 0.1F * yaw,
                                                    mc.thePlayer.rotationPitch + (rotation[1] - mc.thePlayer.rotationPitch) * 0.1F * pitch,
                                                    0,
                                                    false
                                            );
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @EventTarget
    public void onPress(KeyEvent event) {
        if (event.getKey() == mc.gameSettings.keyBindAttack.getKeyCode() && !Leader.moduleManager.modules.get(AutoClicker.class).isEnabled()) {
            this.timer.reset();
        }
    }
}
