package dev.atlasdive.geode.path;
import java.util.*;
public final class Pathfinder {
    private static final double EPS=1.0E-8D;
    private static final double EDGE_STAIR_LANE_PENALTY=0.32D;
    public PathResult find(PathWorld world,BlockPos start,List<BlockPos> goals,int maxNodes,int maxFall){
        List<PathNode> nodes=new ArrayList<PathNode>(goals.size());
        for(BlockPos goal:goals)nodes.add(PathNode.fromBlockPos(goal));
        return findNodes(world,PathNode.fromBlockPos(start),nodes,maxNodes,maxFall);
    }
    public PathResult findNodes(PathWorld world,PathNode start,List<PathNode> goals,int maxNodes,int maxFall){
        if(world==null||start==null||goals==null||goals.isEmpty()||!world.canStand(start))
            return new PathResult(Collections.<PathStep>emptyList(),0);
        Set<PathNode> targets=new HashSet<PathNode>(goals);
        Map<PathNode,Double> best=new HashMap<PathNode,Double>();
        Map<PathNode,Parent> parents=new HashMap<PathNode,Parent>();
        PriorityQueue<Entry> open=new PriorityQueue<Entry>(64,new Comparator<Entry>(){
            public int compare(Entry a,Entry b){return Double.compare(a.f,b.f);}
        });
        best.put(start,0.0D);open.add(new Entry(start,0.0D,heuristic(start,goals)));
        int expanded=0;
        while(!open.isEmpty()&&expanded<maxNodes){
            Entry cur=open.poll();Double known=best.get(cur.pos);
            if(known==null||cur.g>known+EPS)continue;
            if(targets.contains(cur.pos))return build(cur.pos,parents,best,expanded,world);
            expanded++;
            for(int lane=0;lane<PathNode.LANE_COUNT;lane++){
                if(lane==cur.pos.lane)continue;
                PathNode shifted=cur.pos.withLane(lane);
                if(world.canStand(shifted)&&world.transitionClear(cur.pos,shifted))
                    relax(shifted,MovementKind.WALK,cur.pos,cur.g+0.22D,goals,best,parents,open);
            }
            if(world.climbable(cur.pos)||world.liquid(cur.pos)){
                MovementKind verticalKind=world.climbable(cur.pos)?MovementKind.CLIMB:MovementKind.SWIM;
                for(int dyHalf:new int[]{2,1,-2,-1}){
                    PathNode to=cur.pos.add(0,dyHalf,0);
                    if(world.canStand(to)&&!world.hazard(to)&&(world.climbable(to)||world.liquid(to))){
                        MovementKind kind=world.liquid(to)?MovementKind.SWIM:verticalKind;
                        double climbCost=verticalKind==MovementKind.CLIMB
                                ?0.22D+Math.abs(dyHalf)*0.12D:0.8D;
                        relax(to,kind,cur.pos,cur.g+climbCost+Math.max(0.0D,world.extraCost(to)),
                                goals,best,parents,open);
                    }
                }
            }
            for(int dyHalf=-1;dyHalf>=-maxFall*2;dyHalf--){
                PathNode landing=cur.pos.add(0,dyHalf,0);
                if(!world.canStand(landing)||world.hazard(landing)||!dropArcClear(world,cur.pos,landing,0,0))continue;
                MovementKind kind=dyHalf< -1&&!world.liquid(landing)?MovementKind.DROP:
                        (world.liquid(cur.pos)||world.liquid(landing))?MovementKind.SWIM:
                        dyHalf==-1?MovementKind.STEP_DOWN:MovementKind.DROP;
                relax(landing,kind,cur.pos,cur.g+Math.max(0.0D,world.extraCost(landing)),
                        goals,best,parents,open);
            }
            for(int[] d:DIRS){
                boolean diagonal=d[0]!=0&&d[1]!=0;
                boolean cornerClear=!diagonal||
                        (world.clear(cur.pos.add(d[0],0,0))&&world.clear(cur.pos.add(0,0,d[1])));
                if(!cornerClear)continue;
                // Nearby stair/slab height changes can use every lane. Long drops are
                // considered separately at whole-block intervals to keep A* bounded.
                for(int dyHalf=2;dyHalf>=-2;dyHalf--){
                    for(int lane=0;lane<PathNode.LANE_COUNT;lane++){
                        PathNode next=new PathNode(cur.pos.x+d[0],cur.pos.y2+dyHalf,cur.pos.z+d[1],lane);
                        addHorizontalTransition(world,cur.pos,next,dyHalf,d[0],d[1],goals,best,parents,open,cur.g);
                    }
                }
                for(int dyHalf=-4;dyHalf>=-maxFall*2;dyHalf-=2){
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
                                +Math.max(0.0D,world.extraCost(next)),goals,best,parents,open);
                    }
                }
                PathNode jump=new PathNode(cur.pos.x+d[0]*2,cur.pos.y2,cur.pos.z+d[1]*2,cur.pos.lane);
                if(world.canStand(jump)&&!world.hazard(jump)&&world.jumpArcClear(cur.pos,jump)){
                    double cost=2.0D*Math.sqrt(d[0]*d[0]+d[1]*d[1])+2.0D
                            +Math.max(0.0D,world.extraCost(jump));
                    relax(jump,MovementKind.JUMP,cur.pos,cur.g+cost,goals,best,parents,open);
                }
            }
            if(Thread.currentThread().isInterrupted())break;
        }
        return new PathResult(Collections.<PathStep>emptyList(),expanded);
    }
    private static void addHorizontalTransition(PathWorld world,PathNode from,PathNode next,int dyHalf,
            int dx,int dz,List<PathNode> goals,Map<PathNode,Double> best,Map<PathNode,Parent> parents,
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
        // On half-block stair steps, prefer the cell centre whenever it is actually
        // standable. Keep the normal collision checks so narrow/blocked stairs still
        // fall back to a supported side lane.
        double stairEdgePenalty=(kind==MovementKind.STEP_UP||kind==MovementKind.STEP_DOWN)
                &&next.lane!=PathNode.CENTER?EDGE_STAIR_LANE_PENALTY:0.0D;
        double stepCost=horizontal+lanePenalty+stairEdgePenalty;
        if(kind==MovementKind.STEP_UP)stepCost+=vertical*2.4D;
        else if(kind==MovementKind.SWIM)stepCost+=0.6D;
        else if(kind==MovementKind.CLIMB)stepCost+=0.30D;
        relax(next,kind,from,base+stepCost+Math.max(0.0D,world.extraCost(next)),
                goals,best,parents,open);
    }
    private static void relax(PathNode next,MovementKind kind,PathNode from,double g,List<PathNode> goals,
            Map<PathNode,Double> best,Map<PathNode,Parent> parents,PriorityQueue<Entry> open){
        Double old=best.get(next);
        if(old==null||g+EPS<old){
            best.put(next,g);parents.put(next,new Parent(from,kind));
            open.add(new Entry(next,g,heuristic(next,goals)));
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
                    costs.containsKey(p)?costs.get(p):0.0D));
            p=parent==null?null:parent.pos;
        }
        Collections.reverse(raw);return new PathResult(smooth(raw,world),expanded);
    }
    private static List<PathStep> smooth(List<PathStep> raw,PathWorld world){
        if(raw.size()<3)return raw;
        List<PathStep> out=new ArrayList<PathStep>();int anchor=0;out.add(raw.get(0));
        while(anchor<raw.size()-1){
            int chosen=anchor+1;
            for(int candidate=raw.size()-1;candidate>anchor+1;candidate--){
                boolean normalWalk=true;
                for(int k=anchor+1;k<=candidate;k++){
                    PathStep step=raw.get(k);
                    if(step.kind!=MovementKind.WALK||step.node.y2!=raw.get(anchor).node.y2
                            ||step.node.lane!=raw.get(anchor).node.lane){normalWalk=false;break;}
                }
                if(normalWalk&&world.lineClear(raw.get(anchor).node,raw.get(candidate).node)){
                    chosen=candidate;break;
                }
            }
            out.add(raw.get(chosen));anchor=chosen;
        }
        return out;
    }
    private static double heuristic(PathNode p,List<PathNode> goals){
        double best=Double.POSITIVE_INFINITY;
        for(PathNode g:goals){
            double dx=g.worldX()-p.worldX(),dz=g.worldZ()-p.worldZ();
            best=Math.min(best,Math.sqrt(dx*dx+dz*dz));
        }
        return best;
    }
    private static final int[][] DIRS={{1,0},{-1,0},{0,1},{0,-1},{1,1},{1,-1},{-1,1},{-1,-1}};
    private static final class Parent{final PathNode pos;final MovementKind kind;Parent(PathNode p,MovementKind k){pos=p;kind=k;}}
    private static final class Entry{final PathNode pos;final double g,f;Entry(PathNode p,double g,double h){pos=p;this.g=g;f=g+h;}}
}