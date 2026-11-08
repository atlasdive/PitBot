package dev.atlasdive.geode.client;

import dev.atlasdive.geode.path.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.MathHelper;
import java.util.Collections;
import java.util.List;

/** Follows collision-tested sub-cell waypoints and handles land, stairs, ladders and water. */
public final class MovementController {
    private static final double WAYPOINT_RADIUS=0.52D;
    private static final double DROP_ALIGNMENT=0.16D;
    private static final float TURN_RATE=8.0F;
    private static final float WALK_ALIGNMENT=58.0F;
    private List<PathStep> path=Collections.emptyList();
    private int index,probeTicks,noProgressTicks,trackedIndex=-1;
    private double lastProbeDistance=Double.POSITIVE_INFINITY;
    private boolean stuck,waitForGroundAtEnd;

    public void setPath(List<PathStep> steps,EntityPlayerSP player){
        setPath(steps,player,false);
    }
    public void setPath(List<PathStep> steps,EntityPlayerSP player,boolean waitForGroundAtEnd){
        path=steps==null?Collections.<PathStep>emptyList():steps;
        index=Math.min(1,Math.max(0,path.size()-1));
        probeTicks=0;noProgressTicks=0;trackedIndex=-1;
        lastProbeDistance=Double.POSITIVE_INFINITY;stuck=false;
        this.waitForGroundAtEnd=waitForGroundAtEnd;
        release();
    }
    public boolean hasPath(){return !path.isEmpty();}
    public boolean complete(){return hasPath()&&index>=path.size();}
    public boolean stuck(){return stuck;}
    public List<PathStep> getPath(){return path;}
    public int getIndex(){return index;}

    public void tick(EntityPlayerSP player){
        if(player==null||!hasPath()||complete()){release();return;}
        if(stuck){release();return;}
        double feet=player.getEntityBoundingBox().minY;
        PathStep step;
        double dx,dz,distance,vertical;
        while(index<path.size()){
            step=path.get(index);
            dx=step.targetX-player.posX;dz=step.targetZ-player.posZ;
            distance=Math.sqrt(dx*dx+dz*dz);vertical=step.feetY-feet;
            double metric=Math.sqrt(distance*distance+vertical*vertical);
            if(step.kind!=MovementKind.DROP&&index>0){
                PathStep previous=path.get(index-1);
                double crossTrackLimit=step.centerOnly?0.16D:0.90D;
                if(WaypointGeometry.hasPassedCheckpoint(previous.targetX,previous.targetZ,
                        step.targetX,step.targetZ,player.posX,player.posZ,crossTrackLimit)){
                    advance();
                    continue;
                }
            }
            if(step.kind==MovementKind.DROP&&distance<=DROP_ALIGNMENT){
                releaseForwardAndJump();
                if(feet<=step.feetY+0.45D&&player.onGround){advance();continue;}
                trackProgress(metric);return;
            }
            if(step.kind==MovementKind.SWIM){
                // SWIM steps include the transition from water onto shore. Advance when
                // this waypoint is reached instead of waiting until the stuck timer fires.
                if(WaypointGeometry.hasReachedWaypoint(distance,vertical,WAYPOINT_RADIUS)){
                    advance();
                    continue;
                }
                steer(player,dx,dz);
                float targetYaw=(float)(Math.toDegrees(Math.atan2(dz,dx))-90.0D);
                boolean aligned=distance<=0.08D
                        ||Math.abs(MathHelper.wrapAngleTo180_float(targetYaw-player.rotationYaw))<=95.0F;
                setForward(distance>0.08D&&aligned);
                setJump(vertical>0.12D);setSneak(vertical< -0.12D);
                setSprint(false);trackProgress(metric);return;
            }
            if(step.kind==MovementKind.CLIMB&&distance<=0.72D&&Math.abs(vertical)>0.25D){
                steerForClimb(player,step.node.lane);
                setForward(true);setJump(false);setSneak(vertical< -0.20D);
                setSprint(false);trackProgress(metric);return;
            }
            if(WaypointGeometry.shouldWaitForGroundAtFinalWaypoint(
                    index==path.size()-1,waitForGroundAtEnd,player.onGround,
                    distance,vertical,WAYPOINT_RADIUS)){
                // A scaffold route must finish on solid ground. Releasing movement here
                // lets the player land before beginScaffold validates the placement base.
                release();trackProgress(metric);return;
            }
            double waypointRadius=step.centerOnly?0.16D:WAYPOINT_RADIUS;
            if(distance<=waypointRadius&&Math.abs(vertical)<0.34D){advance();continue;}
            break;
        }
        if(index>=path.size()){release();return;}
        step=path.get(index);
        dx=step.targetX-player.posX;dz=step.targetZ-player.posZ;
        distance=Math.sqrt(dx*dx+dz*dz);vertical=step.feetY-feet;
        double metric=Math.sqrt(distance*distance+vertical*vertical);
        if(distance>0.06D){
            float targetYaw=(float)(Math.toDegrees(Math.atan2(dz,dx))-90.0D);
            float difference=MathHelper.wrapAngleTo180_float(targetYaw-player.rotationYaw);
            player.rotationYaw+=MathHelper.clamp_float(difference,-TURN_RATE,TURN_RATE);
            if(Math.abs(difference)>WALK_ALIGNMENT){
                setForward(false);setJump(false);setSprint(false);
            }else{
                setForward(true);
                boolean sharpTurn=false;
                if(index>0&&index+1<path.size()){
                    PathStep previous=path.get(index-1),next=path.get(index+1);
                    sharpTurn=WaypointGeometry.isSharpTurn(previous.targetX,previous.targetZ,
                            step.targetX,step.targetZ,next.targetX,next.targetZ,38.0D);
                }
                boolean sprint=step.kind!=MovementKind.CLIMB&&step.kind!=MovementKind.SWIM
                        &&player.onGround&&WaypointGeometry.shouldSprint(distance,sharpTurn);
                setSprint(sprint);
                boolean stepJump=((step.kind==MovementKind.STEP_UP&&vertical>0.65D)
                        ||step.kind==MovementKind.JUMP)&&player.onGround&&distance<2.5D;
                setJump(stepJump);
            }
        }else{setForward(false);setJump(false);setSprint(false);}
        setSneak(false);trackProgress(metric);
    }

