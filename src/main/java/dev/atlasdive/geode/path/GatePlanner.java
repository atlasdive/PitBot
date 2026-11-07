package dev.atlasdive.geode.path;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Finds an open hole in an iron-bar floor and safe standing cells adjacent to it.
 *
 * Detection does NOT require a solid landing directly beneath the hole. The player
 * pathfinds to a supported approach cell, steps into the opening, and the runtime
 * controller confirms the downward crossing. The Sewer floor can be elsewhere.
 */
public final class GatePlanner {
    private int scannedColumns,clearColumns,framedColumns,approachColumns,minPlaneY,maxPlaneY;

    public GatePlan find(PathWorld world,BlockPos marker,BlockPos start,int radius,int maxFall){
        scannedColumns=0;clearColumns=0;framedColumns=0;approachColumns=0;
        if(world==null||marker==null||start==null||radius<0||maxFall<1)return null;

        minPlaneY=marker.y-maxFall;
        maxPlaneY=Math.min(marker.y+2,start.y);
        if(maxPlaneY<minPlaneY)return null;

        // Scan from the player's current level downward. Prefer the first usable
        // grate layer encountered, rather than a deeper opening below it.
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

                // The player's 1.8-block body must fit through the hole and begin
                // descending into the block layer below the iron-bar floor.
                if(!world.clear(aperture.add(0,-1,0))
                        ||!world.clear(aperture)
                        ||!world.clear(aperture.add(0,1,0)))continue;
                clearColumns++;

                int frameScore=countNearbyIronBars(world,x,z,plane);
                // Require a frame on at least three cardinal sides. This prevents
                // nearby bars from making an unrelated diagonal air cell look like a hole.
                if(frameScore<300)continue;
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

            if(!approaches.isEmpty()){
                return new GatePlan(plane,approaches,approachMap,columns);
            }
        }
        return null;
    }

    private int countNearbyIronBars(PathWorld world,int x,int z,int plane){
        int sides=0,total=0;
        for(int[] d:APPROACH_DIRS){
            boolean found=false;
            for(int dy=-1;dy<=1;dy++){
                if(world.ironBars(new BlockPos(x+d[0],plane+dy,z+d[1]))){
                    found=true;
                    total++;
                }
            }
            if(found)sides++;
        }
        // Hundreds encode the number of framed sides; the remainder ranks denser frames.
        return sides*100+total;
    }

    private static final int[][] APPROACH_DIRS={{1,0},{-1,0},{0,1},{0,-1}};

    public int scannedColumns(){return scannedColumns;}
    public int clearColumns(){return clearColumns;}
    public int framedColumns(){return framedColumns;}
    public int approachColumns(){return approachColumns;}
    public int minPlaneY(){return minPlaneY;}
    public int maxPlaneY(){return maxPlaneY;}
}
