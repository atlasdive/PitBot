package dev.atlasdive.geode.path;

/**
 * Applies the Sewer's mandatory passage constraint to every path search.
 * At x=93, within the (y=57..58, z=30..34) band, only feet position
 * (93,58,z=30..35) is traversable. A* cannot jump or smooth across the wall.
 */
public final class RequiredCorridorPathWorld implements PathWorld {
    private final PathWorld delegate;

    public RequiredCorridorPathWorld(PathWorld delegate){
        if(delegate==null)throw new IllegalArgumentException("delegate cannot be null");
        this.delegate=delegate;
    }

    private boolean isPassage(PathNode node){
        return node!=null&&node.x==93&&node.y2==116&&node.z>=30&&node.z<=35;
    }

    private boolean isRestricted(PathNode node){
        if(node==null||isPassage(node))return false;
        return node.x==93&&node.z>=30&&node.z<=34
                &&node.y2>=114&&node.y2<=117;
    }

    /** True when a long edge would skip the required x=93 passage column. */
    private boolean skipsPassage(PathNode from,PathNode to){
        if(from==null||to==null)return false;
        if(!((from.x<93&&to.x>93)||(to.x<93&&from.x>93)))return false;
        double x0=from.worldX(),x1=to.worldX();
        double dx=x1-x0;
        if(Math.abs(dx)<1.0E-8D)return false;
        double t=(93.5D-x0)/dx;
        if(t<0.0D||t>1.0D)return false;
        double z=from.worldZ()+(to.worldZ()-from.worldZ())*t;
        double y=from.feetY()+(to.feetY()-from.feetY())*t;
        boolean crossesBand=z>=29.5D&&z<=34.5D&&y>=57.0D&&y<=58.5D;
        return crossesBand&&!isPassage(from)&&!isPassage(to);
    }

    public boolean canStand(BlockPos pos){return canStand(PathNode.fromBlockPos(pos));}
    public boolean canStand(PathNode pos){return !isRestricted(pos)&&delegate.canStand(pos);}
    public boolean clear(BlockPos pos){return clear(PathNode.fromBlockPos(pos));}
    public boolean clear(PathNode pos){return !isRestricted(pos)&&delegate.clear(pos);}
    public boolean hazard(BlockPos pos){return hazard(PathNode.fromBlockPos(pos));}
    public boolean hazard(PathNode pos){return isRestricted(pos)||delegate.hazard(pos);}
    public boolean climbable(BlockPos pos){return delegate.climbable(pos);}
    public boolean climbable(PathNode pos){return delegate.climbable(pos);}
    public boolean liquid(BlockPos pos){return delegate.liquid(pos);}
    public boolean liquid(PathNode pos){return delegate.liquid(pos);}
    public boolean gateBarrier(BlockPos pos){return delegate.gateBarrier(pos);}
    public double extraCost(BlockPos pos){return delegate.extraCost(pos);}
    public double extraCost(PathNode pos){return delegate.extraCost(pos);}
    public boolean centerOnly(PathNode pos){return delegate.centerOnly(pos);}
    public boolean jumpArcClear(BlockPos from,BlockPos to){
        return jumpArcClear(PathNode.fromBlockPos(from),PathNode.fromBlockPos(to));
    }
    public boolean jumpArcClear(PathNode from,PathNode to){
        return !isRestricted(from)&&!isRestricted(to)&&!skipsPassage(from,to)
                &&delegate.jumpArcClear(from,to);
    }
    public boolean lineClear(BlockPos from,BlockPos to){
        return lineClear(PathNode.fromBlockPos(from),PathNode.fromBlockPos(to));
    }
    public boolean lineClear(PathNode from,PathNode to){
        return !isRestricted(from)&&!isRestricted(to)&&!skipsPassage(from,to)
                &&delegate.lineClear(from,to);
    }
    public boolean transitionClear(PathNode from,PathNode to){
        return !isRestricted(from)&&!isRestricted(to)&&!skipsPassage(from,to)
                &&delegate.transitionClear(from,to);
    }
}
