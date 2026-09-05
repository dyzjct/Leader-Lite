package leader.ui;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import leader.Leader;
import leader.module.Module;
import leader.ui.components.CategoryComponent;
import leader.ui.components.ModuleComponent;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.input.Mouse;
import java.awt.*;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.List;
public class ClickGui extends GuiScreen {
    private static final int CONFIG_VERSION = 2;
    private static final int PANEL_WIDTH = 132;
    private static final int PANEL_GAP = 6;
    private static final int SCREEN_PADDING = 5;
    private static ClickGui instance;
    private final File configFile = new File("./config/Leader/", "clickgui.txt");
    private final ArrayList<CategoryComponent> categoryList;
    public ClickGui() {
        instance = this;
        Map<String, List<Module>> categoryMap = new LinkedHashMap<>();
        categoryMap.put("Combat", new ArrayList<>());
        categoryMap.put("Movement", new ArrayList<>());
        categoryMap.put("Render", new ArrayList<>());
        categoryMap.put("Player", new ArrayList<>());
        categoryMap.put("Misc", new ArrayList<>());
        categoryMap.put("Legit", new ArrayList<>());
        for (Module module : Leader.moduleManager.modules.values()) {
            String pkg = module.getClass().getPackage().getName().toLowerCase();
            boolean classified = false;
            if (pkg.contains("combat")) {
                categoryMap.get("Combat").add(module);
                classified = true;
            } else if (pkg.contains("movement")) {
                categoryMap.get("Movement").add(module);
                classified = true;
            } else if (pkg.contains("render")) {
                categoryMap.get("Render").add(module);
                classified = true;
            } else if (pkg.contains("player")) {
                categoryMap.get("Player").add(module);
                classified = true;
            } else if (pkg.contains("misc")) {
                categoryMap.get("Misc").add(module);
                classified = true;
            } else if (pkg.contains("legit")) {
                categoryMap.get("Legit").add(module);
                classified = true;
            }
            if (!classified) {
                throw new RuntimeException("Module " + module.getClass().getSimpleName() +
                        " has unknown category. Please move it to a proper category package.");
            }
        }
        Comparator<Module> comparator = Comparator.comparing(m -> m.getName().toLowerCase());
        categoryMap.values().forEach(list -> list.sort(comparator));
        this.categoryList = new ArrayList<>();
        int topOffset = 28;
        int column = 5;
        for (Map.Entry<String, List<Module>> entry : categoryMap.entrySet()) {
            CategoryComponent cat = new CategoryComponent(entry.getKey(), entry.getValue());
            cat.setX(column);
            cat.setY(topOffset);
            categoryList.add(cat);
            column += cat.getWidth() + 6;
        }
        loadPositions();
    }
    public static ClickGui getInstance() {
        return instance;
    }

