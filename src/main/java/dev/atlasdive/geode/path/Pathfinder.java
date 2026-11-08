package dev.atlasdive.geode.path;
import java.util.*;
public final class Pathfinder {
    private static final double EPS=1.0E-8D;
    private static final double CENTER_LANE_PREFERENCE_PENALTY=0.40D;
    public PathResult find(PathWorld world,BlockPos start,List<BlockPos> goals,int maxNodes,int maxFall){
        List<PathNode> nodes=new ArrayList<PathNode>(goals.size());
        for(BlockPos goal:goals)nodes.add(PathNode.fromBlockPos(goal));
        return findNodes(world,PathNode.fromBlockPos(start),nodes,maxNodes,maxFall);
    }
    public PathResult findNodes(PathWorld world,PathNode start,List<PathNode> goals,int maxNodes,int maxFall){
        if(world==null||start==null||goals==null||goals.isEmpty()||!world.canStand(start))
            return new PathResult(Collections.<PathStep>emptyList(),0);
        Set<PathNode> targets=new HashSet<PathNode>(goals);
        GoalIndex goalIndex=new GoalIndex(goals);
        Map<PathNode,Double> best=new HashMap<PathNode,Double>();
        Map<PathNode,Parent> parents=new HashMap<PathNode,Parent>();
        PriorityQueue<Entry> open=new PriorityQueue<Entry>(64,new Comparator<Entry>(){
            public int compare(Entry a,Entry b){return Double.compare(a.f,b.f);}
        });
        best.put(start,0.0D);open.add(new Entry(start,0.0D,goalIndex.distance(start)));
        int expanded=0;
        while(!open.isEmpty()&&expanded<maxNodes){
            if(Thread.currentThread().isInterrupted())
                return new PathResult(Collections.<PathStep>emptyList(),expanded);
            Entry cur=open.poll();Double known=best.get(cur.pos);
            if(known==null||cur.g>known+EPS)continue;
            if(targets.contains(cur.pos))return build(cur.pos,parents,best,expanded,world);
            expanded++;
            for(int lane=0;lane<PathNode.LANE_COUNT;lane++){
                if(lane==cur.pos.lane)continue;
                PathNode shifted=cur.pos.withLane(lane);
                if(world.canStand(shifted)&&world.transitionClear(cur.pos,shifted))
                    relax(shifted,MovementKind.WALK,cur.pos,
                            cur.g+0.85D+centerLanePreferencePenalty(world,shifted),
                            goalIndex,best,parents,open);
            }
            boolean onClimbable=world.climbable(cur.pos);
            int climbDirection=onClimbable?VineTraversal.verticalDirection(cur.pos,goals):0;
            if(onClimbable||world.liquid(cur.pos)){
                MovementKind verticalKind=onClimbable?MovementKind.CLIMB:MovementKind.SWIM;
                int[] verticalOffsets=onClimbable
                        ?(climbDirection>0?new int[]{2,1}:climbDirection<0?new int[]{-2,-1}:new int[0])
                        :new int[]{2,1,-2,-1};
                for(int dyHalf:verticalOffsets){
                    PathNode to=cur.pos.add(0,dyHalf,0);
                    if(world.canStand(to)&&!world.hazard(to)&&(world.climbable(to)||world.liquid(to))){
                        MovementKind kind=world.liquid(to)?MovementKind.SWIM:verticalKind;
                        double climbCost=verticalKind==MovementKind.CLIMB
                                ?0.22D+Math.abs(dyHalf)*0.12D:0.8D;
                        relax(to,kind,cur.pos,cur.g+climbCost+Math.max(0.0D,world.extraCost(to)),
                                goalIndex,best,parents,open);
                    }
                }
            }
            // Generic drop edges used to be almost free, which encouraged repeated
            // one-step descents and climbs. Stay on the vine's vertical route instead.
            if(!onClimbable){
                for(int dyHalf=-1;dyHalf>=-maxFall*2;dyHalf--){
                    if(Thread.currentThread().isInterrupted())
                        return new PathResult(Collections.<PathStep>emptyList(),expanded);
                    PathNode landing=cur.pos.add(0,dyHalf,0);
                    if(!world.canStand(landing)||world.hazard(landing)||!dropArcClear(world,cur.pos,landing,0,0))continue;
                    MovementKind kind=dyHalf< -1&&!world.liquid(landing)?MovementKind.DROP:
                            (world.liquid(cur.pos)||world.liquid(landing))?MovementKind.SWIM:
                            dyHalf==-1?MovementKind.STEP_DOWN:MovementKind.DROP;
                    relax(landing,kind,cur.pos,cur.g+Math.max(0.0D,world.extraCost(landing)),
                            goalIndex,best,parents,open);
                }
            }
            for(int[] d:DIRS){
                if(Thread.currentThread().isInterrupted())
                    return new PathResult(Collections.<PathStep>emptyList(),expanded);
                boolean diagonal=d[0]!=0&&d[1]!=0;
                boolean cornerClear=!diagonal||
                        (world.clear(cur.pos.add(d[0],0,0))&&world.clear(cur.pos.add(0,0,d[1])));
                if(!cornerClear)continue;
                // Nearby stair/slab height changes can use every lane. Long drops are
                // considered separately at whole-block intervals to keep A* bounded.
                for(int dyHalf=2;dyHalf>=-2;dyHalf--){
                    if(Thread.currentThread().isInterrupted())
                        return new PathResult(Collections.<PathStep>emptyList(),expanded);
                    // While attached to a vine, only move vertically toward the nearest goal.
                    if(onClimbable&&(climbDirection==0&&dyHalf!=0
                            ||climbDirection>0&&dyHalf<0
                            ||climbDirection<0&&dyHalf>0))continue;
                    for(int lane=0;lane<PathNode.LANE_COUNT;lane++){
                        PathNode next=new PathNode(cur.pos.x+d[0],cur.pos.y2+dyHalf,cur.pos.z+d[1],lane);
                        addHorizontalTransition(world,cur.pos,next,dyHalf,d[0],d[1],goalIndex,best,parents,open,cur.g);
                    }
                }
                for(int dyHalf=-4;dyHalf>=-maxFall*2;dyHalf-=2){
                    if(Thread.currentThread().isInterrupted())
                        return new PathResult(Collections.<PathStep>emptyList(),expanded);
                    int[] dropLanes=cur.pos.lane==PathNode.CENTER
                            ?new int[]{PathNode.CENTER}
                            :new int[]{cur.pos.lane,PathNode.CENTER};
                    for(int lane:dropLanes){
                        PathNode next=new PathNode(cur.pos.x+d[0],cur.pos.y2+dyHalf,cur.pos.z+d[1],lane);
                        if(!world.canStand(next)||world.hazard(next)||!dropArcClear(world,cur.pos,next,d[0],d[1]))continue;
                        MovementKind kind=world.liquid(next)?MovementKind.SWIM:MovementKind.DROP;
                        double horizontal=Math.sqrt(d[0]*d[0]+d[1]*d[1]);
                        double lanePenalty=lane==cur.pos.lane?0.0D:0.18D;
                        relax(next,kind,cur.pos,cur.g+horizontal+lanePenalty
                                +centerLanePreferencePenalty(world,next)
                                +Math.max(0.0D,world.extraCost(next)),goalIndex,best,parents,open);
                    }
                }
                PathNode jump=new PathNode(cur.pos.x+d[0]*2,cur.pos.y2,cur.pos.z+d[1]*2,cur.pos.lane);
                if(world.canStand(jump)&&!world.hazard(jump)&&world.jumpArcClear(cur.pos,jump)){
                    double cost=2.0D*Math.sqrt(d[0]*d[0]+d[1]*d[1])+2.0D
                            +Math.max(0.0D,world.extraCost(jump));
                    relax(jump,MovementKind.JUMP,cur.pos,cur.g+cost,goalIndex,best,parents,open);
                }
            }
            if(Thread.currentThread().isInterrupted())break;
        }
        return new PathResult(Collections.<PathStep>emptyList(),expanded);
    }
    private static void addHorizontalTransition(PathWorld world,PathNode from,PathNode next,int dyHalf,
            int dx,int dz,GoalIndex goalIndex,Map<PathNode,Double> best,Map<PathNode,Parent> parents,
            PriorityQueue<Entry> open,double base){
        if(!world.canStand(next)||world.hazard(next)||!world.transitionClear(from,next))return;
        MovementKind kind;
        if(dyHalf< -1&&!world.liquid(next))kind=MovementKind.DROP;
        else if(world.liquid(next)||world.liquid(from))kind=MovementKind.SWIM;
        else if(dyHalf>0){
            kind=MovementKind.STEP_UP;
            if(dyHalf>1&&!world.jumpArcClear(from,next))return;
        }else if(dyHalf==-1)kind=MovementKind.STEP_DOWN;
        else if(dyHalf<0){
            kind=MovementKind.DROP;
            if(!dropArcClear(world,from,next,dx,dz))return;
        }else if(world.climbable(next))kind=MovementKind.CLIMB;
        else kind=MovementKind.WALK;
        double horizontal=Math.sqrt(dx*dx+dz*dz);
        double vertical=Math.abs(dyHalf)*0.5D;
        double lanePenalty=next.lane==from.lane?0.0D:0.18D;
        // Prefer the centre of any traversable surface (including half slabs), not
        // just stair transitions. A side lane is penalized only when the centre lane
        // at that exact feet position is also collision-tested as standable.
        double stepCost=horizontal+lanePenalty+centerLanePreferencePenalty(world,next);
        if(kind==MovementKind.STEP_UP)stepCost+=vertical*2.4D;
        else if(kind==MovementKind.SWIM)stepCost+=0.6D;
        else if(kind==MovementKind.CLIMB)stepCost+=0.30D;
        relax(next,kind,from,base+stepCost+Math.max(0.0D,world.extraCost(next)),
                goalIndex,best,parents,open);
    }
    private static double centerLanePreferencePenalty(PathWorld world,PathNode node){
        if(node.lane==PathNode.CENTER)return 0.0D;
        PathNode center=node.withLane(PathNode.CENTER);
        return world.canStand(center)&&world.transitionClear(node,center)
                ?CENTER_LANE_PREFERENCE_PENALTY:0.0D;
    }
    private static void relax(PathNode next,MovementKind kind,PathNode from,double g,GoalIndex goalIndex,
            Map<PathNode,Double> best,Map<PathNode,Parent> parents,PriorityQueue<Entry> open){
        Double old=best.get(next);
        if(old==null||g+EPS<old){
            best.put(next,g);parents.put(next,new Parent(from,kind));
            open.add(new Entry(next,g,goalIndex.distance(next)));
        }
    }
    private static boolean dropArcClear(PathWorld world,PathNode from,PathNode landing,int dx,int dz){
        for(int y2=from.y2-1;y2>landing.y2;y2--){
            PathNode air=new PathNode(from.x+dx,y2,from.z+dz,landing.lane);
            if(!world.clear(air)||world.hazard(air))return false;
        }
        return true;
    }
    private static PathResult build(PathNode end,Map<PathNode,Parent> parents,Map<PathNode,Double> costs,
            int expanded,PathWorld world){
        List<PathStep> raw=new ArrayList<PathStep>();PathNode p=end;
        while(p!=null){
            Parent parent=parents.get(p);
            raw.add(new PathStep(p,parent==null?MovementKind.WALK:parent.kind,
                    costs.containsKey(p)?costs.get(p):0.0D,world.centerOnly(p)));
            p=parent==null?null:parent.pos;
        }
        Collections.reverse(raw);return new PathResult(smooth(raw,world),expanded);
    }
    private static List<PathStep> smooth(List<PathStep> raw,PathWorld world){
        if(raw.size()<3)return raw;
        List<PathStep> out=new ArrayList<PathStep>();int anchor=0;out.add(raw.get(0));
        while(anchor<raw.size()-1){
            if(Thread.currentThread().isInterrupted())return raw;
            int chosen=anchor+1;
            for(int candidate=raw.size()-1;candidate>anchor+1;candidate--){
                if(Thread.currentThread().isInterrupted())return raw;
                boolean normalWalk=true;
                for(int k=anchor+1;k<=candidate;k++){
                    if(Thread.currentThread().isInterrupted())return raw;
                    PathStep step=raw.get(k);
                    if(step.kind!=MovementKind.WALK||step.node.y2!=raw.get(anchor).node.y2
                            ||step.node.lane!=raw.get(anchor).node.lane
                            ||(step.centerOnly&&k<candidate)){normalWalk=false;break;}
                }
                if(normalWalk&&world.lineClear(raw.get(anchor).node,raw.get(candidate).node)){
                    chosen=candidate;break;
                }
            }
            out.add(raw.get(chosen));anchor=chosen;
        }
        return out;
    }
    /**
     * Balanced 2D index for nearest-goal queries. The heuristic deliberately
     * ignores height, keeping it a lower bound while avoiding a full goal scan
     * for every neighbor expanded by A*.
     */
    private static final class GoalIndex {
        private final GoalNode root;
        GoalIndex(List<PathNode> goals){
            List<GoalPoint> points=new ArrayList<GoalPoint>(goals.size());
            for(PathNode goal:goals)points.add(new GoalPoint(goal.worldX(),goal.worldZ()));
            root=build(points,0);
        }
        double distance(PathNode node){
            return Math.sqrt(nearestSquared(root,node.worldX(),node.worldZ(),Double.POSITIVE_INFINITY));
        }
        private static GoalNode build(List<GoalPoint> points,int depth){
            if(points.isEmpty())return null;
            final int axis=depth&1;
            Collections.sort(points,new Comparator<GoalPoint>(){
                public int compare(GoalPoint a,GoalPoint b){
                    int primary=Double.compare(axis==0?a.x:a.z,axis==0?b.x:b.z);
                    return primary!=0?primary:Double.compare(axis==0?a.z:a.x,axis==0?b.z:b.x);
                }
            });
            int middle=points.size()/2;
            GoalPoint point=points.get(middle);
            GoalNode left=build(new ArrayList<GoalPoint>(points.subList(0,middle)),depth+1);
            GoalNode right=build(new ArrayList<GoalPoint>(points.subList(middle+1,points.size())),depth+1);
            return new GoalNode(point,axis,left,right);
        }
        private static double nearestSquared(GoalNode node,double x,double z,double best){
            if(node==null)return best;
            double dx=x-node.point.x,dz=z-node.point.z;
            best=Math.min(best,dx*dx+dz*dz);
            double split=node.axis==0?dx:dz;
            GoalNode near=split<0.0D?node.left:node.right;
            GoalNode far=split<0.0D?node.right:node.left;
            best=nearestSquared(near,x,z,best);
            if(split*split<best)best=nearestSquared(far,x,z,best);
            return best;
        }
    }
    private static final class GoalPoint {
        final double x,z;
        GoalPoint(double x,double z){this.x=x;this.z=z;}
    }
    private static final class GoalNode {
        final GoalPoint point;
        final int axis;
        final GoalNode left,right;
        GoalNode(GoalPoint point,int axis,GoalNode left,GoalNode right){
            this.point=point;this.axis=axis;this.left=left;this.right=right;
        }
    }
    private static final int[][] DIRS={{1,0},{-1,0},{0,1},{0,-1},{1,1},{1,-1},{-1,1},{-1,-1}};
    private static final class Parent{final PathNode pos;final MovementKind kind;Parent(PathNode p,MovementKind k){pos=p;kind=k;}}
    private static final class Entry{final PathNode pos;final double g,f;Entry(PathNode p,double g,double h){pos=p;this.g=g;f=g+h;}}
}