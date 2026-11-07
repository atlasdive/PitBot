package dev.atlasdive.geode.path;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Finds a real opening in an iron-bar layer and a player-sized, clear drop shaft
 * from above the grate to a safe supported landing below it.
 */
public final class GatePlanner {
    public GatePlan find(PathWorld world,BlockPos marker,int radius,int planeSearch,int maxFall){
        if(world==null||marker==null||radius<0||planeSearch<0||maxFall<1)return null;
        int bestFrame=-1;
        int bestPlaneDelta=Integer.MAX_VALUE;
        int bestPlaneY=marker.y;
        List<BlockPos> landings=new ArrayList<BlockPos>();
        Set<BlockPos> columns=new HashSet<BlockPos>();

        for(int plane=marker.y-planeSearch;plane<=marker.y+planeSearch;plane++){
            for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++){
                if(dx*dx+dz*dz>radius*radius)continue;
                int x=marker.x+dx,z=marker.z+dz;
                if(!clearPlayerColumn(world,x,z,plane,plane+2))continue;

                int frame=countIronBars(world,x,z,plane);
                if(frame<2)continue;

                BlockPos landing=findLanding(world,x,z,plane,maxFall);
                if(landing==null)continue;

                int delta=Math.abs(plane-marker.y);
                if(frame>bestFrame||(frame==bestFrame&&delta<bestPlaneDelta)){
                    bestFrame=frame;
                    bestPlaneDelta=delta;
                    bestPlaneY=plane;
                    landings.clear();
                    columns.clear();
                }
                if(frame==bestFrame&&delta==bestPlaneDelta&&plane==bestPlaneY){
                    landings.add(landing);
                    columns.add(new BlockPos(x,plane,z));
                }
            }
        }
        return landings.isEmpty()?null:new GatePlan(bestPlaneY,landings,columns);
    }

    private int countIronBars(PathWorld world,int x,int z,int plane){
        int count=0;
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){
            if(dx==0&&dz==0)continue;
            if(world.ironBars(new BlockPos(x+dx,plane,z+dz)))count++;
        }
        return count;
    }

    private BlockPos findLanding(PathWorld world,int x,int z,int plane,int maxFall){
        for(int y=plane-1;y>=plane-maxFall;y--){
            BlockPos candidate=new BlockPos(x,y,z);
            if(world.canStand(candidate)&&clearPlayerColumn(world,x,z,y,plane+1))return candidate;
        }
        return null;
    }

    private boolean clearPlayerColumn(PathWorld world,int x,int z,int fromY,int throughY){
        for(int y=fromY;y<=throughY;y++){
            BlockPos feet=new BlockPos(x,y,z);
            if(!world.clear(feet)||!world.clear(feet.add(0,1,0)))return false;
        }
        return true;
    }
}
