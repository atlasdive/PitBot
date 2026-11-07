package dev.atlasdive.geode.path;

/** A player-feet position on a half-block vertical grid. */
public final class PathNode {
    public final int x,y2,z;
    public PathNode(int x,int y2,int z){this.x=x;this.y2=y2;this.z=z;}
    public static PathNode fromBlockPos(BlockPos p){return new PathNode(p.x,p.y*2,p.z);}
    public static PathNode atFeet(int x,double feetY,int z){return new PathNode(x,(int)Math.round(feetY*2.0D),z);}
    public double feetY(){return y2*0.5D;}
    public BlockPos blockPos(){return new BlockPos(x,(int)Math.floor(feetY()),z);}
    public PathNode add(int dx,int dyHalf,int dz){return new PathNode(x+dx,y2+dyHalf,z+dz);}
    @Override public boolean equals(Object other){
        if(!(other instanceof PathNode))return false;
        PathNode p=(PathNode)other;return x==p.x&&y2==p.y2&&z==p.z;
    }
    @Override public int hashCode(){return ((x*31)+y2)*31+z;}
    @Override public String toString(){return "("+x+", "+feetY()+", "+z+")";}
}
