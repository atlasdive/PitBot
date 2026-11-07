package dev.atlasdive.geode.path;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLadder;
import net.minecraft.block.BlockVine;
import net.minecraft.block.BlockPane;
import net.minecraft.block.BlockFence;
import net.minecraft.block.BlockWall;
import net.minecraft.block.material.Material;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.init.Blocks;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.World;
import java.util.ArrayList;
import java.util.List;

/** Immutable collision snapshot with lane-specific support and clearance masks. */
public final class MinecraftPathWorld implements PathWorld {
    private static final byte HAZARD=1,CLIMB=2,LIQUID=4,GATE_BARRIER=8;
    private static final double[] OFFSET_X={0.0D,-0.35D,0.35D,0.0D,0.0D};
    private static final double[] OFFSET_Z={0.0D,0.0D,0.0D,-0.35D,0.35D};
    private final int minX,minY,minZ,w,h,d,halfH;
    private final byte[] flags,clearMasks,standMasks;
    private final float[] costs;

    private MinecraftPathWorld(int x,int y,int z,int w,int h,int d){
        minX=x;minY=y;minZ=z;this.w=w;this.h=h;this.d=d;halfH=h*2+1;
        int count=w*halfH*d;
        flags=new byte[count];clearMasks=new byte[count];standMasks=new byte[count];costs=new float[count];
    }
    public static MinecraftPathWorld capture(World world,EntityPlayerSP player,BlockPos start,List<BlockPos> goals){
        return capture(world,player,PathNode.fromBlockPos(start),toNodes(goals));
    }
    public static MinecraftPathWorld capture(World world,EntityPlayerSP player,PathNode start,List<PathNode> goals){
        int ax=start.x,bx=start.x,ay=(int)Math.floor(start.feetY()),by=ay,az=start.z,bz=start.z;
        for(PathNode node:goals){
            ax=Math.min(ax,node.x);bx=Math.max(bx,node.x);
            int nodeY=(int)Math.floor(node.feetY());ay=Math.min(ay,nodeY);by=Math.max(by,nodeY);
            az=Math.min(az,node.z);bz=Math.max(bz,node.z);
        }
        final int horizontalPadding=24;
        ax=Math.max(-29999990,ax-horizontalPadding);bx=Math.min(29999990,bx+horizontalPadding);
        az=Math.max(-29999990,az-horizontalPadding);bz=Math.min(29999990,bz+horizontalPadding);
        ay=Math.max(1,ay-12);by=Math.min(254,by+12);
        int w=Math.min(128,bx-ax+1),d=Math.min(128,bz-az+1),h=Math.min(96,by-ay+1);
        MinecraftPathWorld out=new MinecraftPathWorld(ax,ay,az,w,h,d);
        int minY2=ay*2,maxY2=minY2+out.halfH-1;
        byte[] blocked=new byte[w*out.halfH*d],supported=new byte[w*out.halfH*d];
        AxisAlignedBB region=new AxisAlignedBB(ax,ay,az,ax+w,ay+h,az+d);
        List<AxisAlignedBB> collisions=world.getCollidingBoundingBoxes(player,region);
        for(AxisAlignedBB box:collisions){
            int x0=Math.max(ax,(int)Math.floor(box.minX-1.15D));
            int x1=Math.min(ax+w-1,(int)Math.ceil(box.maxX+0.65D));
            int z0=Math.max(az,(int)Math.floor(box.minZ-1.15D));
            int z1=Math.min(az+d-1,(int)Math.ceil(box.maxZ+0.65D));
            int y0=Math.max(minY2,(int)Math.floor((box.minY-1.80D)*2.0D)+1);
            int y1=Math.min(maxY2,(int)Math.ceil(box.maxY*2.0D)-1);
            for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++){
                for(int y2=y0;y2<=y1;y2++){
                    double feetY=y2*0.5D;int i=out.index(x,y2,z),mask=blocked[i]&0xFF;
                    for(int lane=0;lane<PathNode.LANE_COUNT;lane++){
                        double cx=x+0.5D+OFFSET_X[lane],cz=z+0.5D+OFFSET_Z[lane];
                        AxisAlignedBB playerBox=new AxisAlignedBB(cx-0.30D,feetY,cz-0.30D,
                                cx+0.30D,feetY+1.80D,cz+0.30D);
                        if(playerBox.intersectsWith(box))mask|=1<<lane;
                    }
                    blocked[i]=(byte)mask;
                }
            }
            int feetY2=(int)Math.round(box.maxY*2.0D);
            if(Math.abs(box.maxY-feetY2*0.5D)<0.035D&&feetY2>=minY2&&feetY2<=maxY2){
                for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++){
                    int i=out.index(x,feetY2,z),mask=supported[i]&0xFF;
                    for(int lane=0;lane<PathNode.LANE_COUNT;lane++){
                        double cx=x+0.5D+OFFSET_X[lane],cz=z+0.5D+OFFSET_Z[lane];
                        double overlapX=Math.min(box.maxX,cx+0.30D)-Math.max(box.minX,cx-0.30D);
                        double overlapZ=Math.min(box.maxZ,cz+0.30D)-Math.max(box.minZ,cz-0.30D);
                        if(overlapX>0.08D&&overlapZ>0.08D)mask|=1<<lane;
                    }
                    supported[i]=(byte)mask;
                }
            }
        }
        for(int x=ax;x<ax+w;x++)for(int y2=minY2;y2<=maxY2;y2++)for(int z=az;z<az+d;z++){
            double feetY=y2*0.5D;int blockY=(int)Math.floor(feetY);
            net.minecraft.util.BlockPos p=new net.minecraft.util.BlockPos(x,blockY,z);
            if(!world.isBlockLoaded(p,false))continue;
            Block b=world.getBlockState(p).getBlock();Material m=b.getMaterial();
            boolean liquid=m==Material.water;
            boolean hazard=m==Material.lava||b==Blocks.lava||b==Blocks.flowing_lava
                    ||b==Blocks.fire||b==Blocks.cactus;
            boolean climb=b instanceof BlockLadder||b instanceof BlockVine;
            boolean gateBarrier=b==Blocks.iron_bars||b instanceof BlockPane||b instanceof BlockFence
                    ||b instanceof BlockWall||b.isOpaqueCube();
            int i=out.index(x,y2,z);
            int clearMask=(~(blocked[i]&0xFF))&((1<<PathNode.LANE_COUNT)-1);
            int standMask=clearMask&(supported[i]&0xFF);
            if((y2&1)==0){
                net.minecraft.util.BlockPos below=p.down();
                if(world.isBlockLoaded(below,false)&&world.isSideSolid(below,EnumFacing.UP,false))
                    standMask|=clearMask;
            }
            out.clearMasks[i]=(byte)clearMask;out.standMasks[i]=(byte)standMask;
            byte f=0;if(hazard)f|=HAZARD;if(climb)f|=CLIMB;if(liquid)f|=LIQUID;if(gateBarrier)f|=GATE_BARRIER;
            out.flags[i]=f;
            float c=0F;if(b==Blocks.water||b==Blocks.flowing_water)c+=1.0F;
            if(b==Blocks.web)c+=8F;if(b==Blocks.soul_sand)c+=1F;out.costs[i]=c;
        }
        return out;
    }
    private static List<PathNode> toNodes(List<BlockPos> goals){
        List<PathNode> nodes=new ArrayList<PathNode>(goals.size());
        for(BlockPos goal:goals)nodes.add(PathNode.fromBlockPos(goal));
        return nodes;
    }
    private int index(int x,int y2,int z){return ((x-minX)*halfH+(y2-minY*2))*d+(z-minZ);}
    private int idx(PathNode p){
        if(p.x<minX||p.y2<minY*2||p.z<minZ||p.x>=minX+w||p.y2>=minY*2+halfH||p.z>=minZ+d)return -1;
        return index(p.x,p.y2,p.z);
    }
    private byte flags(PathNode p){int i=idx(p);return i<0?0:flags[i];}
    public boolean canStand(BlockPos p){return canStand(PathNode.fromBlockPos(p));}
    public boolean canStand(PathNode p){
        int i=idx(p);if(i<0)return false;
        byte f=flags[i];int bit=1<<p.lane;
        if((clearMasks[i]&bit)==0||(f&HAZARD)!=0)return false;
        return (standMasks[i]&bit)!=0||(f&LIQUID)!=0||(f&CLIMB)!=0;
    }
    public boolean clear(BlockPos p){return clear(PathNode.fromBlockPos(p));}
    public boolean clear(PathNode p){int i=idx(p);return i>=0&&(clearMasks[i]&(1<<p.lane))!=0;}
    public boolean hazard(BlockPos p){return hazard(PathNode.fromBlockPos(p));}
    public boolean hazard(PathNode p){return(flags(p)&HAZARD)!=0;}
    public boolean climbable(BlockPos p){return climbable(PathNode.fromBlockPos(p));}
    public boolean climbable(PathNode p){return(flags(p)&CLIMB)!=0;}
    public boolean liquid(BlockPos p){return liquid(PathNode.fromBlockPos(p));}
    public boolean liquid(PathNode p){return(flags(p)&LIQUID)!=0;}
    public boolean gateBarrier(BlockPos p){return(flags(PathNode.fromBlockPos(p))&GATE_BARRIER)!=0;}
    public double extraCost(BlockPos p){return extraCost(PathNode.fromBlockPos(p));}
    public double extraCost(PathNode p){int i=idx(p);return i<0?1000000D:costs[i];}
    public boolean lineClear(BlockPos from,BlockPos to){
        return lineClear(PathNode.fromBlockPos(from),PathNode.fromBlockPos(to));
    }
    public boolean lineClear(PathNode from,PathNode to){
        if(from.y2!=to.y2)return false;
        double dx=to.worldX()-from.worldX(),dz=to.worldZ()-from.worldZ();
        int samples=Math.max(1,(int)Math.ceil(Math.sqrt(dx*dx+dz*dz)*6.0D));
        for(int s=0;s<=samples;s++){
            double t=s/(double)samples;
            PathNode p=PathNode.atWorld(from.worldX()+dx*t,from.feetY(),from.worldZ()+dz*t);
            if(!clear(p))return false;
        }
        return true;
    }
    public boolean transitionClear(PathNode from,PathNode to){
        double dy=Math.abs(to.feetY()-from.feetY());
        if(dy>0.5001D)return jumpArcClear(from,to);
        if(dy>0.0001D)return true;
        return lineClear(from,to);
    }
    public boolean jumpArcClear(BlockPos from,BlockPos to){
        return jumpArcClear(PathNode.fromBlockPos(from),PathNode.fromBlockPos(to));
    }
    public boolean jumpArcClear(PathNode from,PathNode to){
        int n=Math.max(6,(int)Math.ceil(Math.sqrt(
                (to.worldX()-from.worldX())*(to.worldX()-from.worldX())
                +(to.worldZ()-from.worldZ())*(to.worldZ()-from.worldZ()))*5D));
        for(int i=1;i<n;i++){
            double t=i/(double)n;
            double x=from.worldX()+(to.worldX()-from.worldX())*t;
            double z=from.worldZ()+(to.worldZ()-from.worldZ())*t;
            double y=from.feetY()+(to.feetY()-from.feetY())*t+Math.sin(Math.PI*t);
            if(!clear(PathNode.atWorld(x,y,z)))return false;
        }
        return true;
    }
}
