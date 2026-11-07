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

/** Immutable collision snapshot. Capture on the client thread; search the snapshot off-thread. */
public final class MinecraftPathWorld implements PathWorld {
    private static final byte OCCUPY=1, SUPPORT=2, HAZARD=4, CLIMB=8, LIQUID=16, CLEAR=32;
    private final int minX,minY,minZ,w,h,d;
    private final byte[] flags;
    private final float[] costs;

    private MinecraftPathWorld(int x,int y,int z,int w,int h,int d) {
        minX=x; minY=y; minZ=z; this.w=w; this.h=h; this.d=d;
        flags=new byte[w*h*d]; costs=new float[w*h*d];
    }

    public static MinecraftPathWorld capture(World world, EntityPlayerSP player,
                                               BlockPos start, java.util.List<BlockPos> goals) {
        int ax=start.x,bx=start.x,ay=start.y,by=start.y,az=start.z,bz=start.z;
        for(BlockPos p:goals) {
            ax=Math.min(ax,p.x); bx=Math.max(bx,p.x);
            ay=Math.min(ay,p.y); by=Math.max(by,p.y);
            az=Math.min(az,p.z); bz=Math.max(bz,p.z);
        }
        ax=Math.max(-29999990,ax-8); bx=Math.min(29999990,bx+8);
        az=Math.max(-29999990,az-8); bz=Math.min(29999990,bz+8);
        ay=Math.max(1,ay-8); by=Math.min(254,by+10);
        int w=Math.min(128,bx-ax+1), d=Math.min(128,bz-az+1), h=Math.min(96,by-ay+1);
        MinecraftPathWorld out=new MinecraftPathWorld(ax,ay,az,w,h,d);
        for(int x=ax;x<ax+w;x++) for(int y=ay;y<ay+h;y++) for(int z=az;z<az+d;z++) {
            net.minecraft.util.BlockPos p=new net.minecraft.util.BlockPos(x,y,z);
            if(!world.isBlockLoaded(p,false)) continue;
            Block b=world.getBlockState(p).getBlock();
            Material m=b.getMaterial();
            boolean liquid=m==Material.water || m==Material.lava;
            boolean hazard=b==Blocks.lava || b==Blocks.flowing_lava || b==Blocks.fire || b==Blocks.cactus;
            boolean climb=b instanceof BlockLadder || b instanceof BlockVine;
            AxisAlignedBB playerBox=new AxisAlignedBB(x+.20D,y,z+.20D,x+.80D,y+1.80D,z+.80D);
            boolean occupy=world.getCollidingBoundingBoxes(player,playerBox).isEmpty();
            boolean support=world.isBlockLoaded(p.down(),false)
                    && world.isSideSolid(p.down(),EnumFacing.UP,false);
            int i=out.index(x,y,z);
            byte f=0;
            if(occupy) f|=OCCUPY;
            if(support) f|=SUPPORT;
            if(hazard) f|=HAZARD;
            if(climb) f|=CLIMB;
            if(liquid) f|=LIQUID;
            if(occupy && !hazard) f|=CLEAR;
            out.flags[i]=f;
            float c=0;
            if(b==Blocks.water || b==Blocks.flowing_water) c+=2.0F;
            if(b==Blocks.web) c+=8.0F;
            if(b==Blocks.soul_sand) c+=1.0F;
            out.costs[i]=c;
        }
        return out;
    }

    private int index(int x,int y,int z) { return ((x-minX)*h+(y-minY))*d+(z-minZ); }
    private int idx(BlockPos p) {
        if(p.x<minX||p.y<minY||p.z<minZ||p.x>=minX+w||p.y>=minY+h||p.z>=minZ+d) return -1;
        return index(p.x,p.y,p.z);
    }
    private byte flags(BlockPos p) { int i=idx(p); return i<0?0:flags[i]; }

    public boolean canStand(BlockPos p) {
        byte f=flags(p);
        return (f&OCCUPY)!=0 && (f&HAZARD)==0 && ((f&SUPPORT)!=0||(f&CLIMB)!=0||(f&LIQUID)!=0);
    }
    public boolean clear(BlockPos p) { return (flags(p)&CLEAR)!=0; }
    public boolean hazard(BlockPos p) { return (flags(p)&HAZARD)!=0; }
    public boolean climbable(BlockPos p) { return (flags(p)&CLIMB)!=0; }
    public boolean liquid(BlockPos p) { return (flags(p)&LIQUID)!=0; }
    public double extraCost(BlockPos p) { int i=idx(p); return i<0?1000000D:costs[i]; }

    public boolean jumpArcClear(BlockPos from,BlockPos to) {
        int n=Math.max(6,(int)Math.ceil(Math.sqrt((to.x-from.x)*(double)(to.x-from.x)
                +(to.z-from.z)*(double)(to.z-from.z))*4D));
        for(int i=1;i<n;i++) {
            double t=i/(double)n;
            int x=(int)Math.floor(from.x+.5D+(to.x-from.x)*t);
            int z=(int)Math.floor(from.z+.5D+(to.z-from.z)*t);
            int y=(int)Math.floor(from.y+(to.y-from.y)*t+Math.sin(Math.PI*t)*1.0D);
            if(!clear(new BlockPos(x,y,z))||!clear(new BlockPos(x,y+1,z))) return false;
        }
        return true;
    }
}
