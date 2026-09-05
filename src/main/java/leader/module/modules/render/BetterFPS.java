package leader.module.modules.render;

import leader.event.EventTarget;
import leader.events.Render3DEvent;
import leader.module.Module;
import leader.property.properties.BooleanProperty;
import leader.property.properties.ModeProperty;
import leader.util.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;

public class BetterFPS extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public BetterFPS() {
        super("BetterFPS", false);
    }

    public static BooleanProperty fastLoad = new BooleanProperty("FastLoad", true);
    public static BooleanProperty entityOptimize = new BooleanProperty("EntityOptimize", false);
    public static ModeProperty entityLevel = new ModeProperty("EntityLevel", 0, new String[]{"Normal", "Fast", "Extreme"}, () -> entityOptimize.getValue());
    public static boolean using = false;

    public static boolean shouldCancelEntity(Entity entity) {
        if (!using || !entityOptimize.getValue()) {
            return false;
        }
        int level = entityLevel.getValue();
        if (level == 2) {
            return true;
        }
        if (level == 1 && mc.theWorld != null && mc.thePlayer != null) {
            return entity.getDistanceToEntity(mc.thePlayer) > 48.0;
        }
        return false;
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (using && entityOptimize.getValue() && entityLevel.getValue() == 2 && mc.theWorld != null) {
            RenderUtil.enableRenderState();
            for (Object obj : mc.theWorld.loadedEntityList) {
                Entity entity = (Entity) obj;
                if (entity == mc.getRenderViewEntity()) {
                    continue;
                }
                if (entity.isInvisible()) {
                    continue;
                }
                RenderUtil.drawEntityBoundingBox(entity, 255, 255, 255, 255, 1.5F, 0.0F);
            }
            GlStateManager.resetColor();
            RenderUtil.disableRenderState();
        }
    }

    @Override
    public void onEnabled() {
        using = true;
    }

    @Override
    public void onDisabled() {
        using = false;
    }
}
