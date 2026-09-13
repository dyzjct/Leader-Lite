package leader.module.modules.render;

import leader.event.EventTarget;
import leader.event.types.EventType;
import leader.events.TickEvent;
import leader.module.Module;
import leader.property.properties.BooleanProperty;
import leader.property.properties.IntProperty;
import leader.property.properties.ModeProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.util.BlockPos;
import net.minecraft.world.biome.BiomeGenBase;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;

public class EnvModifier extends Module {

    private static final Minecraft mc = Minecraft.getMinecraft();

    public final BooleanProperty timeEnabled = new BooleanProperty("Time", false);
    public final ModeProperty timePreset = new ModeProperty("Time Preset", 1,
            new String[]{"Sunrise", "Noon", "Sunset", "Night", "Midnight", "Custom"}, this.timeEnabled::getValue);
    public final IntProperty customTime = new IntProperty("Custom Time", 6000, 0, 23999,
            () -> this.timeEnabled.getValue() && this.timePreset.getValue() == 5);
    public final ModeProperty weather = new ModeProperty("Weather", 0,
            new String[]{"Off", "Clear", "Rain", "Snow", "Thunder"});
    private final Set<BiomeGenBase> snowBiomes = new HashSet<>();
    private static Field snowField;

    private static Field getSnowField() {
        if (snowField == null) {
            try {
                snowField = BiomeGenBase.class.getDeclaredField("enableSnow");
            } catch (NoSuchFieldException ignored) {
                try {
                    snowField = BiomeGenBase.class.getDeclaredField("field_76766_R");
                } catch (NoSuchFieldException ignored2) {
                    return null;
                }
            }
            if (snowField != null) {
                snowField.setAccessible(true);
            }
        }
        return snowField;
    }

    private void setBiomeSnow(BiomeGenBase biome, boolean snow) {
        Field field = getSnowField();
        if (field == null) return;
        try {
            field.setBoolean(biome, snow);
        } catch (IllegalAccessException ignored) {
        }
    }

    public EnvModifier() {
        super("EnvModifier", false);
    }

    private long getTargetTime() {
        return switch (this.timePreset.getValue()) {
            case 0 -> 23000L;
            case 1 -> 6000L;
            case 2 -> 12000L;
            case 3 -> 15000L;
            case 4 -> 18000L;
            default -> (long) this.customTime.getValue();
        };
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE) return;
        if (mc.theWorld == null) return;

        if (this.timeEnabled.getValue()) {
            mc.theWorld.setWorldTime(this.getTargetTime());
        }

        int mode = this.weather.getValue();
        if (mode == 0) {
            this.clearSnow();
            return;
        }

        boolean precipitation = mode != 1;
        boolean snow = mode == 3;
        boolean thunder = mode == 4;
        mc.theWorld.getWorldInfo().setRaining(precipitation);
        mc.theWorld.getWorldInfo().setThundering(thunder);
        mc.theWorld.getWorldInfo().setRainTime(Integer.MAX_VALUE);
        mc.theWorld.getWorldInfo().setThunderTime(Integer.MAX_VALUE);

        float rainStrength = precipitation ? 1.0F : 0.0F;
        float thunderStrength = thunder ? 1.0F : 0.0F;
        if (mc.theWorld.getRainStrength(1.0F) != rainStrength) {
            mc.theWorld.setRainStrength(rainStrength);
        }
        if (mc.theWorld.getThunderStrength(1.0F) != thunderStrength) {
            mc.theWorld.setThunderStrength(thunderStrength);
        }

        if (snow) {
            this.applySnow();
        } else {
            this.clearSnow();
        }
    }

    private void applySnow() {
        if (mc.thePlayer == null) return;
        BiomeGenBase biome = mc.theWorld.getBiomeGenForCoords(
                new BlockPos(mc.thePlayer.posX, 0.0D, mc.thePlayer.posZ));
        if (biome == null || biome.getEnableSnow()) return;
        this.setBiomeSnow(biome, true);
        this.snowBiomes.add(biome);
    }

    private void clearSnow() {
        if (this.snowBiomes.isEmpty()) return;
        for (BiomeGenBase biome : this.snowBiomes) {
            this.setBiomeSnow(biome, false);
        }
        this.snowBiomes.clear();
    }

    @Override
    public void onDisabled() {
        this.clearSnow();
        if (mc.theWorld == null) return;
        mc.theWorld.getWorldInfo().setRainTime(0);
        mc.theWorld.getWorldInfo().setThunderTime(0);
    }

    @Override
    public String[] getSuffix() {
        if (this.weather.getValue() == 1) return new String[]{"Clear"};
        if (this.weather.getValue() == 2) return new String[]{"Rain"};
        if (this.weather.getValue() == 3) return new String[]{"Snow"};
        if (this.weather.getValue() == 4) return new String[]{"Thunder"};
        if (this.timeEnabled.getValue()) return new String[]{this.timePreset.getModeString()};
        return new String[]{"Off"};
    }
}
