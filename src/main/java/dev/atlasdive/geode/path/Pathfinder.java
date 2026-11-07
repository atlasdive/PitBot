package dev.atlasdive.geode.path;

import java.util.*;

public final class Pathfinder {
    private static final double EPS=1.0E-8D;

    public PathResult find(PathWorld world,BlockPos start,List<BlockPos> goals,int maxNodes,int maxFall){
        if(world==null||start==null||goals==null||goals.isEmpty()||!world.canStand(start))
            return new PathResult(Collections.<PathStep>emptyList(),0);
        Set<BlockPos> targets=new HashSet<BlockPos>(goals);
        Map<BlockPos,Double> best=new HashMap<BlockPos,Double>();
        Map<BlockPos,Parent> parents=new HashMap<BlockPos,Parent>();
        PriorityQueue<Entry> open=new PriorityQueue<Entry>(64,new Comparator<Entry>(){
            public int compare(Entry a,Entry b){return Double.compare(a.f,b.f);}
        });
        best.put(start,0.0D);open.add(new Entry(start,0.0D,heuristic(start,goals)));
        int expanded=0;
        while(!open.isEmpty()&&expanded<maxNodes){
            Entry cur=open.poll();Double known=best.get(cur.pos);
            if(known==null||cur.g>known+EPS)continue;
            if(targets.contains(cur.pos))return build(cur.pos,parents,best,expanded);
            expanded++;

            if(world.climbable(cur.pos)||world.liquid(cur.pos)){
                MovementKind verticalKind=world.climbable(cur.pos)?MovementKind.CLIMB:MovementKind.SWIM;
                addVertical(world,cur.pos,1,verticalKind,cur.g,goals,best,parents,open);
                addVertical(world,cur.pos,-1,verticalKind,cur.g,goals,best,parents,open);
            }

            for(int[] d:DIRS){
                BlockPos next=null;MovementKind kind=MovementKind.WALK;
                for(int dy=1;dy>=-maxFall;dy--){
                    BlockPos p=cur.pos.add(d[0],dy,d[1]);
                    if(!world.canStand(p)||world.hazard(p))continue;
                    if(d[0]!=0&&d[1]!=0&&(!world.clear(cur.pos.add(d[0],0,0))||!world.clear(cur.pos.add(0,0,d[1]))))continue;
                    if(dy>0){kind=MovementKind.STEP_UP;if(!world.jumpArcClear(cur.pos,p))continue;}
                    else if(dy<0){kind=MovementKind.DROP;if(!dropArcClear(world,cur.pos,p,d[0],d[1]))continue;}
                    else if(world.liquid(p))kind=MovementKind.SWIM;
                    else if(world.climbable(p))kind=MovementKind.CLIMB;
                    next=p;break;
                }
                if(next==null){
                    BlockPos jump=cur.pos.add(d[0]*2,0,d[1]*2);
                    if(world.canStand(jump)&&!world.hazard(jump)&&world.jumpArcClear(cur.pos,jump)){next=jump;kind=MovementKind.JUMP;}
                }
                if(next==null)continue;
                double horizontal=Math.sqrt(d[0]*d[0]+d[1]*d[1])*(kind==MovementKind.JUMP?2.0D:1.0D);
                double vertical=Math.abs(next.y-cur.pos.y);
                double stepCost=horizontal;
                if(kind==MovementKind.DROP)stepCost=horizontal; // Falls are not penalized on this server.
                else if(kind==MovementKind.STEP_UP)stepCost+=vertical*2.4D;
                else if(kind==MovementKind.JUMP)stepCost+=2.0D;
                else if(kind==MovementKind.SWIM)stepCost+=2.0D;
                else if(kind==MovementKind.CLIMB)stepCost+=1.2D;
                relax(next,kind,cur.pos,cur.g+stepCost+Math.max(0.0D,world.extraCost(next)),goals,best,parents,open);
            }
            if(Thread.currentThread().isInterrupted())break;
        }
        return new PathResult(Collections.<PathStep>emptyList(),expanded);
    }

    private static void addVertical(PathWorld world,BlockPos from,int dy,MovementKind kind,double base,
                                    List<BlockPos> goals,Map<BlockPos,Double> best,Map<BlockPos,Parent> parents,
                                    PriorityQueue<Entry> open){
        BlockPos to=from.add(0,dy,0);
        if(world.canStand(to)&&!world.hazard(to)&&(world.climbable(to)||world.liquid(to)))
            relax(to,kind,from,base+1.2D+Math.max(0.0D,world.extraCost(to)),goals,best,parents,open);
    }

    private static void relax(BlockPos next,MovementKind kind,BlockPos from,double g,List<BlockPos> goals,
                              Map<BlockPos,Double> best,Map<BlockPos,Parent> parents,PriorityQueue<Entry> open){
        Double old=best.get(next);
        if(old==null||g+EPS<old){
            best.put(next,g);parents.put(next,new Parent(from,kind));
            open.add(new Entry(next,g,heuristic(next,goals)));
        }
    }

    private static boolean dropArcClear(PathWorld world,BlockPos from,BlockPos landing,int dx,int dz){
        int drop=from.y-landing.y;
        for(int y=1;y<drop;y++){
            BlockPos air=new BlockPos(from.x+dx,from.y-y,from.z+dz);
            if(!world.clear(air)||world.hazard(air))return false;
        }
        return true;
    }

    private static PathResult build(BlockPos end,Map<BlockPos,Parent> parents,Map<BlockPos,Double> costs,int expanded){
        List<PathStep> out=new ArrayList<PathStep>();BlockPos p=end;
        while(p!=null){
            Parent parent=parents.get(p);
            out.add(new PathStep(p,parent==null?MovementKind.WALK:parent.kind,costs.containsKey(p)?costs.get(p):0.0D));
            p=parent==null?null:parent.pos;
        }
        Collections.reverse(out);return new PathResult(out,expanded);
    }
    private static double heuristic(BlockPos p,List<BlockPos> goals){
        double best=Double.POSITIVE_INFINITY;
        for(BlockPos g:goals){double dx=g.x-p.x,dz=g.z-p.z;best=Math.min(best,Math.sqrt(dx*dx+dz*dz));}
        return best;
    }
    private static final int[][] DIRS={{1,0},{-1,0},{0,1},{0,-1},{1,1},{1,-1},{-1,1},{-1,-1}};
    private static final class Parent{final BlockPos pos;final MovementKind kind;Parent(BlockPos p,MovementKind k){pos=p;kind=k;}}
    private static final class Entry{final BlockPos pos;final double g,f;Entry(BlockPos p,double g,double h){pos=p;this.g=g;f=g+h;}}
}
