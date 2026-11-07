package dev.atlasdive.geode.path;

/** A path position with half-block vertical height and a collision-tested horizontal lane. */
public final class PathNode {
    public static final int CENTER=0,WEST=1,EAST=2,NORTH=3,SOUTH=4,LANE_COUNT=5;
    private static final double[] OFFSET_X={0.0D,-0.35D,0.35D,0.0D,0.0D};
    private static final double[] OFFSET_Z={0.0D,0.0D,0.0D,-0.35D,0.35D};
    public final int x,y2,z,lane;
    public PathNode(int x,int y2,int z){this(x,y2,z,CENTER);}
    public PathNode(int x,int y2,int z,int lane){
        if(lane<0||lane>=LANE_COUNT)throw new IllegalArgumentException("invalid horizontal lane");
        this.x=x;this.y2=y2;this.z=z;this.lane=lane;
    }
    public static PathNode fromBlockPos(BlockPos p){return new PathNode(p.x,p.y*2,p.z);}
    public static PathNode atFeet(int x,double feetY,int z){return new PathNode(x,(int)Math.round(feetY*2.0D),z);}
    public static PathNode atPlayer(double worldX,double feetY,double worldZ){
        int x=(int)Math.floor(worldX),z=(int)Math.floor(worldZ);
        return new PathNode(x,(int)Math.round(feetY*2.0D),z,nearestLane(worldX,worldZ));
    }
    public static PathNode atWorld(double worldX,double feetY,double worldZ){return atPlayer(worldX,feetY,worldZ);}
    public double feetY(){return y2*0.5D;}
    public double worldX(){return x+0.5D+OFFSET_X[lane];}
    public double worldZ(){return z+0.5D+OFFSET_Z[lane];}
    public BlockPos blockPos(){return new BlockPos(x,(int)Math.floor(feetY()),z);}
    public PathNode add(int dx,int dyHalf,int dz){return new PathNode(x+dx,y2+dyHalf,z+dz,lane);}
    public PathNode withLane(int nextLane){return new PathNode(x,y2,z,nextLane);}
    public static int nearestLane(double worldX,double worldZ){
        int x=(int)Math.floor(worldX),z=(int)Math.floor(worldZ);
        double localX=worldX-(x+0.5D),localZ=worldZ-(z+0.5D);
        int best=CENTER;double bestDistance=Double.POSITIVE_INFINITY;
        for(int lane=0;lane<LANE_COUNT;lane++){
            double dx=localX-OFFSET_X[lane],dz=localZ-OFFSET_Z[lane];
            double distance=dx*dx+dz*dz;
            if(distance<bestDistance){bestDistance=distance;best=lane;}
        }
        return best;
    }
    @Override public boolean equals(Object other){
        if(!(other instanceof PathNode))return false;
        PathNode p=(PathNode)other;return x==p.x&&y2==p.y2&&z==p.z&&lane==p.lane;
    }
    @Override public int hashCode(){return (((x*31)+y2)*31+z)*31+lane;}
    @Override public String toString(){return "("+worldX()+", "+feetY()+", "+worldZ()+")";}
}
