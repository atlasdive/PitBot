package dev.atlasdive.geode.client;

import dev.atlasdive.geode.path.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.MathHelper;
import java.util.Collections;
import java.util.List;

/**
 * Stable waypoint follower for vanilla 1.8.9 movement.
 *
 * It stops walking while making large turns, uses generous waypoint capture,
 * and detects lack of progress toward the current target (not merely lack of motion).
 */
public final class MovementController {
    private static final double WAYPOINT_RADIUS=0.58D;
    private static final double DROP_ALIGNMENT=0.16D;
    private static final float TURN_RATE=10.0F;
    private static final float WALK_ALIGNMENT=32.0F;
    private List<PathStep> path=Collections.emptyList();
    private int index,probeTicks,noProgressTicks,trackedIndex=-1;
    private double lastProbeDistance=Double.POSITIVE_INFINITY;
    private boolean stuck;

    public void setPath(List<PathStep> steps,EntityPlayerSP player){
        path=steps==null?Collections.<PathStep>emptyList():steps;
        index=Math.min(1,Math.max(0,path.size()-1));
        probeTicks=0;noProgressTicks=0;trackedIndex=-1;
        lastProbeDistance=Double.POSITIVE_INFINITY;stuck=false;
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

        // Consume already reached nodes before choosing the next movement input.
        while(index<path.size()){
            step=path.get(index);
            dx=step.pos.x+.5D-player.posX;
            dz=step.pos.z+.5D-player.posZ;
            distance=Math.sqrt(dx*dx+dz*dz);
            vertical=step.pos.y-feet;

            if(step.kind==MovementKind.DROP){
                if(distance<=DROP_ALIGNMENT){
                    // Do not keep walking while gravity carries the player through the opening.
                    releaseForwardAndJump();
                    if(feet<=step.pos.y+.45D&&player.onGround){
                        advance();
                        continue;
                    }
                    trackProgress(distance);
                    return;
                }
                break;
            }
            if(step.kind==MovementKind.CLIMB){
                if(distance<=.65D&&Math.abs(vertical)>.45D){
                    setForward(true);
                    setJump(false);
                    setSneak(vertical<-.35D);
                    trackProgress(distance);
                    return;
                }
            }else if(step.kind==MovementKind.SWIM){
                if(distance<=.65D&&Math.abs(vertical)>.45D){
                    setForward(false);
                    setJump(vertical>.35D);
                    setSneak(vertical<-.35D);
                    trackProgress(distance);
                    return;
                }
            }

            if(distance<=WAYPOINT_RADIUS&&Math.abs(vertical)<.75D){
                advance();
                continue;
            }
            break;
        }

        if(index>=path.size()){release();return;}
        step=path.get(index);
        dx=step.pos.x+.5D-player.posX;
        dz=step.pos.z+.5D-player.posZ;
        distance=Math.sqrt(dx*dx+dz*dz);
        vertical=step.pos.y-feet;

        if(distance>.08D){
            float targetYaw=(float)(Math.toDegrees(Math.atan2(dz,dx))-90.0D);
            float difference=MathHelper.wrapAngleTo180_float(targetYaw-player.rotationYaw);
            player.rotationYaw+=MathHelper.clamp_float(difference,-TURN_RATE,TURN_RATE);

            // Turning on the spot prevents the player from orbiting around short waypoints.
            if(Math.abs(difference)>WALK_ALIGNMENT){
                setForward(false);
                setJump(false);
            }else{
                setForward(true);
                boolean jump=(step.kind==MovementKind.STEP_UP||step.kind==MovementKind.JUMP)
                        &&player.onGround&&distance<2.5D;
                setJump(jump);
            }
        }else{
            setForward(false);
            setJump(false);
        }
        setSneak(false);
        trackProgress(distance);
    }

    private void advance(){
        index++;
        probeTicks=0;noProgressTicks=0;trackedIndex=-1;
        lastProbeDistance=Double.POSITIVE_INFINITY;
    }

    private void trackProgress(double distance){
        if(trackedIndex!=index){
            trackedIndex=index;
            lastProbeDistance=distance;
            probeTicks=0;
            noProgressTicks=0;
            return;
        }
        if(++probeTicks<20)return;
        probeTicks=0;
        // Movement can occur in circles. Only reduction in distance to the waypoint counts.
        if(distance>.75D&&lastProbeDistance-distance<.12D)noProgressTicks+=20;
        else noProgressTicks=0;
        lastProbeDistance=distance;
        if(noProgressTicks>=60)stuck=true;
    }

    public void stop(){
        release();path=Collections.emptyList();index=0;stuck=false;
        probeTicks=0;noProgressTicks=0;trackedIndex=-1;
        lastProbeDistance=Double.POSITIVE_INFINITY;
    }
    private void releaseForwardAndJump(){setForward(false);setJump(false);setSneak(false);}
    private void setForward(boolean v){key(Minecraft.getMinecraft().gameSettings.keyBindForward,v);}
    private void setJump(boolean v){key(Minecraft.getMinecraft().gameSettings.keyBindJump,v);}
    private void setSneak(boolean v){key(Minecraft.getMinecraft().gameSettings.keyBindSneak,v);}
    private void key(KeyBinding binding,boolean v){KeyBinding.setKeyBindState(binding.getKeyCode(),v);}
    private void release(){
        Minecraft m=Minecraft.getMinecraft();if(m==null)return;
        key(m.gameSettings.keyBindForward,false);key(m.gameSettings.keyBindBack,false);
        key(m.gameSettings.keyBindLeft,false);key(m.gameSettings.keyBindRight,false);
        key(m.gameSettings.keyBindJump,false);key(m.gameSettings.keyBindSneak,false);
    }
}
