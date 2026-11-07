package dev.atlasdive.geode.path;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Detects a passable shaft through a nearby iron-bar structure.
 * The marker is approximate: scan downward from the player's current level
 * through the entire configured fall range and rank planes by nearby bar evidence.
 */
public final class GatePlanner {
    private int scannedColumns,clearColumns,framedColumns,safeLandingColumns,minPlaneY,maxPlaneY;

    public GatePlan find(PathWorld world,BlockPos marker,BlockPos start,int radius,int maxFall){
        scannedColumns=0;clearColumns=0;framedColumns=0;safeLandingColumns=0;
        if(world==null||marker==null||start==null||radius<0||maxFall<1)return null;

        minPlaneY=marker.y-maxFall;
        // playerPos() uses floor(feetY), so the same integer Y may still be
        // physically above a partial-block grate.
        maxPlaneY=Math.min(marker.y+2,start.y);
        if(maxPlaneY<minPlaneY)return null;

        int bestScore=-1;
        int bestPlaneY=Integer.MIN_VALUE;
        List<BlockPos> bestLandings=new ArrayList<BlockPos>();
        Set<BlockPos> bestColumns=new HashSet<BlockPos>();

        for(int plane=maxPlaneY;plane>=minPlaneY;plane--){
            for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++){
                if(dx*dx+dz*dz>radius*radius)continue;
                scannedColumns++;
                int x=marker.x+dx,z=marker.z+dz;

                // Need enough collision clearance for the full 1.8-block player body.
                if(!clearPlayerColumn(world,x,z,plane,plane+1))continue;
                clearColumns++;

                int score=countNearbyIronBars(world,x,z,plane);
                if(score<=0)continue;
                framedColumns++;

                BlockPos landing=findLanding(world,x,z,plane,maxFall);
                if(landing==null)continue;
                safeLandingColumns++;

                // Same-plane bars are strongest evidence; staggered bars one block
                // above/below are accepted but have a lower score.
                if(score>bestScore||(score==bestScore&&plane>bestPlaneY)){
                    bestScore=score;
                    bestPlaneY=plane;
                    bestLandings.clear();
                    bestColumns.clear();
                }
                if(score==bestScore&&plane==bestPlaneY){
                    bestLandings.add(landing);
                    bestColumns.add(new BlockPos(x,plane,z));
                }
            }
        }

        return bestLandings.isEmpty()?null:new GatePlan(bestPlaneY,bestLandings,bestColumns);
    }

    private int countNearbyIronBars(PathWorld world,int x,int z,int plane){
        int samePlane=0,staggered=0;
        for(int dy=-1;dy<=1;dy++){
            for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++){
                if(dx==0&&dz==0&&dy==0)continue;
                if(Math.abs(dx)+Math.abs(dz)>3)continue;
                if(!world.ironBars(new BlockPos(x+dx,plane+dy,z+dz)))continue;
                if(dy==0)samePlane++;
                else staggered++;
            }
        }
        return samePlane*10+staggered;
    }

    private BlockPos findLanding(PathWorld world,int x,int z,int plane,int maxFall){
        for(int y=plane-1;y>=plane-maxFall;y--){
            BlockPos candidate=new BlockPos(x,y,z);
            if(world.canStand(candidate)&&clearPlayerColumn(world,x,z,y,plane+1))return candidate;
        }
        return null;
    }

    private boolean clearPlayerColumn(PathWorld world,int x,int z,int fromY,int throughY){
        // throughY is the head block of the last tested player position.
        // Check each occupied block once (the previous version accidentally
        // required three full air blocks above the opening).
        for(int y=fromY;y<=throughY;y++){
            if(!world.clear(new BlockPos(x,y,z)))return false;
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
