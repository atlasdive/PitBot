package dev.atlasdive.geode.client;

import dev.atlasdive.geode.GeodeConfig;
import dev.atlasdive.geode.path.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import org.lwjgl.opengl.GL11;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;
import java.util.*;
import java.util.concurrent.*;

public final class GeodeClient {
    public static final KeyBinding TOGGLE_KEY=new KeyBinding("Toggle Geode",Keyboard.KEY_G,"Geode");
    private static final BlockPos LAUNCH=new BlockPos(16,95,16), GATE=new BlockPos(47,66,75);
    private enum Stage { IDLE,TO_LAUNCH,WAIT_LAUNCH,TO_GATE,ENTER_GATE,WAIT_GATE_LANDING,SEEK,PATROL,TO_CHEST,OPEN,LOOT }
    private final Minecraft mc=Minecraft.getMinecraft();
    private final MovementController movement=new MovementController();
    private final ExecutorService worker=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"Geode-AStar");t.setDaemon(true);return t;});
    private Future<PathResult> future;
    private Stage stage=Stage.IDLE;
    private boolean running,launchDetected;
    private String status="Stopped",purpose="route",gateDiagnostics="not scanned";
    private BlockPos chest,lastStart,gateLanding,gateDropSource;
    private final Set<BlockPos> gateColumns=new HashSet<BlockPos>();
    private final Map<BlockPos,BlockPos> gateApproachMap=new HashMap<BlockPos,BlockPos>();
    private final Set<BlockPos> failedGateApproaches=new HashSet<BlockPos>();
    private int gatePlaneY=GATE.y;
    private int ticks,waitTicks,scanTicks,emptyScans,retryTicks,openAttempts,lootTicks,emptySlots,failedClicks,patrol,expandedNodes,goalCandidates,gateEntryTicks;
    private double launchY,launchX,launchZ,previousGateFeet=Double.NaN;
    private boolean gateCrossed;
    private final Map<BlockPos,Long> cooldown=new HashMap<BlockPos,Long>();

    @SubscribeEvent public void onTick(TickEvent.ClientTickEvent e) {
        if(e.phase!=TickEvent.Phase.END)return;
        ticks++;
        while(TOGGLE_KEY.isPressed())toggle();
        if(!running)return;
        EntityPlayerSP p=mc.thePlayer;World w=mc.theWorld;
        if(p==null||w==null){stop("Disconnected");return;}
        if(!GeodeConfig.isAllowed(mc.getCurrentServerData()==null?"":mc.getCurrentServerData().serverIP,mc.isSingleplayer())){
            stop("Server is not allowlisted");tell("Stopped: add the exact host to config/geode.cfg > security.allowedServers.");return;
        }
        collectResult(p);
        if(stage==Stage.ENTER_GATE||stage==Stage.WAIT_GATE_LANDING)trackGateCrossing(p);
        switch(stage){
            case TO_LAUNCH:case TO_GATE:case TO_CHEST:case PATROL:tickRoute(p,w);break;
            case ENTER_GATE:tickGateEntry(p);break;
            case WAIT_GATE_LANDING:movement.stop();tickGateLanding(p,w);break;
            case WAIT_LAUNCH:
                movement.stop();waitTicks++;
                if(p.posY<launchY-5D||sq(p.posX-launchX)+sq(p.posZ-launchZ)>64D)launchDetected=true;
                // Wait for the LaunchPad drop to finish before planning from a stable feet position.
                if((launchDetected&&p.onGround)||(waitTicks>300&&p.onGround)){
                    stage=Stage.TO_GATE;status="Pathfinding through Sewer iron-bar opening";routeGate(p,w);
                }
                break;
            case SEEK:
                movement.stop();scanTicks++;
                if(scanTicks>=GeodeConfig.scanIntervalTicks){scanTicks=0;findChest(p,w);}
                break;
            case OPEN:movement.stop();openChest(p,w);break;
            case LOOT:lootChest(p);break;
            default:movement.stop();
        }
    }

    @SubscribeEvent public void onChat(ClientChatReceivedEvent e){
        if(running&&e.message!=null&&e.message.getUnformattedText().toLowerCase(Locale.ROOT).contains("a new treasure spawned somewhere")){
            scanTicks=GeodeConfig.scanIntervalTicks;status="Treasure message received - scanning loaded chunks";
        }
    }
    @SubscribeEvent public void onHud(RenderGameOverlayEvent.Post e){
        if(e.type!=RenderGameOverlayEvent.ElementType.ALL||mc.fontRendererObj==null)return;
        mc.fontRendererObj.drawStringWithShadow("Geode "+(running?"ON":"OFF")+" | "+stage,7,7,running?0x55FF55:0xAAAAAA);
        mc.fontRendererObj.drawStringWithShadow(status,7,18,0xFFFFFF);
        mc.fontRendererObj.drawStringWithShadow("Gap: "+(gateLanding==null?"detecting":gateLanding.x+","+gatePlaneY+","+gateLanding.z)
                +" | crossed: "+gateCrossed,7,29,0x55FFFF);
        mc.fontRendererObj.drawStringWithShadow("Gate scan: "+gateDiagnostics,7,40,0xAAAAAA);
        mc.fontRendererObj.drawStringWithShadow(chest==null?"Chest: scanning loaded chunks":"Chest: "+chest,7,51,0x55FFFF);
        mc.fontRendererObj.drawStringWithShadow("Expanded: "+expandedNodes+" | Goals: "+goalCandidates,7,62,0xAAAAAA);
        if(mc.thePlayer!=null){
            mc.fontRendererObj.drawStringWithShadow("Player: "+playerPos(mc.thePlayer),7,73,0xAAAAAA);
        }
        mc.fontRendererObj.drawStringWithShadow("G: toggle",7,84,0xAAAAAA);
    }

    @SubscribeEvent public void onWorldRender(RenderWorldLastEvent e){
        if(!running||!movement.hasPath()||mc.theWorld==null)return;
        List<PathStep> steps=movement.getPath();
        if(steps.size()<2)return;
        net.minecraft.client.renderer.entity.RenderManager rm=mc.getRenderManager();
        int first=Math.max(1,movement.getIndex());
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT|GL11.GL_LINE_BIT|GL11.GL_CURRENT_BIT|GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glLineWidth(2.0F);
        GL11.glDepthMask(false);
        for(int i=first;i<steps.size();i++){
            PathStep a=steps.get(i-1),b=steps.get(i);
            if(b.kind==MovementKind.DROP)GL11.glColor4f(1.0F,0.62F,0.16F,0.95F);
            else if(b.kind==MovementKind.JUMP||b.kind==MovementKind.STEP_UP)GL11.glColor4f(0.95F,0.85F,0.20F,0.95F);
            else GL11.glColor4f(0.20F,0.85F,1.0F,0.90F);
            GL11.glBegin(GL11.GL_LINES);
            GL11.glVertex3d(a.pos.x+0.5D-rm.viewerPosX,a.pos.y+0.08D-rm.viewerPosY,a.pos.z+0.5D-rm.viewerPosZ);
            GL11.glVertex3d(b.pos.x+0.5D-rm.viewerPosX,b.pos.y+0.08D-rm.viewerPosY,b.pos.z+0.5D-rm.viewerPosZ);
            GL11.glEnd();
        }
        GL11.glDepthMask(true);
        GL11.glPopAttrib();
    }

    private void toggle(){if(running){stop("Stopped by user");tell("Geode stopped.");}else start();}
    private void start(){
        EntityPlayerSP p=mc.thePlayer;if(p==null||mc.theWorld==null){tell("Join the remake server first.");return;}
        if(!GeodeConfig.isAllowed(mc.getCurrentServerData()==null?"":mc.getCurrentServerData().serverIP,mc.isSingleplayer())){
            tell("This host is blocked. Add its exact hostname to config/geode.cfg.");return;
        }
        running=true;stage=Stage.TO_LAUNCH;launchX=p.posX;launchY=p.posY;launchZ=p.posZ;launchDetected=false;
        cooldown.clear();chest=null;emptyScans=0;patrol=0;failedGateApproaches.clear();resetGateState();
        tell("Geode started. Pathfinding to (16, 95, 16).");routeLaunch(p,mc.theWorld);
    }
    private void stop(String why){
        running=false;stage=Stage.IDLE;status=why;movement.stop();
        if(future!=null)future.cancel(true);future=null;chest=null;resetGateState();
    }
    private void tickRoute(EntityPlayerSP p,World w){
        if(future!=null){movement.stop();return;}
        if(!movement.hasPath()){if(ticks>=retryTicks)reRoute(p,w);return;}
        movement.tick(p);
        if(movement.stuck()){movement.stop();retryTicks=ticks+5;status="Blocked - recalculating path";return;}
        if(movement.complete()){movement.stop();arrived(p,w);}
    }
    private void arrived(EntityPlayerSP p,World w){
        if(stage==Stage.TO_LAUNCH){
            launchX=p.posX;launchY=p.posY;launchZ=p.posZ;waitTicks=0;launchDetected=false;
            stage=Stage.WAIT_LAUNCH;status="At LaunchPad - waiting for launch and landing";
        }else if(stage==Stage.TO_GATE){
            double feet=p.getEntityBoundingBox().minY;
            if(gateLanding!=null&&gateDropSource!=null){
                stage=Stage.ENTER_GATE;
                gateEntryTicks=0;
                previousGateFeet=feet;
                status="At gate approach "+gateDropSource+"; stepping into opening "+gateLanding;
            }else{
                retryTicks=ticks+20;
                status="Gate approach target missing; replanning";
            }
        }else if(stage==Stage.TO_CHEST){stage=Stage.OPEN;openAttempts=0;retryTicks=ticks;}
        else if(stage==Stage.PATROL){stage=Stage.SEEK;scanTicks=GeodeConfig.scanIntervalTicks;status="Patrol point reached - scanning again";}
    }
    private void reRoute(EntityPlayerSP p,World w){
        if(stage==Stage.TO_LAUNCH)routeLaunch(p,w);
        else if(stage==Stage.TO_GATE)routeGate(p,w);
        else if(stage==Stage.TO_CHEST&&chest!=null)routeChest(p,w,chest);
        else if(stage==Stage.PATROL)routePatrol(p,w);
    }
    private void routeLaunch(EntityPlayerSP p,World w){route(p,w,box(16,16,94,97,16,16),"LaunchPad");}
    private void routeGate(EntityPlayerSP p,World w){
        // The marked coordinate is approximate. The planner scans a small area around it,
        // detects a real iron-bar frame, and only accepts a clear shaft through that frame.
        List<BlockPos> volume=new ArrayList<BlockPos>();
        int radius=GeodeConfig.gateScanRadius;
        for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++){
            if(dx*dx+dz*dz>radius*radius)continue;
            for(int y=GATE.y+2-GeodeConfig.maxFallBlocks;y<GATE.y;y++)
                volume.add(new BlockPos(GATE.x+dx,y,GATE.z+dz));
        }
        route(p,w,volume,"Sewer opening");
    }
    private void routeChest(EntityPlayerSP p,World w,BlockPos c){
        route(p,w,Arrays.asList(c.add(1,0,0),c.add(-1,0,0),c.add(0,0,1),c.add(0,0,-1),
                c.add(1,-1,0),c.add(-1,-1,0),c.add(0,-1,1),c.add(0,-1,-1)),"chest "+c);
    }
    private void routePatrol(EntityPlayerSP p,World w){
        int[][] ring={{0,-12},{8,-8},{12,0},{8,8},{0,12},{-8,8},{-12,0},{-8,-8}};
        int[] o=ring[patrol++%ring.length];int x=GATE.x+o[0],z=GATE.z+o[1],y=MathHelper.floor_double(p.getEntityBoundingBox().minY);
        route(p,w,box(x-2,x+2,y-3,y+3,z-2,z+2),"Sewer patrol");
    }
    private void route(EntityPlayerSP p,World w,List<BlockPos> rough,String why){
        if(future!=null)future.cancel(true);movement.stop();
        BlockPos start=playerPos(p);
        lastStart=start;
        MinecraftPathWorld snap=MinecraftPathWorld.capture(w,p,start,rough);
        List<BlockPos> goals=new ArrayList<BlockPos>();
        if("Sewer opening".equals(why)){
            GatePlanner planner=new GatePlanner();
            GatePlan plan=planner.find(snap,GATE,start,GeodeConfig.gateScanRadius,GeodeConfig.maxFallBlocks);
            gateDiagnostics="Y "+planner.minPlaneY()+".."+planner.maxPlaneY()
                    +" | clear "+planner.clearColumns()
                    +" | frame "+planner.framedColumns()
                    +" | approach "+planner.approachColumns();
            if(plan==null){
                gateColumns.clear();gateApproachMap.clear();gateLanding=null;gateDropSource=null;gateCrossed=false;
                goalCandidates=0;
                status="No framed opening with a safe approach near "+GATE;
                retryTicks=ticks+40;
                return;
            }
            gatePlaneY=plan.planeY;
            gateColumns.clear();gateColumns.addAll(plan.columns);
            gateApproachMap.clear();gateApproachMap.putAll(plan.approachToAperture);
            for(BlockPos approach:plan.approaches){
                if(!failedGateApproaches.contains(approach))goals.add(approach);
            }
            if(goals.isEmpty()){
                failedGateApproaches.clear();
                goals.addAll(plan.approaches);
            }
            goalCandidates=goals.size();
            gateLanding=null;gateDropSource=null;gateCrossed=false;
            previousGateFeet=Double.NaN;
        }else{
            for(BlockPos g:rough)if(snap.canStand(g))goals.add(g);
        }
        if(goals.isEmpty()&&"LaunchPad".equals(why)){
            for(int x=LAUNCH.x-1;x<=LAUNCH.x+1;x++)for(int y=LAUNCH.y-1;y<=LAUNCH.y+2;y++)
                for(int z=LAUNCH.z-1;z<=LAUNCH.z+1;z++){
                    BlockPos g=new BlockPos(x,y,z);if(snap.canStand(g))goals.add(g);
                }
        }
        goalCandidates=goals.size();
        if(goals.isEmpty()){
            status="No standable goal cells for "+why+" near "+(rough.isEmpty()?"target":rough.get(0));
            retryTicks=ticks+40;
            return;
        }
        if(!snap.canStand(start)){
            status="Start position is not standable in snapshot: "+start;
            retryTicks=ticks+20;
            return;
        }
        purpose=why;status="Computing "+why+" path from "+start+" ("+goalCandidates+" goals)...";
        future=worker.submit(()->new Pathfinder().find(snap,start,goals,GeodeConfig.maxExpandedNodes,GeodeConfig.maxFallBlocks));
    }
    private void collectResult(EntityPlayerSP p){
        if(future==null||!future.isDone())return;
        Future<PathResult> f=future;future=null;
        try{
            PathResult r=f.get();
            expandedNodes=r.expanded;
            if(r.found()){
                if(stage==Stage.TO_GATE){
                    BlockPos approach=r.steps.get(r.steps.size()-1).pos;
                    BlockPos aperture=gateApproachMap.get(approach);
                    if(aperture==null){
                        status="A* reached a cell that is not a gate approach; retrying";
                        retryTicks=ticks+40;
                        return;
                    }
                    gateDropSource=approach;
                    gateLanding=aperture;
                    gateCrossed=false;
                    previousGateFeet=p.getEntityBoundingBox().minY;
                    status="Path to gate approach ready: "+approach+"; opening "+aperture;
                }else{
                    status=purpose+" path ready ("+r.steps.size()+" steps)";
                }
                movement.setPath(r.steps,p);
            }else{
                status="No path to "+purpose+" ("+r.expanded+" nodes, "+goalCandidates+" goals, start "+lastStart+")";
                retryTicks=ticks+40;
            }
        }catch(Exception ex){status="Path search error: "+ex.getClass().getSimpleName();retryTicks=ticks+30;}
    }
    private void findChest(EntityPlayerSP p,World w){
        BlockPos best=null;double bd=Double.POSITIVE_INFINITY;long now=w.getTotalWorldTime();
        // Always scan loaded tile entities. The chat announcement is only an optional early trigger.
        for(Object o:w.loadedTileEntityList)if(o instanceof TileEntityChest){
            net.minecraft.util.BlockPos pos=((TileEntityChest)o).getPos();
            // Ignore the explicitly excluded lobby/structure chest cuboid (inclusive bounds).
            if(isExcludedChest(pos.getX(),pos.getY(),pos.getZ()))continue;
            if(Math.abs(pos.getX()-GATE.x)>GeodeConfig.scanRadius||Math.abs(pos.getZ()-GATE.z)>GeodeConfig.scanRadius
                    ||pos.getY()<GATE.y-28||pos.getY()>GATE.y+8)continue;
            BlockPos c=new BlockPos(pos.getX(),pos.getY(),pos.getZ());
            Long until=cooldown.get(c);if(until!=null&&until>now)continue;
            double d=c.distanceSquared(p.posX,p.getEntityBoundingBox().minY,p.posZ);if(d<bd){bd=d;best=c;}
        }
        if(best!=null){emptyScans=0;chest=best;stage=Stage.TO_CHEST;status="Chest found: "+best;routeChest(p,w,best);}
        else if(++emptyScans>=5){emptyScans=0;stage=Stage.PATROL;routePatrol(p,w);}
        else status="No loaded Sewer chest; scanning again";
    }
    private boolean isExcludedChest(int x,int y,int z){
        return x>=19&&x<=23&&y>=66&&y<=70&&z>=72&&z<=82;
    }

    private void openChest(EntityPlayerSP p,World w){
        if(chest==null){stage=Stage.SEEK;return;}
        net.minecraft.util.BlockPos bp=new net.minecraft.util.BlockPos(chest.x,chest.y,chest.z);
        if(!w.isBlockLoaded(bp,false)||(w.getBlockState(bp).getBlock()!=net.minecraft.init.Blocks.chest
                &&w.getBlockState(bp).getBlock()!=net.minecraft.init.Blocks.trapped_chest)){
            cooldown.put(chest,w.getTotalWorldTime()+100L);chest=null;stage=Stage.SEEK;return;
        }
        if(mc.currentScreen instanceof GuiChest&&p.openContainer instanceof ContainerChest){stage=Stage.LOOT;lootTicks=0;emptySlots=0;failedClicks=0;return;}
        if(p.getDistance(chest.x+.5D,chest.y+.5D,chest.z+.5D)>5D){stage=Stage.TO_CHEST;routeChest(p,w,chest);return;}
        if(ticks<retryTicks)return;
        if(openAttempts>=3){cooldown.put(chest,w.getTotalWorldTime()+200L);chest=null;stage=Stage.SEEK;return;}
        openAttempts++;retryTicks=ticks+12;
        mc.playerController.onPlayerRightClick(p,mc.theWorld,p.getHeldItem(),bp,EnumFacing.UP,new Vec3(chest.x+.5D,chest.y+.5D,chest.z+.5D));
    }
    private void lootChest(EntityPlayerSP p){
        if(!(mc.currentScreen instanceof GuiChest)||!(p.openContainer instanceof ContainerChest)){finish();return;}
        if(++lootTicks<3)return;lootTicks=0;
        ContainerChest c=(ContainerChest)p.openContainer;int n=c.getLowerChestInventory().getSizeInventory();
        for(int i=0;i<n;i++){
            ItemStack s=c.getLowerChestInventory().getStackInSlot(i);if(s==null)continue;
            int before=s.stackSize;mc.playerController.windowClick(c.windowId,i,0,1,p);
            ItemStack after=c.getLowerChestInventory().getStackInSlot(i);
            failedClicks=after!=null&&after.stackSize>=before?failedClicks+1:0;
            if(failedClicks>=3){p.closeScreen();finish();return;}return;
        }
        if(++emptySlots>=2){p.closeScreen();finish();}
    }
    private void finish(){
        if(chest!=null&&mc.theWorld!=null)cooldown.put(chest,mc.theWorld.getTotalWorldTime()+1200L);
        chest=null;stage=Stage.SEEK;scanTicks=GeodeConfig.scanIntervalTicks;status="Chest cleared; searching for next";
    }
    private void tickGateEntry(EntityPlayerSP p){
        if(gateLanding==null){setGateKeys(false);stage=Stage.TO_GATE;retryTicks=ticks+5;return;}
        gateEntryTicks++;
        double targetX=gateLanding.x+0.5D,targetZ=gateLanding.z+0.5D;
        double dx=targetX-p.posX,dz=targetZ-p.posZ;
        double distance=Math.sqrt(dx*dx+dz*dz);
        double feet=p.getEntityBoundingBox().minY;

        if(gateCrossed||feet<gatePlaneY-0.10D){
            setGateKeys(false);
            stage=Stage.WAIT_GATE_LANDING;
            status=gateCrossed?"Iron-bar plane crossed; waiting for landing":"Below gate plane; verifying centered passage";
            return;
        }

        if(distance<0.16D){
            setGateKeys(false);
            status="Aligned with iron-bar opening; waiting to drop";
            if(gateEntryTicks>60){
                failedGateApproaches.add(gateDropSource);
                stage=Stage.TO_GATE;
                gateLanding=null;gateDropSource=null;gateCrossed=false;
                previousGateFeet=Double.NaN;
                retryTicks=ticks+5;
                status="Opening did not release player; trying another approach side";
            }
            return;
        }

        float targetYaw=(float)(Math.toDegrees(Math.atan2(dz,dx))-90.0D);
        float difference=MathHelper.wrapAngleTo180_float(targetYaw-p.rotationYaw);
        p.rotationYaw+=MathHelper.clamp_float(difference,-10.0F,10.0F);
        if(Math.abs(difference)>28.0F){
            setGateKeys(false);
        }else{
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(),true);
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(),false);
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(),false);
        }

        if(gateEntryTicks>120){
            setGateKeys(false);
            failedGateApproaches.add(gateDropSource);
            stage=Stage.TO_GATE;
            gateLanding=null;gateDropSource=null;gateCrossed=false;
            previousGateFeet=Double.NaN;
            retryTicks=ticks+5;
            status="Could not enter selected opening; choosing another approach";
        }
    }

    private void trackGateCrossing(EntityPlayerSP p){
        if(gateLanding==null||gateDropSource==null||gateCrossed)return;
        double feet=p.getEntityBoundingBox().minY;
        if(!Double.isNaN(previousGateFeet)){
            boolean fallingThrough=previousGateFeet>=gatePlaneY-0.02D&&feet<gatePlaneY-0.02D;
            boolean centered=Math.abs(p.posX-(gateLanding.x+0.5D))<=0.30D
                    &&Math.abs(p.posZ-(gateLanding.z+0.5D))<=0.30D;
            if(fallingThrough&&centered){
                gateCrossed=true;
                stage=Stage.WAIT_GATE_LANDING;
                gateEntryTicks=0;
                setGateKeys(false);
                status="Iron-bar opening crossed; confirming Sewer landing";
            }
        }
        previousGateFeet=feet;
    }

    private void tickGateLanding(EntityPlayerSP p,World w){
        double feet=p.getEntityBoundingBox().minY;
        gateEntryTicks++;
        if(gateCrossed&&p.onGround&&feet<gatePlaneY-0.25D){
            setGateKeys(false);
            stage=Stage.SEEK;
            scanTicks=GeodeConfig.scanIntervalTicks;
            tell("Iron-bar gap crossed. Sewer reached; searching for existing chests.");
            findChest(p,w);
            return;
        }
        if(gateEntryTicks>240){
            setGateKeys(false);
            status=gateCrossed?"Passed the grate; still waiting for a safe landing":"No verified crossing; retrying the gate approach";
            if(gateCrossed){
                // Keep waiting instead of falsely declaring Sewer entry.
                gateEntryTicks=0;
            }else{
                stage=Stage.TO_GATE;
                gateLanding=null;gateDropSource=null;previousGateFeet=Double.NaN;
                retryTicks=ticks+10;
            }
        }
    }

    private void setGateKeys(boolean forward){
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(),forward);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindBack.getKeyCode(),false);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindLeft.getKeyCode(),false);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindRight.getKeyCode(),false);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(),false);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(),false);
    }

    private void resetGateState(){
        gateLanding=null;gateDropSource=null;gateColumns.clear();gateApproachMap.clear();
        failedGateApproaches.clear();
        gateCrossed=false;previousGateFeet=Double.NaN;gatePlaneY=GATE.y;gateEntryTicks=0;
        if(mc!=null)setGateKeys(false);
    }

    private List<BlockPos> box(int ax,int bx,int ay,int by,int az,int bz){
        List<BlockPos> l=new ArrayList<BlockPos>();
        for(int x=ax;x<=bx;x++)for(int y=ay;y<=by;y++)for(int z=az;z<=bz;z++)l.add(new BlockPos(x,y,z));
        return l;
    }
    private BlockPos playerPos(EntityPlayerSP p){
        return new BlockPos(MathHelper.floor_double(p.posX),MathHelper.floor_double(p.getEntityBoundingBox().minY),MathHelper.floor_double(p.posZ));
    }
    private void tell(String s){if(mc.thePlayer!=null)mc.thePlayer.addChatMessage(new ChatComponentText("§b[Geode] §f"+s));}
    private static double sq(double v){return v*v;}
}
