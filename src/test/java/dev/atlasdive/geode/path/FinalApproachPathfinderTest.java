package dev.atlasdive.geode.path;

import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public class FinalApproachPathfinderTest {
    @Test public void choosesWaypointByTravelCostPlusDistanceToFinalTarget(){
        PathWorld world=new OpenGridWorld();
        PathNode start=new PathNode(0,2,0);
        BlockPos nearStartButOffRoute=new BlockPos(1,1,3);
        BlockPos onTheWayToFinal=new BlockPos(6,1,0);
        PathResult result=new Pathfinder().findNodesToward(world,start,
                Arrays.asList(PathNode.fromBlockPos(nearStartButOffRoute),
                        PathNode.fromBlockPos(onTheWayToFinal)),
                new BlockPos(10,58,0),10000,8);
        assertTrue(result.found());
        assertEquals("The route-aware score should prefer the point that advances toward the final target",
                onTheWayToFinal,result.steps.get(result.steps.size()-1).pos);
    }

    private static final class OpenGridWorld implements PathWorld {
        public boolean canStand(BlockPos pos){return pos.y==1;}
        public boolean clear(BlockPos pos){return true;}
        public boolean hazard(BlockPos pos){return false;}
        public boolean climbable(BlockPos pos){return false;}
        public boolean liquid(BlockPos pos){return false;}
        public boolean gateBarrier(BlockPos pos){return false;}
        public double extraCost(BlockPos pos){return 0.0D;}
        public boolean jumpArcClear(BlockPos from,BlockPos to){return true;}
        public boolean lineClear(BlockPos from,BlockPos to){return true;}
        public boolean canStand(PathNode pos){
            return pos.lane==PathNode.CENTER&&pos.y2==2&&canStand(pos.blockPos());
        }
        public boolean clear(PathNode pos){return pos.lane==PathNode.CENTER;}
        public boolean transitionClear(PathNode from,PathNode to){return true;}
        public boolean lineClear(PathNode from,PathNode to){return from.y2==to.y2;}
        public boolean jumpArcClear(PathNode from,PathNode to){return true;}
    }
}
