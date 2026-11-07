package dev.atlasdive.geode.path;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLadder;
import net.minecraft.block.BlockVine;
import net.minecraft.block.material.Material;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.init.Blocks;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.World;
import java.util.List;

/** Immutable collision snapshot. Capture on the client thread; search the snapshot off-thread. */
public final class MinecraftPathWorld implements PathWorld {
    private static final byte OCCUPY=1, SUPPORT=2, HAZARD=4, CLIMB=8, LIQUID=16, CLEAR=32, IRON_BARS=64;
    private final int minX,minY,minZ,w,h,d;
    private final byte[] flags;
    private final float[] costs;

    private MinecraftPathWorld(int x,int y,int z,int w,int h,int d) {
        minX=x;minY=y;minZ=z;this.w=w;this.h=h;this.d=d;
        flags=new byte[w*h*d];costs=new float[w*h*d];
    }

    public static MinecraftPathWorld capture(World world,EntityPlayerSP player,BlockPos start,List<BlockPos> goals) {
        int ax=start.x,bx=start.x,ay=start.y,by=start.y,az=start.z,bz=start.z;
        for(BlockPos p:goals){ax=Math.min(ax,p.x);bx=Math.max(bx,p.x);ay=Math.min(ay,p.y);by=Math.max(by,p.y);az=Math.min(az,p.z);bz=Math.max(bz,p.z);}
        ax=Math.max(-29999990,ax-8);bx=Math.min(29999990,bx+8);
        az=Math.max(-29999990,az-8);bz=Math.min(29999990,bz+8);
        ay=Math.max(1,ay-8);by=Math.min(254,by+10);
        int w=Math.min(128,bx-ax+1),d=Math.min(128,bz-az+1),h=Math.min(96,by-ay+1);
        MinecraftPathWorld out=new MinecraftPathWorld(ax,ay,az,w,h,d);

        // Ask Minecraft for collisions once for the region, then rasterize the returned boxes
        // into possible player-feet positions. This avoids hundreds of thousands of per-cell
        // collision queries on the render/client thread.
        boolean[] blocked=new boolean[w*h*d];
        boolean[] supported=new boolean[w*h*d];
        AxisAlignedBB region=new AxisAlignedBB(ax,ay,az,ax+w,ay+h,az+d);
        List<AxisAlignedBB> collisions=world.getCollidingBoundingBoxes(player,region);
        for(AxisAlignedBB box:collisions){
            int x0=Math.max(ax, (int)Math.floor(box.minX-1.0D));
            int x1=Math.min(ax+w-1,(int)Math.floor(box.maxX));
            int y0=Math.max(ay, (int)Math.floor(box.minY-2.0D));
            int y1=Math.min(ay+h-1,(int)Math.floor(box.maxY));
            int z0=Math.max(az, (int)Math.floor(box.minZ-1.0D));
            int z1=Math.min(az+d-1,(int)Math.floor(box.maxZ));
            for(int x=x0;x<=x1;x++)for(int y=y0;y<=y1;y++)for(int z=z0;z<=z1;z++){
                AxisAlignedBB playerBox=new AxisAlignedBB(x+.20D,y,z+.20D,x+.80D,y+1.80D,z+.80D);
                if(playerBox.intersectsWith(box))blocked[out.index(x,y,z)]=true;
            }

            // Support is based on actual collision-box tops under the player's footprint,
            // not isSideSolid(). The latter rejects useful partial blocks such as iron bars.
            int feetY=(int)Math.round(box.maxY);
            if(Math.abs(box.maxY-feetY)<0.035D&&feetY>=ay&&feetY<ay+h){
                int sx0=Math.max(ax,(int)Math.floor(box.minX-.80D));
                int sx1=Math.min(ax+w-1,(int)Math.floor(box.maxX-.20D));
                int sz0=Math.max(az,(int)Math.floor(box.minZ-.80D));
                int sz1=Math.min(az+d-1,(int)Math.floor(box.maxZ-.20D));
                for(int x=sx0;x<=sx1;x++)for(int z=sz0;z<=sz1;z++){
                    if(box.maxX>x+.20D&&box.minX<x+.80D
                            &&box.maxZ>z+.20D&&box.minZ<z+.80D){
                        supported[out.index(x,feetY,z)]=true;
                    }
                }
            }
        }

        for(int x=ax;x<ax+w;x++)for(int y=ay;y<ay+h;y++)for(int z=az;z<az+d;z++){
            net.minecraft.util.BlockPos p=new net.minecraft.util.BlockPos(x,y,z);
            if(!world.isBlockLoaded(p,false))continue;
            Block b=world.getBlockState(p).getBlock();
            Material m=b.getMaterial();
            boolean liquid=m==Material.water||m==Material.lava;
            boolean hazard=b==Blocks.lava||b==Blocks.flowing_lava||b==Blocks.fire||b==Blocks.cactus;
            boolean climb=b instanceof BlockLadder||b instanceof BlockVine;
            boolean ironBars=b==Blocks.iron_bars;
            int i=out.index(x,y,z);
            boolean occupy=!blocked[i];
            boolean support=supported[i];
            byte f=0;
            if(occupy)f|=OCCUPY;
            if(support)f|=SUPPORT;
            if(hazard)f|=HAZARD;
            if(climb)f|=CLIMB;
            if(liquid)f|=LIQUID;
            if(occupy&&!hazard)f|=CLEAR;
            if(ironBars)f|=IRON_BARS;
            out.flags[i]=f;
            float c=0F;
            if(b==Blocks.water||b==Blocks.flowing_water)c+=2F;
            if(b==Blocks.web)c+=8F;
            if(b==Blocks.soul_sand)c+=1F;
            out.costs[i]=c;
        }
        return out;
    }