    private void steerForClimb(EntityPlayerSP player,int lane){
        float target=VineTraversal.yawForLane(lane,player.rotationYaw);
        float difference=MathHelper.wrapAngleTo180_float(target-player.rotationYaw);
        player.rotationYaw+=MathHelper.clamp_float(difference,-10.0F,10.0F);
        player.rotationYawHead=player.rotationYaw;
    }
    private void steer(EntityPlayerSP player,double dx,double dz){
        if(Math.sqrt(dx*dx+dz*dz)<0.04D)return;
        float targetYaw=(float)(Math.toDegrees(Math.atan2(dz,dx))-90.0D);
        float difference=MathHelper.wrapAngleTo180_float(targetYaw-player.rotationYaw);
        player.rotationYaw+=MathHelper.clamp_float(difference,-TURN_RATE,TURN_RATE);
    }
    private void advance(){
        index++;probeTicks=0;noProgressTicks=0;trackedIndex=-1;
        lastProbeDistance=Double.POSITIVE_INFINITY;
    }
    private void trackProgress(double metric){
        if(trackedIndex!=index){trackedIndex=index;lastProbeDistance=metric;probeTicks=0;noProgressTicks=0;return;}
        if(++probeTicks<20)return;
        probeTicks=0;
        if(metric>0.60D&&lastProbeDistance-metric<0.10D)noProgressTicks+=20;
        else noProgressTicks=0;
        lastProbeDistance=metric;
        if(noProgressTicks>=80)stuck=true;
    }
    public void stop(){
        release();path=Collections.emptyList();index=0;stuck=false;
        probeTicks=0;noProgressTicks=0;trackedIndex=-1;lastProbeDistance=Double.POSITIVE_INFINITY;
        waitForGroundAtEnd=false;
    }
    private void releaseForwardAndJump(){setForward(false);setJump(false);setSneak(false);setSprint(false);}
    private void setForward(boolean v){key(Minecraft.getMinecraft().gameSettings.keyBindForward,v);}
    private void setJump(boolean v){key(Minecraft.getMinecraft().gameSettings.keyBindJump,v);}
    private void setSneak(boolean v){key(Minecraft.getMinecraft().gameSettings.keyBindSneak,v);}
    private void setSprint(boolean v){key(Minecraft.getMinecraft().gameSettings.keyBindSprint,v);}
    private void key(KeyBinding binding,boolean v){KeyBinding.setKeyBindState(binding.getKeyCode(),v);}
    private void release(){
        Minecraft m=Minecraft.getMinecraft();if(m==null)return;
        key(m.gameSettings.keyBindForward,false);key(m.gameSettings.keyBindBack,false);
        key(m.gameSettings.keyBindLeft,false);key(m.gameSettings.keyBindRight,false);
        key(m.gameSettings.keyBindJump,false);key(m.gameSettings.keyBindSneak,false);
        key(m.gameSettings.keyBindSprint,false);
    }
}