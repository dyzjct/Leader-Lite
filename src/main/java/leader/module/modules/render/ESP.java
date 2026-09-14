package leader.module.modules.render;

import leader.Leader;
import leader.enums.ChatColors;
import leader.event.EventTarget;
import leader.event.types.Priority;
import leader.events.Render2DEvent;
import leader.events.Render3DEvent;
import leader.events.ResizeEvent;
import leader.mixin.IAccessorEntityRenderer;
import leader.mixin.IAccessorRenderManager;
import leader.module.Module;
import leader.util.ColorUtil;
import leader.util.RenderUtil;
import leader.util.TeamUtil;
import leader.util.shader.GlowShader;
import leader.util.shader.OutlineShader;
import leader.property.properties.BooleanProperty;
import leader.property.properties.ModeProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.player.EntityPlayer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;

import javax.vecmath.Vector4d;
import java.awt.*;
import java.util.List;
import java.util.stream.Collectors;

public class ESP extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private OutlineShader outlineRenderer;
    private GlowShader glowShader;
    private Framebuffer framebuffer = null;
    private boolean outline = true;
    private boolean glow = true;
    public final ModeProperty mode = new ModeProperty("mode", 2, new String[]{"NONE", "2D", "3D", "OUTLINE", "FAKECORNER", "FAKE2D"});
    public final ModeProperty color = new ModeProperty("color", 0, new String[]{"DEFAULT", "TEAMS", "HUD"});
    public final ModeProperty healthBar = new ModeProperty("health-bar", 0, new String[]{"NONE", "2D", "RAVEN"});
    public final BooleanProperty players = new BooleanProperty("players", true);
    public final BooleanProperty friends = new BooleanProperty("friends", true);
    public final BooleanProperty enemies = new BooleanProperty("enemies", true);
    public final BooleanProperty self = new BooleanProperty("self", false);
    public final BooleanProperty bots = new BooleanProperty("bots", false);

    private boolean shouldRenderPlayer(EntityPlayer entityPlayer) {
        if (entityPlayer.deathTime > 0) {
            return false;
        } else if (mc.getRenderViewEntity().getDistanceToEntity(entityPlayer) > 512.0F) {
            return false;
        } else if (!entityPlayer.ignoreFrustumCheck && !RenderUtil.isInViewFrustum(entityPlayer.getEntityBoundingBox(), 0.1F)) {
            return false;
        } else if (entityPlayer != mc.thePlayer && entityPlayer != mc.getRenderViewEntity()) {
            if (TeamUtil.isBot(entityPlayer)) {
                return this.bots.getValue();
            } else if (TeamUtil.isFriend(entityPlayer)) {
                return this.friends.getValue();
            } else {
                return TeamUtil.isTarget(entityPlayer) ? this.enemies.getValue() : this.players.getValue();
            }
        } else {
            return this.self.getValue() && mc.gameSettings.thirdPersonView != 0;
        }
    }

    private Color getEntityColor(EntityPlayer entityPlayer) {
        if (TeamUtil.isFriend(entityPlayer)) {
            return Leader.friendManager.getColor();
        } else if (TeamUtil.isTarget(entityPlayer)) {
            return Leader.targetManager.getColor();
        } else {
            switch (this.color.getValue()) {
                case 0:
                    return TeamUtil.getTeamColor(entityPlayer, 1.0F);
                case 1:
                    int teamColor = TeamUtil.isSameTeam(entityPlayer) ? ChatColors.BLUE.toAwtColor() : ChatColors.RED.toAwtColor();
                    return new Color(teamColor);
                case 2:
                    int hudColor = ((HUD) Leader.moduleManager.modules.get(HUD.class)).getColor(System.currentTimeMillis()).getRGB();
                    return new Color(hudColor);
                default:
                    return new Color(-1);
            }
        }
    }

    public ESP() {
        super("ESP", false);
    }

    public boolean isOutlineEnabled() {
        return this.outline;
    }

    public boolean isGlowEnabled() {
        return this.glow;
    }

    @EventTarget
    public void onResize(ResizeEvent event) {
        if (this.framebuffer != null) {
            this.framebuffer.deleteFramebuffer();
            this.framebuffer = null;
        }
    }

    @EventTarget(Priority.HIGH)
    public void onRender(Render2DEvent event) {
        if (this.isEnabled() && (this.mode.getValue() == 1 || this.mode.getValue() == 3 || this.healthBar.getValue() == 1)) {
            List<EntityPlayer> renderedEntities = TeamUtil.getLoadedEntitiesSorted().stream().filter(entity -> entity instanceof EntityPlayer && this.shouldRenderPlayer((EntityPlayer) entity)).map(EntityPlayer.class::cast).collect(Collectors.toList());
            if (!renderedEntities.isEmpty()) {
                if (this.mode.getValue() == 3) {
                    this.renderOutline(renderedEntities, event.getPartialTicks());
                }
                if (this.mode.getValue() == 1 || this.healthBar.getValue() == 1) {
                    RenderUtil.enableRenderState();
                    double scaleFactor = new ScaledResolution(mc).getScaleFactor();
                    double scale = scaleFactor / Math.pow(scaleFactor, 2.0);
                    GlStateManager.pushMatrix();
                    GlStateManager.scale(scale, scale, scale);
                    for (EntityPlayer player : renderedEntities) {
                        ((IAccessorEntityRenderer) mc.entityRenderer).callSetupCameraTransform(event.getPartialTicks(), 0);
                        Vector4d screenPosition = RenderUtil.projectToScreen(player, scaleFactor);
                        mc.entityRenderer.setupOverlayRendering();
                        if (screenPosition != null) {
                            float x = (float) screenPosition.x;
                            float y = (float) screenPosition.y;
                            float z = (float) screenPosition.z;
                            float w = (float) screenPosition.w;
                            if (this.mode.getValue() == 1) {
                                int color = this.getEntityColor(player).getRGB();
                                RenderUtil.drawOutlineRect(x, y, z, w, 3.0F, 0, (color & 16579836) >> 2 | color & 0xFF000000);
                                RenderUtil.drawOutlineRect(x, y, z, w, 1.5F, 0, color);
                            }
                            if (this.healthBar.getValue() == 1) {
                                float heal = player.getHealth() + player.getAbsorptionAmount();
                                float percent = Math.min(Math.max(heal / player.getMaxHealth(), 0.0F), 1.0F);
                                float box = (z - x) * 0.08F;
                                Color healthColor = ColorUtil.getHealthBlend(percent);
                                RenderUtil.drawLine(x - box, y, x - box, w, 3.0F, ColorUtil.darker(healthColor, 0.2F).getRGB());
                                RenderUtil.drawLine(x - box, w, x - box, w + (y - w) * percent, 1.5F, healthColor.getRGB());
                            }
                        }
                    }
                    GlStateManager.popMatrix();
                    RenderUtil.disableRenderState();
                }
            }
        }
    }

    private void renderOutline(List<EntityPlayer> renderedEntities, float partialTicks) {
        if (!OpenGlHelper.isFramebufferEnabled() || mc.displayWidth < 1 || mc.displayHeight < 1) {
            return;
        }
        if (this.outlineRenderer == null) {
            this.outlineRenderer = new OutlineShader();
        }
        if (this.glowShader == null) {
            this.glowShader = new GlowShader();
        }
        if (!this.outlineRenderer.isUsable() || !this.glowShader.isUsable()) {
            return;
        }
        if (this.framebuffer == null || this.framebuffer.framebufferWidth != mc.displayWidth
                || this.framebuffer.framebufferHeight != mc.displayHeight) {
            if (this.framebuffer != null) {
                this.framebuffer.deleteFramebuffer();
            }
            this.framebuffer = new Framebuffer(mc.displayWidth, mc.displayHeight, false);
        }

        boolean shadow = mc.gameSettings.entityShadows;
        int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            GlStateManager.pushMatrix();
            GlStateManager.pushAttrib();
            this.framebuffer.framebufferClear();
            this.framebuffer.bindFramebuffer(false);
            ((IAccessorEntityRenderer) mc.entityRenderer).callSetupCameraTransform(partialTicks, 0);
            mc.gameSettings.entityShadows = false;
            this.outline = false;
            this.glow = false;
            GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
            this.glowShader.use();
            for (EntityPlayer player : renderedEntities) {
                Color entityColor = this.getEntityColor(player);
                this.glowShader.W(entityColor);
                boolean invisible = player.isInvisible();
                try {
                    player.setInvisible(false);
                    mc.getRenderManager().renderEntityStatic(player, partialTicks, true);
                } finally {
                    player.setInvisible(invisible);
                }
            }
            this.glowShader.stop();
            mc.entityRenderer.disableLightmap();
            mc.entityRenderer.setupOverlayRendering();
            mc.getFramebuffer().bindFramebuffer(false);
            GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
            GlStateManager.enableTexture2D();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GlStateManager.disableDepth();
            GlStateManager.depthMask(false);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            this.outlineRenderer.use();
            RenderUtil.drawFramebuffer(this.framebuffer);
            this.outlineRenderer.stop();
        } finally {
            GL20.glUseProgram(previousProgram);
            this.outline = true;
            this.glow = true;
            mc.gameSettings.entityShadows = shadow;
            mc.getFramebuffer().bindFramebuffer(false);
            GL13.glActiveTexture(previousActiveTexture);
            GlStateManager.bindTexture(previousTexture);
            GlStateManager.depthMask(true);
            GlStateManager.enableDepth();
            GlStateManager.enableAlpha();
            GlStateManager.enableTexture2D();
            GlStateManager.resetColor();
            GlStateManager.popAttrib();
            GlStateManager.popMatrix();
        }
    }

    @EventTarget
    public void onRender(Render3DEvent event) {
        if (this.isEnabled() && (this.mode.getValue() == 2 || this.mode.getValue() == 4 || this.mode.getValue() == 5 || this.healthBar.getValue() == 2)) {
            RenderUtil.enableRenderState();
            for (EntityPlayer player : TeamUtil.getLoadedEntitiesSorted().stream().filter(entity -> entity instanceof EntityPlayer && this.shouldRenderPlayer((EntityPlayer) entity)).map(EntityPlayer.class::cast).collect(Collectors.toList())) {
                if (player.ignoreFrustumCheck || RenderUtil.isInViewFrustum(player.getEntityBoundingBox(), 0.1F)) {
                    if (this.mode.getValue() == 2) {
                        Color color = this.getEntityColor(player);
                        RenderUtil.drawEntityBoundingBox(player, color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha(), 1.5F, 0.1F);
                        GlStateManager.resetColor();
                    }
                    if (this.mode.getValue() == 4) {
                        Color color = this.getEntityColor(player);
                        RenderUtil.drawCornerESP(player, color.getRed() / 255.0F, color.getGreen() / 255.0F, color.getBlue() / 255.0F);
                    }
                    if (this.mode.getValue() == 5) {
                        Color color = this.getEntityColor(player);
                        RenderUtil.drawFake2DESP(player, color.getRed() / 255.0F, color.getGreen() / 255.0F, color.getBlue() / 255.0F);
                    }
                    if (this.healthBar.getValue() == 2) {
                        double x = RenderUtil.lerpDouble(player.posX, player.lastTickPosX, event.getPartialTicks())
                                - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosX();
                        double y = RenderUtil.lerpDouble(player.posY, player.lastTickPosY, event.getPartialTicks())
                                - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosY()
                                - 0.1F;
                        double z = RenderUtil.lerpDouble(player.posZ, player.lastTickPosZ, event.getPartialTicks())
                                - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosZ();
                        GlStateManager.pushMatrix();
                        GlStateManager.translate(x, y, z);
                        GlStateManager.rotate(mc.getRenderManager().playerViewY * -1.0F, 0.0F, 1.0F, 0.0F);
                        float heal = player.getHealth() + player.getAbsorptionAmount();
                        float percent = Math.min(Math.max(heal / player.getMaxHealth(), 0.0F), 1.0F);
                        Color healthColor = ColorUtil.getHealthBlend(percent);
                        float height = player.height + 0.2F;
                        RenderUtil.drawRect3D(0.57250005F, -0.027500002F, 0.7275F, height + 0.027500002F, Color.black.getRGB());
                        RenderUtil.drawRect3D(0.6F, 0.0F, 0.70000005F, height, Color.darkGray.getRGB());
                        RenderUtil.drawRect3D(0.6F, 0.0F, 0.70000005F, height * percent, healthColor.getRGB());
                        GlStateManager.popMatrix();
                    }
                }
            }
            RenderUtil.disableRenderState();
        }
    }
}