    public static void setInstance(ClickGui screen) {
        instance = screen;
    }
    private float getUiScale() {
        int contentWidth = categoryList.size() * PANEL_WIDTH
                + Math.max(0, categoryList.size() - 1) * PANEL_GAP
                + SCREEN_PADDING * 2;
        return Math.max(0.45F, Math.min(1.0F, this.width / (float) contentWidth));
    }
    @Override
    public void initGui() {
        super.initGui();
    }
    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawGradientBackground();
        float uiScale = getUiScale();
        GlStateManager.pushMatrix();
        GlStateManager.scale(uiScale, uiScale, 1.0F);
        mouseX = (int)(mouseX / uiScale);
        mouseY = (int)(mouseY / uiScale);
        mc.fontRendererObj.drawString("Leader Lite  |  Click GUI", 8, 8, new Color(235, 238, 245).getRGB(), false);
        mc.fontRendererObj.drawString("Right click a module for settings", 8, 20, new Color(145, 155, 175).getRGB(), false);
        for (CategoryComponent category : categoryList) {
            category.layoutModules();
            category.render(uiScale);
            category.handleDrag(mouseX, mouseY);
            for (Component module : category.getModules()) {
                module.update(mouseX, mouseY);
            }
        }
        // 滚轮处理
        int wheel = Mouse.getDWheel();
        if (wheel != 0) {
            int scrollDir = wheel > 0 ? 1 : -1;
            for (CategoryComponent category : categoryList) {
                category.onScroll(mouseX, mouseY, scrollDir);
            }
        }
        GlStateManager.popMatrix();
    }
    private void drawGradientBackground() {
        ScaledResolution sr = new ScaledResolution(mc);
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.shadeModel(7425);
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer world = tessellator.getWorldRenderer();
        world.begin(7, DefaultVertexFormats.POSITION_COLOR);
        world.pos(0, sr.getScaledHeight(), 0).color(8, 9, 14, 195).endVertex();
        world.pos(sr.getScaledWidth(), sr.getScaledHeight(), 0).color(8, 9, 14, 195).endVertex();
        world.pos(sr.getScaledWidth(), 0, 0).color(16, 19, 28, 170).endVertex();
        world.pos(0, 0, 0).color(16, 19, 28, 170).endVertex();
        tessellator.draw();
        GlStateManager.shadeModel(7424);
        GlStateManager.disableBlend();
        GlStateManager.enableAlpha();
        GlStateManager.enableTexture2D();
    }
    @Override
    public void mouseClicked(int x, int y, int mouseButton) {
        float scale = getUiScale();
        x = (int) (x / scale);
        y = (int) (y / scale);
        for (CategoryComponent category : categoryList) {
            if (category.insideArea(x, y) && !category.isHovered(x, y) && !category.mousePressed(x, y) && mouseButton == 0) {
                category.mousePressed(true);
                category.xx = x - category.getX();
                category.yy = y - category.getY();
            }
            if (category.mousePressed(x, y) && mouseButton == 0) {
                category.setOpened(!category.isOpened());
            }
            if (category.isHovered(x, y) && mouseButton == 0) {
                category.setPin(!category.isPin());
            }
            if (category.isInsideContent(x, y) && !category.getModules().isEmpty()) {
                category.layoutModules();
                for (Component c : category.getModules()) {
                    if (((ModuleComponent) c).contains(x, y)) {
                        c.mouseDown(x, y, mouseButton);
                        break;
                    }
                }
            }
        }
    }
    @Override
    public void mouseReleased(int x, int y, int mouseButton) {
        float scale = getUiScale();
        x = (int) (x / scale);
        y = (int) (y / scale);
        for (CategoryComponent category : categoryList) {
            if (mouseButton == 0) category.mousePressed(false);
            if (category.isOpened() && !category.getModules().isEmpty()) {
                for (Component component : category.getModules()) {
                    component.mouseReleased(x, y, mouseButton);
                }
            }
        }
    }
    @Override
    public void keyTyped(char typedChar, int key) {
        if (key == 1) {
            this.mc.displayGuiScreen(null);
            return;
        }
        for (CategoryComponent cat : categoryList) {
            if (cat.isOpened() && !cat.getModules().isEmpty()) {
                for (Component component : cat.getModules()) {
                    component.keyTyped(typedChar, key);
                }
            }
        }
    }
    @Override
    public void onGuiClosed() {
        savePositions();
    }
    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
    private void savePositions() {
        JsonObject json = new JsonObject();
        json.addProperty("version", CONFIG_VERSION);
        for (CategoryComponent cat : categoryList) {
            JsonObject pos = new JsonObject();
            pos.addProperty("x", cat.getX());
            pos.addProperty("y", cat.getY());
            pos.addProperty("open", cat.isOpened());
            json.add(cat.getName(), pos);
        }
        try (FileWriter writer = new FileWriter(configFile)) {
            new GsonBuilder().setPrettyPrinting().create().toJson(json, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private void loadPositions() {
        if (!configFile.exists()) return;
        try (FileReader reader = new FileReader(configFile)) {
            JsonObject json = new JsonParser().parse(reader).getAsJsonObject();
            if (!json.has("version") || json.get("version").getAsInt() != CONFIG_VERSION) return;
            for (CategoryComponent cat : categoryList) {
                if (json.has(cat.getName())) {
                    JsonObject pos = json.getAsJsonObject(cat.getName());
                    cat.setX(pos.get("x").getAsInt());
                    cat.setY(pos.get("y").getAsInt());
                    cat.setOpened(pos.get("open").getAsBoolean());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
