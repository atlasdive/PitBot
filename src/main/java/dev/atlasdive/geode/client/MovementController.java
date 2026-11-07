package dev.atlasdive.geode.client;

import dev.atlasdive.geode.path.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.MathHelper;
import java.util.Collections;
import java.util.List;

/** Drives vanilla movement keys along a computed route. */
public final class MovementController {
    private List<PathStep> path=Collections.emptyList();
    private int index,stillTicks;
    private double lastX,lastY,lastZ;
    private boolean stuck;

    public void setPath(List<PathStep> steps,EntityPlayerSP player) {
        path=steps==null?Collections.<PathStep>emptyList():steps;
        index=Math.min(1,Math.max(0,path.size()-1));
        stillTicks=0; stuck=false;
        if(player!=null){lastX=player.posX;lastY=player.posY;lastZ=player.posZ;}
    }
    public boolean hasPath(){return !path.isEmpty();}
    public boolean complete(){return hasPath()&&index>=path.size();}
    public boolean stuck(){return stuck;}

    public void tick(EntityPlayerSP player) {
        if(player==null||!hasPath()||complete()){release();return;}
        PathStep step=path.get(index);
        double tx=step.pos.x+.5D,tz=step.pos.z+.5D;
        double dx=tx-player.posX,dz=tz-player.posZ;
        double distance=Math.sqrt(dx*dx+dz*dz);
        double feet=player.getEntityBoundingBox().minY;
        if(step.kind==MovementKind.DROP && distance<.35D && feet>step.pos.y+.35D) {
            setForward(false); setJump(false);
            progress(player,distance);
            return; // gravity completes the drop
        }
        if(distance<.24D && Math.abs(step.pos.y-feet)<.4D) {
            index++;
            if(complete()){release();return;}
            step=path.get(index); tx=step.pos.x+.5D; tz=step.pos.z+.5D;
            dx=tx-player.posX; dz=tz-player.posZ; distance=Math.sqrt(dx*dx+dz*dz);
        }
        if(distance>.06D) {
            float yaw=(float)(Math.toDegrees(Math.atan2(dz,dx))-90D);
            float diff=MathHelper.wrapAngleTo180_float(yaw-player.rotationYaw);
            player.rotationYaw+=MathHelper.clamp_float(diff,-14F,14F);
            setForward(true);
        } else setForward(false);
        boolean jump=step.kind==MovementKind.STEP_UP||step.kind==MovementKind.JUMP;
        setJump(jump&&player.onGround&&distance<2.5D);
        progress(player,distance);
    }

    private void progress(EntityPlayerSP p,double distance) {
        double moved=Math.abs(p.posX-lastX)+Math.abs(p.posY-lastY)+Math.abs(p.posZ-lastZ);
        if(distance>.8D&&moved<.025D) stillTicks++; else stillTicks=0;
        if(stillTicks>35) stuck=true;
        if(moved>.025D){lastX=p.posX;lastY=p.posY;lastZ=p.posZ;}
    }
    public void stop(){release();path=Collections.emptyList();index=0;stuck=false;stillTicks=0;}
    private void setForward(boolean v){key(Minecraft.getMinecraft().gameSettings.keyBindForward,v);}
    private void setJump(boolean v){key(Minecraft.getMinecraft().gameSettings.keyBindJump,v);}
    private void key(KeyBinding binding,boolean v){KeyBinding.setKeyBindState(binding.getKeyCode(),v);}
    private void release() {
        Minecraft m=Minecraft.getMinecraft(); if(m==null)return;
        key(m.gameSettings.keyBindForward,false); key(m.gameSettings.keyBindBack,false);
        key(m.gameSettings.keyBindLeft,false); key(m.gameSettings.keyBindRight,false);
        key(m.gameSettings.keyBindJump,false); key(m.gameSettings.keyBindSneak,false);
    }
}
