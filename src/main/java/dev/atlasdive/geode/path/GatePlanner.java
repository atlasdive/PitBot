package dev.atlasdive.geode.path;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Detects an actual passable shaft through a nearby iron-bar layer.
 *
 * Coordinates supplied by the user are only a search marker. The grate may be
 * several blocks below that marker, so the search covers the full configured
 * vertical drop range and prefers the first usable grate below the player.
 */
public final class GatePlanner {
    private int scannedColumns;
    private int clearColumns;
    private int framedColumns;
    private int safeLandingColumns;
    private int minPlaneY;
    private int maxPlaneY;

    public GatePlan find(PathWorld world,BlockPos marker,BlockPos start,int radius,int maxFall){
        scannedColumns=0;clearColumns=0;framedColumns=0;safeLandingColumns=0;
        if(world==null||marker==null||start==null||radius<0||maxFall<1)return null;

        // A route must start above the grate. Do not select a grate plane the
        // player has already fallen below.
        minPlaneY=marker.y-maxFall;
        maxPlaneY=Math.min(marker.y+2,start.y-1);
        if(maxPlaneY<minPlaneY)return null;

        for(int plane=maxPlaneY;plane>=minPlaneY;plane--){
            int bestFrame=-1;
            List<BlockPos> landings=new ArrayList<BlockPos>();
            Set<BlockPos> columns=new HashSet<BlockPos>();

            for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++){
                if(dx*dx+dz*dz>radius*radius)continue;
                scannedColumns++;
                int x=marker.x+dx,z=marker.z+dz;

                // Only require enough clearance for the 1.8-block player body.
                if(!clearPlayerColumn(world,x,z,plane,plane+1))continue;
                clearColumns++;

                // Bars can be staggered vertically or sit two blocks from the gap.
                int frame=countNearbyIronBars(world,x,z,plane);
                if(frame<1)continue;
                framedColumns++;

                BlockPos landing=findLanding(world,x,z,plane,maxFall);
                if(landing==null)continue;
                safeLandingColumns++;

                if(frame>bestFrame){
                    bestFrame=frame;
                    landings.clear();
                    columns.clear();
                }
                if(frame==bestFrame){
                    landings.add(landing);
                    columns.add(new BlockPos(x,plane,z));
                }
            }

            // Highest usable grate below the player is the next barrier the bot
            // must pass, rather than a deeper grate that might be below it.
            if(!landings.isEmpty()){
                return new GatePlan(plane,landings,columns);
            }
        }
        return null;
    }

    private int countNearbyIronBars(PathWorld world,int x,int z,int plane){
        int count=0;
        for(int dy=-1;dy<=1;dy++){
            for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++){
                if(dx==0&&dz==0&&dy==0)continue;
                if(Math.abs(dx)+Math.abs(dz)>3)continue;
                if(world.ironBars(new BlockPos(x+dx,plane+dy,z+dz)))count++;
            }
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

    public int scannedColumns(){return scannedColumns;}
    public int clearColumns(){return clearColumns;}
    public int framedColumns(){return framedColumns;}
    public int safeLandingColumns(){return safeLandingColumns;}
    public int minPlaneY(){return minPlaneY;}
    public int maxPlaneY(){return maxPlaneY;}
}
