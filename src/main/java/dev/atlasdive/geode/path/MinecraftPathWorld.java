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

/** Immutable collision snapshot. Capture on the client thread; search the snapshot off-thread. */
public final class MinecraftPathWorld implements PathWorld {
    private static final byte OCCUPY=1,SUPPORT=2,HAZARD=4,CLIMB=8,LIQUID=16,CLEAR=32,GATE_BARRIER=64,CENTER_CLEAR=(byte)128;
    // A block cell may be standable slightly off-centre on a stair tread. These five
    // candidate player centres stay within the movement controller's waypoint radius.
    private static final double[] OFFSET_X={0.0D,-0.35D,0.35D,0.0D,0.0D};
    private static final double[] OFFSET_Z={0.0D,0.0D,0.0D,-0.35D,0.35D};
    private static final int OFFSET_MASK=(1<<5)-1;
    private final int minX,minY,minZ,w,h,d,halfH;
    private final byte[] flags;
    private final float[] costs;

    private MinecraftPathWorld(int x,int y,int z,int w,int h,int d){
        minX=x;minY=y;minZ=z;this.w=w;this.h=h;this.d=d;halfH=h*2;
        flags=new byte[w*halfH*d];costs=new float[w*halfH*d];
    }

    public static MinecraftPathWorld capture(World world,EntityPlayerSP player,BlockPos start,List<BlockPos> goals){
        List<PathNode> nodes=new ArrayList<PathNode>(goals.size());
        for(BlockPos goal:goals)nodes.add(PathNode.fromBlockPos(goal));
        return capture(world,player,PathNode.fromBlockPos(start),nodes);
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
        byte[] blockedMask=new byte[w*out.halfH*d],supportedMask=new byte[w*out.halfH*d];
        AxisAlignedBB region=new AxisAlignedBB(ax,ay,az,ax+w,ay+h,az+d);
        List<AxisAlignedBB> collisions=world.getCollidingBoundingBoxes(player,region);

        for(AxisAlignedBB box:collisions){
            // Expand by the largest candidate offset plus the player's 0.3-block half-width.
            int x0=Math.max(ax,(int)Math.floor(box.minX-1.15D));
            int x1=Math.min(ax+w-1,(int)Math.ceil(box.maxX+0.15D)-1);
            int z0=Math.max(az,(int)Math.floor(box.minZ-1.15D));
            int z1=Math.min(az+d-1,(int)Math.ceil(box.maxZ+0.15D)-1);
            int y0=Math.max(minY2,(int)Math.floor((box.minY-1.80D)*2.0D)+1);
            int y1=Math.min(maxY2,(int)Math.ceil(box.maxY*2.0D)-1);

            for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++){
                for(int y2=y0;y2<=y1;y2++){
                    double feetY=y2*0.5D;
                    int i=out.index(x,y2,z),mask=blockedMask[i]&0xFF;
                    for(int candidate=0;candidate<OFFSET_X.length;candidate++){
                        double cx=x+0.5D+OFFSET_X[candidate],cz=z+0.5D+OFFSET_Z[candidate];
                        AxisAlignedBB playerBox=new AxisAlignedBB(cx-0.30D,feetY,cz-0.30D,
                                cx+0.30D,feetY+1.80D,cz+0.30D);
                        if(playerBox.intersectsWith(box))mask|=1<<candidate;
                    }
                    blockedMask[i]=(byte)mask;
                }
            }

            // A collision-box top is a usable tread when at least one nearby player
            // centre has its full footprint supported by that surface.
            int feetY2=(int)Math.round(box.maxY*2.0D);
            if(Math.abs(box.maxY-feetY2*0.5D)<0.035D&&feetY2>=minY2&&feetY2<=maxY2){
                for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++){
                    int i=out.index(x,feetY2,z),mask=supportedMask[i]&0xFF;
                    for(int candidate=0;candidate<OFFSET_X.length;candidate++){
                        double cx=x+0.5D+OFFSET_X[candidate],cz=z+0.5D+OFFSET_Z[candidate];
                        if(box.maxX>cx-0.30D&&box.minX<cx+0.30D
                                &&box.maxZ>cz-0.30D&&box.minZ<cz+0.30D)mask|=1<<candidate;
                    }
                    supportedMask[i]=(byte)mask;
                }
            }
        }

        for(int x=ax;x<ax+w;x++)for(int y2=minY2;y2<=maxY2;y2++)for(int z=az;z<az+d;z++){
            double feetY=y2*0.5D;
            int blockY=(int)Math.floor(feetY);
            net.minecraft.util.BlockPos p=new net.minecraft.util.BlockPos(x,blockY,z);
            if(!world.isBlockLoaded(p,false))continue;
            Block b=world.getBlockState(p).getBlock();
            Material m=b.getMaterial();
            boolean liquid=m==Material.water||m==Material.lava;
            boolean hazard=b==Blocks.lava||b==Blocks.flowing_lava||b==Blocks.fire||b==Blocks.cactus;
            boolean climb=b instanceof BlockLadder||b instanceof BlockVine;
            boolean gateBarrier=b==Blocks.iron_bars||b instanceof BlockPane||b instanceof BlockFence
                    ||b instanceof BlockWall||b.isOpaqueCube();
            int i=out.index(x,y2,z);
            int clearMask=(~(blockedMask[i]&0xFF))&OFFSET_MASK;
            int standMask=clearMask&(supportedMask[i]&0xFF);
            // Full-cube fallback for integer-height nodes; partial surfaces are detected
            // from collision boxes above, including the half-height stair treads.
            if(standMask==0&&(y2&1)==0){
                net.minecraft.util.BlockPos below=p.down();
                if(world.isBlockLoaded(below,false)&&world.isSideSolid(below,EnumFacing.UP,false))
                    standMask=clearMask;
            }
            boolean occupy=clearMask!=0,support=standMask!=0;
            byte f=0;
            if(occupy)f|=OCCUPY;
            if(support)f|=SUPPORT;
            if(hazard)f|=HAZARD;
            if(climb)f|=CLIMB;
            if(liquid)f|=LIQUID;
            if(occupy&&!hazard)f|=CLEAR;
            if((blockedMask[i]&1)==0&&!hazard)f|=CENTER_CLEAR;
            if(gateBarrier)f|=GATE_BARRIER;
            out.flags[i]=f;
            float c=0F;
            if(b==Blocks.water||b==Blocks.flowing_water)c+=2F;
            if(b==Blocks.web)c+=8F;
            if(b==Blocks.soul_sand)c+=1F;
            out.costs[i]=c;
        }
        return out;
    }

    private int index(int x,int y2,int z){return ((x-minX)*halfH+(y2-minY*2))*d+(z-minZ);}
    private int idx(PathNode p){
        if(p.x<minX||p.y2<minY*2||p.z<minZ||p.x>=minX+w||p.y2>=minY*2+halfH||p.z>=minZ+d)return -1;
        return index(p.x,p.y2,p.z);
    }
    private int idx(BlockPos p){return idx(PathNode.fromBlockPos(p));}
    private byte flags(PathNode p){int i=idx(p);return i<0?0:flags[i];}
    private byte flags(BlockPos p){int i=idx(p);return i<0?0:flags[i];}

    public boolean canStand(BlockPos p){byte f=flags(p);return(f&OCCUPY)!=0&&(f&HAZARD)==0&&(f&SUPPORT)!=0;}
    public boolean canStand(PathNode p){byte f=flags(p);return(f&OCCUPY)!=0&&(f&HAZARD)==0&&(f&SUPPORT)!=0;}
    public boolean clear(BlockPos p){return(flags(p)&CLEAR)!=0;}
    public boolean clear(PathNode p){return(flags(p)&CLEAR)!=0;}
    public boolean hazard(BlockPos p){return(flags(p)&HAZARD)!=0;}
    public boolean hazard(PathNode p){return(flags(p)&HAZARD)!=0;}
    public boolean climbable(BlockPos p){return(flags(p)&CLIMB)!=0;}
    public boolean climbable(PathNode p){return(flags(p)&CLIMB)!=0;}
    public boolean liquid(BlockPos p){return(flags(p)&LIQUID)!=0;}
    public boolean liquid(PathNode p){return(flags(p)&LIQUID)!=0;}
    public boolean gateBarrier(BlockPos p){return(flags(p)&GATE_BARRIER)!=0;}
    public double extraCost(BlockPos p){int i=idx(p);return i<0?1000000D:costs[i];}
    public double extraCost(PathNode p){int i=idx(p);return i<0?1000000D:costs[i];}

    public boolean lineClear(BlockPos from,BlockPos to){return lineClear(PathNode.fromBlockPos(from),PathNode.fromBlockPos(to));}
    public boolean lineClear(PathNode from,PathNode to){
        if(from.y2!=to.y2)return false;
        double dx=to.x-from.x,dz=to.z-from.z;
        int samples=Math.max(1,(int)Math.ceil(Math.sqrt(dx*dx+dz*dz)*5.0D));
        for(int i=0;i<=samples;i++){
            double t=i/(double)samples,cx=from.x+0.5D+dx*t,cz=from.z+0.5D+dz*t;
            PathNode p=new PathNode((int)Math.floor(cx),from.y2,(int)Math.floor(cz));
            if((flags(p)&CENTER_CLEAR)==0)return false;
        }
        return true;
    }
    public boolean jumpArcClear(BlockPos from,BlockPos to){return jumpArcClear(PathNode.fromBlockPos(from),PathNode.fromBlockPos(to));}
    public boolean jumpArcClear(PathNode from,PathNode to){
        int n=Math.max(6,(int)Math.ceil(Math.sqrt((to.x-from.x)*(double)(to.x-from.x)
                +(to.z-from.z)*(double)(to.z-from.z))*4D));
        for(int i=1;i<n;i++){
            double t=i/(double)n;
            int x=(int)Math.floor(from.x+0.5D+(to.x-from.x)*t);
            int z=(int)Math.floor(from.z+0.5D+(to.z-from.z)*t);
            double y=from.feetY()+(to.feetY()-from.feetY())*t+Math.sin(Math.PI*t);
            if(!clear(PathNode.atFeet(x,y,z)))return false;
        }
        return true;
    }
}
