package dev.atlasdive.geode.client;

import dev.atlasdive.geode.path.ParticleChestDetector;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.EffectRenderer;
import net.minecraft.client.particle.EntityFX;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import java.lang.reflect.Field;
import java.util.List;

/** Reads the live 1.8.9 client particle lists on the client thread. */
public final class ParticleTracker {
    private static final Field FX_LAYERS=ReflectionHelper.findField(
            EffectRenderer.class,"fxLayers","field_78876_b");

    private ParticleTracker() {}

    @SuppressWarnings("unchecked")
    public static int capture(Minecraft minecraft,ParticleChestDetector detector,long tick,long memoryTicks) {
        if(minecraft==null||minecraft.effectRenderer==null)return -1;
        int count=0;
        try {
            List<EntityFX>[][] layers=(List<EntityFX>[][])FX_LAYERS.get(minecraft.effectRenderer);
            for(List<EntityFX>[] layer:layers) {
                if(layer==null)continue;
                for(List<EntityFX> group:layer) {
                    if(group==null)continue;
                    for(EntityFX particle:group) {
                        if(particle==null||particle.isDead)continue;
                        detector.observe(particle.posX,particle.posY,particle.posZ,tick);
                        count++;
                    }
                }
            }
        } catch(IllegalAccessException e) {
            detector.clear();
            return -1;
        }
        detector.prune(tick,memoryTicks);
        return count;
    }
}