    private int index(int x,int y,int z){return ((x-minX)*h+(y-minY))*d+(z-minZ);}
    private int idx(BlockPos p){
        if(p.x<minX||p.y<minY||p.z<minZ||p.x>=minX+w||p.y>=minY+h||p.z>=minZ+d)return -1;
        return index(p.x,p.y,p.z);
    }
    private byte flags(BlockPos p){int i=idx(p);return i<0?0:flags[i];}
    public boolean canStand(BlockPos p){byte f=flags(p);return(f&OCCUPY)!=0&&(f&HAZARD)==0&&((f&SUPPORT)!=0||(f&CLIMB)!=0||(f&LIQUID)!=0);}
    public boolean clear(BlockPos p){return(flags(p)&CLEAR)!=0;}
    public boolean hazard(BlockPos p){return(flags(p)&HAZARD)!=0;}
    public boolean climbable(BlockPos p){return(flags(p)&CLIMB)!=0;}
    public boolean liquid(BlockPos p){return(flags(p)&LIQUID)!=0;}
    public boolean ironBars(BlockPos p){return(flags(p)&IRON_BARS)!=0;}
    public double extraCost(BlockPos p){int i=idx(p);return i<0?1000000D:costs[i];}

    public boolean lineClear(BlockPos from,BlockPos to){
        if(from.y!=to.y)return false;
        double dx=to.x-from.x,dz=to.z-from.z;
        int samples=Math.max(1,(int)Math.ceil(Math.sqrt(dx*dx+dz*dz)*5.0D));
        for(int i=0;i<=samples;i++){
            double t=i/(double)samples;
            double cx=from.x+.5D+dx*t,cz=from.z+.5D+dz*t;
            int minX=(int)Math.floor(cx-.30D),maxX=(int)Math.floor(cx+.30D);
            int minZ=(int)Math.floor(cz-.30D),maxZ=(int)Math.floor(cz+.30D);
            for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++){
                if(!clear(new BlockPos(x,from.y,z))||!clear(new BlockPos(x,from.y+1,z)))return false;
            }
        }
        return true;
    }

    public boolean jumpArcClear(BlockPos from,BlockPos to){
        int n=Math.max(6,(int)Math.ceil(Math.sqrt((to.x-from.x)*(double)(to.x-from.x)+(to.z-from.z)*(double)(to.z-from.z))*4D));
        for(int i=1;i<n;i++){
            double t=i/(double)n;
            int x=(int)Math.floor(from.x+.5D+(to.x-from.x)*t);
            int z=(int)Math.floor(from.z+.5D+(to.z-from.z)*t);
            int y=(int)Math.floor(from.y+(to.y-from.y)*t+Math.sin(Math.PI*t));
            if(!clear(new BlockPos(x,y,z))||!clear(new BlockPos(x,y+1,z)))return false;
        }
        return true;
    }
}
