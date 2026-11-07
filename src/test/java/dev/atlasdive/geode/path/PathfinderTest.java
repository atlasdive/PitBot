package dev.atlasdive.geode.path;

import org.junit.Test;
import java.util.HashSet;
import java.util.Set;
import static org.junit.Assert.*;

public class PathfinderTest {
    @Test public void findsFlatFloorRoute(){
        TestWorld world=new TestWorld();
        PathResult result=new Pathfinder().find(world,new BlockPos(0,1,0),
                java.util.Collections.singletonList(new BlockPos(5,1,4)),10000,8);
        assertTrue(result.found());
        assertEquals(new BlockPos(5,1,4),result.steps.get(result.steps.size()-1).pos);
    }

    @Test public void clearDropHasNoFallDamageSurchargeAndIsNotHiddenByNearbyLedge(){
        TestWorld world=new TestWorld();
        world.support.clear();
        world.support.add(new BlockPos(0,5,0));
        // A nearer ledge exists at y=4, but A* must also consider the lower landing at y=0.
        world.support.add(new BlockPos(1,4,0));
        world.support.add(new BlockPos(1,0,0));
        PathResult result=new Pathfinder().find(world,new BlockPos(0,5,0),
                java.util.Collections.singletonList(new BlockPos(1,0,0)),1000,8);
        assertTrue(result.found());
        assertEquals(2,result.steps.size());
        assertEquals(MovementKind.DROP,result.steps.get(1).kind);
        assertEquals(new BlockPos(1,0,0),result.steps.get(1).pos);
        assertTrue("drop cost should only be horizontal distance",result.steps.get(1).cost<1.1D);
    }

    @Test public void smoothsStraightWalkingSegments(){
        TestWorld world=new TestWorld();
        PathResult result=new Pathfinder().find(world,new BlockPos(0,1,0),
                java.util.Collections.singletonList(new BlockPos(8,1,0)),10000,8);
        assertTrue(result.found());
        assertTrue("straight path should be reduced to start and end",result.steps.size()<=2);
        assertEquals(new BlockPos(8,1,0),result.steps.get(result.steps.size()-1).pos);
    }

    @Test public void gatePlannerFindsAFramedOpeningAndPathCrossesIt(){
        TestWorld world=new TestWorld();
        world.support.clear();
        world.support.add(new BlockPos(0,0,0));
        world.support.add(new BlockPos(1,6,0));
        world.iron.add(new BlockPos(-1,5,0));
        world.iron.add(new BlockPos(1,5,0));
        world.iron.add(new BlockPos(0,5,-1));
        world.iron.add(new BlockPos(0,5,1));

        GatePlan gate=new GatePlanner().find(world,new BlockPos(0,5,0),new BlockPos(1,6,0),1,8);
        assertNotNull("must detect a real iron-bar frame",gate);
        assertEquals(5,gate.planeY);
        assertTrue(gate.columns.contains(new BlockPos(0,5,0)));
        assertEquals(new BlockPos(0,0,0),gate.landings.get(0));

        PathResult path=new Pathfinder().find(world,new BlockPos(1,6,0),gate.landings,1000,8);
        assertTrue(path.found());
        boolean crossed=false;
        for(int i=1;i<path.steps.size();i++){
            PathStep from=path.steps.get(i-1),to=path.steps.get(i);
            if(to.kind==MovementKind.DROP&&from.pos.y>gate.planeY&&to.pos.y<gate.planeY
                    &&gate.columns.contains(new BlockPos(to.pos.x,gate.planeY,to.pos.z)))crossed=true;
        }
        assertTrue("path must contain a falling step through the detected aperture",crossed);
    }

    @Test public void gatePlannerFindsGrateBelowApproximateMarker(){
        TestWorld world=new TestWorld();
        world.support.clear();
        world.support.add(new BlockPos(0,0,0));
        world.support.add(new BlockPos(1,6,0));
        // The approximate marker is four blocks above the actual grate plane.
        world.iron.add(new BlockPos(-1,4,0));
        world.iron.add(new BlockPos(1,4,0));
        world.iron.add(new BlockPos(0,4,-1));
        world.iron.add(new BlockPos(0,4,1));

        GatePlan gate=new GatePlanner().find(world,new BlockPos(0,8,0),new BlockPos(1,6,0),1,8);
        assertNotNull(gate);
        assertEquals("planner should choose the real grate plane, not the marker height",4,gate.planeY);
        assertTrue(gate.columns.contains(new BlockPos(0,4,0)));
    }

    @Test public void gatePlannerRejectsAnUnframedOrBlockedColumn(){
        TestWorld world=new TestWorld();
        world.support.clear();
        world.support.add(new BlockPos(0,0,0));
        GatePlan noFrame=new GatePlanner().find(world,new BlockPos(0,5,0),new BlockPos(1,6,0),1,8);
        assertNull(noFrame);

        world.iron.add(new BlockPos(-1,5,0));
        world.iron.add(new BlockPos(1,5,0));
        world.iron.add(new BlockPos(0,5,-1));
        world.iron.add(new BlockPos(0,5,1));
        world.blocked.add(new BlockPos(0,5,0));
        GatePlan blocked=new GatePlanner().find(world,new BlockPos(0,5,0),new BlockPos(1,6,0),1,8);
        assertNull(blocked);
    }

    @Test public void supportsStraightDownDropThroughOneBlockOpening(){
        TestWorld world=new TestWorld();
        world.support.clear();
        world.support.add(new BlockPos(0,5,0));
        world.support.add(new BlockPos(0,0,0));
        PathResult result=new Pathfinder().find(world,new BlockPos(0,5,0),
                java.util.Collections.singletonList(new BlockPos(0,0,0)),1000,8);
        assertTrue(result.found());
        assertEquals(2,result.steps.size());
        assertEquals(MovementKind.DROP,result.steps.get(1).kind);
        assertEquals(0.0D,result.steps.get(1).cost,0.0001D);
    }

    private static final class TestWorld implements PathWorld {
        final Set<BlockPos> support=new HashSet<BlockPos>();
        final Set<BlockPos> iron=new HashSet<BlockPos>();
        final Set<BlockPos> blocked=new HashSet<BlockPos>();
        TestWorld(){for(int x=-2;x<=10;x++)for(int z=-2;z<=10;z++)support.add(new BlockPos(x,1,z));}
        public boolean canStand(BlockPos p){return p.y>=0&&p.y<8&&support.contains(p)&&!blocked.contains(p);}
        public boolean clear(BlockPos p){return p.y>=0&&p.y<10&&!blocked.contains(p)&&!iron.contains(p);}
        public boolean hazard(BlockPos p){return false;}
        public boolean climbable(BlockPos p){return false;}
        public boolean liquid(BlockPos p){return false;}
        public boolean ironBars(BlockPos p){return iron.contains(p);}
        public double extraCost(BlockPos p){return 0D;}
        public boolean jumpArcClear(BlockPos a,BlockPos b){return true;}
        public boolean lineClear(BlockPos a,BlockPos b){return a.y==b.y&&clear(a)&&clear(b);}
    }
}
