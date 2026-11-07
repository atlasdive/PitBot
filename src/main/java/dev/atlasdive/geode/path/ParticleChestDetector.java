package dev.atlasdive.geode.path;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** Remembers recent particle block positions and matches them to nearby chest blocks. */
public final class ParticleChestDetector {
    private final Map<BlockPos,Long> lastSeen=new HashMap<BlockPos,Long>();

    public void observe(double x,double y,double z,long tick) {
        if(Double.isNaN(x)||Double.isNaN(y)||Double.isNaN(z)
                ||Double.isInfinite(x)||Double.isInfinite(y)||Double.isInfinite(z))return;
        lastSeen.put(new BlockPos((int)Math.floor(x),(int)Math.floor(y),(int)Math.floor(z)),tick);
    }

    public boolean hasParticleNear(BlockPos chest,long currentTick,int radius,long memoryTicks) {
        if(chest==null||radius<0||memoryTicks<0)return false;
        long radiusSquared=(long)radius*radius;
        for(int dx=-radius;dx<=radius;dx++) {
            for(int dy=-radius;dy<=radius;dy++) {
                for(int dz=-radius;dz<=radius;dz++) {
                    if((long)dx*dx+(long)dy*dy+(long)dz*dz>radiusSquared)continue;
                    Long seen=lastSeen.get(chest.add(dx,dy,dz));
                    if(seen!=null&&currentTick>=seen&&currentTick-seen<=memoryTicks)return true;
                }
            }
        }
        return false;
    }

    public void prune(long currentTick,long memoryTicks) {
        Iterator<Map.Entry<BlockPos,Long>> iterator=lastSeen.entrySet().iterator();
        while(iterator.hasNext()) {
            Map.Entry<BlockPos,Long> entry=iterator.next();
            if(currentTick<entry.getValue()||currentTick-entry.getValue()>memoryTicks)iterator.remove();
        }
    }

    public int getRememberedBlockCount() {
        return lastSeen.size();
    }

    public void clear() {
        lastSeen.clear();
    }
}
