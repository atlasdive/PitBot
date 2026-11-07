package dev.atlasdive.geode.path;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Finds an opening in a framed floor/railing structure and supported approach cells.
 *
 * Gate geometry is not tied to Blocks.iron_bars: remakes may render the barrier with
 * panes, fences, walls, or solid blocks. The candidate must be a clear player-sized
 * shaft in an otherwise framed layer, with a supported approach next to the opening.
 */
public final class GatePlanner {
    private int scannedColumns,clearColumns,framedColumns,approachColumns,minPlaneY,maxPlaneY;

    public GatePlan find(PathWorld world,BlockPos marker,BlockPos start,int radius,int maxFall){
        scannedColumns=0;clearColumns=0;framedColumns=0;approachColumns=0;
        if(world==null||marker==null||start==null||radius<0||maxFall<1)return null;

        minPlaneY=marker.y-maxFall;
        maxPlaneY=Math.min(marker.y+2,start.y);
        if(maxPlaneY<minPlaneY)return null;

        for(int plane=maxPlaneY;plane>=minPlaneY;plane--){
            int bestFrameScore=-1;
            List<BlockPos> approaches=new ArrayList<BlockPos>();
            Map<BlockPos,BlockPos> approachMap=new HashMap<BlockPos,BlockPos>();
            Set<BlockPos> columns=new HashSet<BlockPos>();

            for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++){
                if(dx*dx+dz*dz>radius*radius)continue;
                scannedColumns++;
                int x=marker.x+dx,z=marker.z+dz;
                BlockPos aperture=new BlockPos(x,plane,z);

                // A player must fit through the empty cell in the floor and retain
                // head clearance while the feet pass below the barrier plane.
                if(!world.clear(aperture.add(0,-1,0))
                        ||!world.clear(aperture)
                        ||!world.clear(aperture.add(0,1,0)))continue;
                clearColumns++;

                int frameScore=countNearbyBarriers(world,x,z,plane);
                // Require a frame on at least two cardinal sides. This allows a
                // corridor-like opening between two rails instead of demanding 3 sides.
                if(frameScore<200)continue;
                framedColumns++;

                List<BlockPos> localApproaches=new ArrayList<BlockPos>();
                for(int[] d:APPROACH_DIRS){
                    BlockPos approach=aperture.add(d[0],0,d[1]);
                    if(!world.canStand(approach)||world.hazard(approach))continue;
                    if(!world.clear(approach.add(0,1,0)))continue;
                    if(!world.lineClear(approach,aperture))continue;
                    localApproaches.add(approach);
                }
                if(localApproaches.isEmpty())continue;
                approachColumns+=localApproaches.size();

                if(frameScore>bestFrameScore){
                    bestFrameScore=frameScore;
                    approaches.clear();
                    approachMap.clear();
                    columns.clear();
                }
                if(frameScore==bestFrameScore){
                    for(BlockPos approach:localApproaches){
                        if(!approachMap.containsKey(approach)){
                            approaches.add(approach);
                            approachMap.put(approach,aperture);
                        }
                    }
                    columns.add(aperture);
                }
            }
            if(!approaches.isEmpty())return new GatePlan(plane,approaches,approachMap,columns);
        }
        return null;
    }

    private int countNearbyBarriers(PathWorld world,int x,int z,int plane){
        int sides=0,floorFrame=0,otherFrame=0;
        for(int[] d:APPROACH_DIRS){
            boolean found=false;
            if(world.gateBarrier(new BlockPos(x+d[0],plane-1,z+d[1]))){
                floorFrame++;
                found=true;
            }
            for(int dy=0;dy<=1&&!found;dy++){
                if(world.gateBarrier(new BlockPos(x+d[0],plane+dy,z+d[1]))){
                    otherFrame++;
                    found=true;
                }
            }
            if(found)sides++;
        }
        // Two opposed floor supports should score higher than incidental overhead walls.
        return sides*100+floorFrame*10+otherFrame;
    }

    private static final int[][] APPROACH_DIRS={{1,0},{-1,0},{0,1},{0,-1}};

    public int scannedColumns(){return scannedColumns;}
    public int clearColumns(){return clearColumns;}
    public int framedColumns(){return framedColumns;}
    public int approachColumns(){return approachColumns;}
    public int minPlaneY(){return minPlaneY;}
    public int maxPlaneY(){return maxPlaneY;}
}
