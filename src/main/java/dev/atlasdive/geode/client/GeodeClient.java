package dev.atlasdive.geode.client;

import dev.atlasdive.geode.GeodeConfig;
import dev.atlasdive.geode.path.*;
import net.minecraft.block.Block;
import net.minecraft.block.BlockFalling;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.GuiOpenEvent;
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
    private enum Stage { IDLE,TO_LAUNCH,WAIT_LAUNCH,TO_GATE,ENTER_GATE,WAIT_GATE_LANDING,SEEK,PATROL,TO_CHEST,TO_SCAFFOLD,BUILD_SCAFFOLD,OPEN,LOOT,WATER_RECOVERY }
    private enum HopPhase { NONE, SPAWN_DELAY, WAIT_TRANSFER, SETTLE }
    private static final BlockPos AIMED_CHEST=new BlockPos(99,61,46);
    private final Minecraft mc=Minecraft.getMinecraft();
    private final MovementController movement=new MovementController();
    private final ExecutorService worker=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"Geode-AStar");t.setDaemon(true);return t;});
    private Future<PathResult> future;
    private Stage stage=Stage.IDLE;
    private boolean running,launchDetected,inSewer,deathRestartPending;
    private HopPhase hopPhase=HopPhase.NONE;
    private World hopOriginWorld;
    private int hopTicks,hopAttempts,hopRetryAt,deathWaitTicks,chestAimStableTicks,chestAimMissTicks,chestAimTicks;
    private String watchPlayerName="";
    private String status="Stopped",purpose="route",gateDiagnostics="not scanned";
    private BlockPos chest,lastStart,gateLanding,gateDropSource,patrolOrigin,patrolWaypoint;
    private final ChestLootTimer lootTimer=new ChestLootTimer();
    private ChestScaffoldPlan scaffoldPlan;
    private BlockPos scaffoldedChest;
    private final Set<BlockPos> failedScaffoldChests=new HashSet<BlockPos>();
    private int scaffoldIndex,scaffoldTicks,scaffoldAttempts,scaffoldOriginalSlot=-1;
    private float scaffoldOriginalPitch;
    private boolean scaffoldStateSaved,scaffoldJumpIssued,scaffoldJumpKeyHeld,scaffoldAirborneSeen,scaffoldPlaceRequested,scaffoldPlaced;
    private int scaffoldPlaceTick;
    private final Set<BlockPos> gateColumns=new HashSet<BlockPos>();
    private final Map<BlockPos,BlockPos> gateApproachMap=new HashMap<BlockPos,BlockPos>();
    private final Set<BlockPos> failedGateApproaches=new HashSet<BlockPos>();
    private int gatePlaneY=GATE.y;
    private int ticks,waitTicks,scanTicks,emptyScans,retryTicks,openAttempts,failedClicks,lootSlotCursor,lootEmptyTicks,
            patrolLeg,patrolFailures,expandedNodes,goalCandidates,gateEntryTicks,patrolScanTicks;
    private int loadedChestTiles,excludedChestTiles,waterRecoveryRadius=8;
    private int closeWindowId=-1,closeAttempts;
    private boolean chestGuiOpenIntercepted;
    private Stage waterResumeStage=Stage.SEEK;
    private boolean patrolTransit;
    private double launchY,launchX,launchZ,previousGateFeet=Double.NaN;
    private boolean gateCrossed;
    private final Map<BlockPos,Long> cooldown=new HashMap<BlockPos,Long>();

    @SubscribeEvent public void onTick(TickEvent.ClientTickEvent e) {
        if(e.phase!=TickEvent.Phase.END)return;
        ticks++;
        while(TOGGLE_KEY.isPressed())toggle();
        if(!running)return;
        EntityPlayerSP p=mc.thePlayer;World w=mc.theWorld;
        if(p==null||w==null){
            if(hopPhase!=HopPhase.NONE||deathRestartPending){movement.stop();return;}
            stop("Disconnected");return;
        }
        if(hopPhase==HopPhase.NONE&&!GeodeConfig.isAllowed(mc.getCurrentServerData()==null?"":mc.getCurrentServerData().serverIP,mc.isSingleplayer())){
            stop("Server is not allowlisted");tell("Stopped: add the exact host to config/geode.cfg > security.allowedServers.");return;
        }
        if(deathRestartPending){
            movement.stop();
            deathWaitTicks++;
            if(!p.isEntityAlive()||p.getHealth()<=0.0F||deathWaitTicks<20){
                status="Death detected; waiting for respawn ("+deathWaitTicks+" ticks)";
                return;
            }
            deathRestartPending=false;deathWaitTicks=0;inSewer=false;
            if(hasOtherListedPlayerInWatchArea(p,w))beginServerHop(p,false);
            else beginSewerRun(p,w);
            return;
        }
        if(hopPhase!=HopPhase.NONE){tickServerHop(p,w);return;}
        if(ticks<hopRetryAt){movement.stop();return;}
        if(chestGuiOpenIntercepted&&(stage==Stage.OPEN||stage==Stage.LOOT)){
            chestGuiOpenIntercepted=false;
            if(isContainerGuiOpen(p)){
                closeWindowId=currentContainerWindowId(p);
                closeActiveContainer(p);
                stage=Stage.LOOT;closeAttempts=1;retryTicks=ticks+4;
                status="Chest GUI suppressed; sent close for window "+closeWindowId;
                return;
            }
        }
        if(stage==Stage.ENTER_GATE||stage==Stage.WAIT_GATE_LANDING)trackGateCrossing(p);
        if(hasOtherListedPlayerInWatchArea(p,w)){
            beginServerHop(p,inSewer);
            return;
        }
        if(stage==Stage.IDLE){beginSewerRun(p,w);return;}
        collectResult(p);
        switch(stage){
            case TO_LAUNCH:case TO_GATE:case TO_CHEST:case TO_SCAFFOLD:case PATROL:case WATER_RECOVERY:tickRoute(p,w);break;
            case BUILD_SCAFFOLD:tickScaffold(p,w);break;
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

    /**
     * Intercept the chest screen before it becomes visible. NetHandlerPlayClient
     * assigns the server's windowId immediately after displayGUIChest() returns;
     * on the following client tick we close that now-identified container.
     */
    @SubscribeEvent public void onGuiOpen(GuiOpenEvent e){
        if(!running||chest==null||(stage!=Stage.OPEN&&stage!=Stage.LOOT))return;
        if(e.gui instanceof GuiChest){
            e.setCanceled(true);
            chestGuiOpenIntercepted=true;
            status="Intercepted chest GUI open event";
        }
    }

    @SubscribeEvent public void onChat(ClientChatReceivedEvent e){
        if(!running||e.message==null)return;
        String message=e.message.getUnformattedText();
        if(DeathMessageDetector.matches(message)){
            suspendNavigation(mc.thePlayer);
            hopPhase=HopPhase.NONE;hopOriginWorld=null;inSewer=false;
            deathRestartPending=true;deathWaitTicks=0;
            status="Death recap received; restarting the Sewer entry sequence";
            return;
        }
        if(message.toLowerCase(Locale.ROOT).contains("a new treasure spawned somewhere")){
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
        mc.fontRendererObj.drawStringWithShadow("Chests: loaded "+loadedChestTiles+" | excluded "+excludedChestTiles,7,62,0xAAAAAA);
        mc.fontRendererObj.drawStringWithShadow("Expanded: "+expandedNodes+" | Goals: "+goalCandidates,7,73,0xAAAAAA);
        if(mc.thePlayer!=null){
            mc.fontRendererObj.drawStringWithShadow("Player: "+playerPos(mc.thePlayer),7,84,0xAAAAAA);
        }
        mc.fontRendererObj.drawStringWithShadow("Patrol: "+patrolLeg+(patrolWaypoint==null?"":" -> "+patrolWaypoint),7,95,0xAAAAAA);
        mc.fontRendererObj.drawStringWithShadow("Scaffold: "+scaffoldIndex+"/7"+(stage==Stage.BUILD_SCAFFOLD?" placing":""),7,106,0xAAAAAA);
        mc.fontRendererObj.drawStringWithShadow("G: toggle",7,117,0xAAAAAA);
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
            GL11.glVertex3d(a.targetX-rm.viewerPosX,a.feetY+0.08D-rm.viewerPosY,a.targetZ-rm.viewerPosZ);
            GL11.glVertex3d(b.targetX-rm.viewerPosX,b.feetY+0.08D-rm.viewerPosY,b.targetZ-rm.viewerPosZ);
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
        running=true;inSewer=false;deathRestartPending=false;hopPhase=HopPhase.NONE;hopRetryAt=0;
        if(hasOtherListedPlayerInWatchArea(p,mc.theWorld))beginServerHop(p,false);
        else beginSewerRun(p,mc.theWorld);
    }

    private void beginSewerRun(EntityPlayerSP p,World w){
        suspendNavigation(p);
        inSewer=false;hopRetryAt=0;stage=Stage.TO_LAUNCH;
        launchX=p.posX;launchY=p.posY;launchZ=p.posZ;launchDetected=false;
        cooldown.clear();chest=null;emptyScans=0;patrolLeg=0;patrolFailures=0;retryTicks=ticks;
        waterRecoveryRadius=8;waterResumeStage=Stage.SEEK;
        scaffoldedChest=null;failedScaffoldChests.clear();scaffoldPlan=null;restoreScaffoldState(p);
        patrolOrigin=null;patrolWaypoint=null;patrolTransit=false;lootTimer.reset();lootSlotCursor=0;
        chestAimStableTicks=0;chestAimMissTicks=0;chestAimTicks=0;
        failedGateApproaches.clear();resetGateState();
        tell("Sewer entry sequence started. Pathfinding to (16, 95, 16).");
        routeLaunch(p,w);
    }

    private void suspendNavigation(EntityPlayerSP p){
        movement.stop();
        if(future!=null)future.cancel(true);
        future=null;
        restoreScaffoldState(p);
        scaffoldPlan=null;scaffoldIndex=0;
    }

    private boolean hasOtherListedPlayerInWatchArea(EntityPlayerSP self,World world){
        if(self==null||world==null||mc.getNetHandler()==null)return false;
        for(Object value:world.playerEntities){
            if(!(value instanceof EntityPlayer))continue;
            EntityPlayer other=(EntityPlayer)value;
            if(other==self)continue;
            NetworkPlayerInfo info=mc.getNetHandler().getPlayerInfo(other.getUniqueID());
            if(info==null)continue; // NPCs and most server-side practice bots are not in the tab list.
            String name=other.getName();
            String display=info.getDisplayName()==null?"":info.getDisplayName().getUnformattedText();
            if(isBotIdentity(name)||isBotIdentity(display))continue;
            if(WatchArea.contains(other.posX,other.getEntityBoundingBox().minY,other.posZ)){
                watchPlayerName=name==null?"unknown":name;
                return true;
            }
        }
        watchPlayerName="";
        return false;
    }

    private boolean isBotIdentity(String raw){
        if(raw==null)return false;
        String lower=raw.toLowerCase(Locale.ROOT).trim();
        return lower.equals("bot")||lower.startsWith("bot_")||lower.startsWith("bot-")
                ||lower.endsWith("_bot")||lower.endsWith("-bot")
                ||lower.startsWith("npc_")||lower.startsWith("npc-")||lower.startsWith("npc ")
                ||lower.contains("[bot]")||lower.contains("[npc]");
    }

    private void beginServerHop(EntityPlayerSP p,boolean fromSewer){
        if(p==null||hopPhase!=HopPhase.NONE)return;
        suspendNavigation(p);
        closeActiveContainer(p);
        stage=Stage.IDLE;hopAttempts=0;hopTicks=0;hopRetryAt=0;
        String who=watchPlayerName.isEmpty()?"another player":watchPlayerName;
        if(fromSewer){
            p.sendChatMessage("/spawn");
            hopPhase=HopPhase.SPAWN_DELAY;
            status="Player "+who+" entered the Sewer area; returning to spawn";
        }else{
            status="Player "+who+" is in the watched area; searching for an empty Pit server";
            sendPlayPit(p);
        }
    }

    private void sendPlayPit(EntityPlayerSP p){
        p.sendChatMessage("/play pit");
        hopOriginWorld=mc.theWorld;
        hopPhase=HopPhase.WAIT_TRANSFER;
        hopTicks=0;hopAttempts++;
        status="Requested /play pit (attempt "+hopAttempts+")";
    }

    private void tickServerHop(EntityPlayerSP p,World w){
        movement.stop();
        if(hopPhase==HopPhase.SPAWN_DELAY){
            if(++hopTicks>=20)sendPlayPit(p);
            return;
        }
        if(hopPhase==HopPhase.WAIT_TRANSFER){
            if(w!=hopOriginWorld){
                hopPhase=HopPhase.SETTLE;hopTicks=0;
                status="New Pit server loaded; rescanning after transfer";
                return;
            }
            if(++hopTicks>=80){
                if(hopAttempts<3)sendPlayPit(p);
                else{
                    hopPhase=HopPhase.NONE;hopOriginWorld=null;hopRetryAt=ticks+40;
                    status="No server transfer detected; retrying the player scan shortly";
                }
            }
            return;
        }
        if(hopPhase==HopPhase.SETTLE){
            if(++hopTicks>=30){
                hopPhase=HopPhase.NONE;hopOriginWorld=null;inSewer=false;
                if(hasOtherListedPlayerInWatchArea(p,w))beginServerHop(p,false);
                else beginSewerRun(p,w);
            }
        }
    }

    private void stop(String why){
        running=false;stage=Stage.IDLE;status=why;hopPhase=HopPhase.NONE;hopOriginWorld=null;
        deathRestartPending=false;inSewer=false;movement.stop();
        chestGuiOpenIntercepted=false;closeWindowId=-1;closeAttempts=0;
        if(future!=null)future.cancel(true);future=null;chest=null;resetGateState();
        lootTimer.reset();lootSlotCursor=0;
        restoreScaffoldState(mc.thePlayer);scaffoldPlan=null;scaffoldIndex=0;
    }
    private void tickRoute(EntityPlayerSP p,World w){
        if(stage==Stage.PATROL&&++patrolScanTicks>=GeodeConfig.scanIntervalTicks){
            patrolScanTicks=0;
            BlockPos discovered=findNearestLoadedChest(p,w);
            if(discovered!=null){
                emptyScans=0;chest=discovered;stage=Stage.TO_CHEST;
                status="Chest found while patrolling: "+discovered;
                movement.stop();routeChest(p,w,discovered);
                return;
            }
        }
        if(future!=null){movement.stop();return;}
        if(!movement.hasPath()){if(ticks>=retryTicks)reRoute(p,w);return;}
        movement.tick(p);
        if(movement.stuck()){
            movement.stop();
            if(p.isInWater()&&stage!=Stage.WATER_RECOVERY){
                beginWaterRecovery(p,w);
            }else if(stage==Stage.WATER_RECOVERY){
                waterRecoveryRadius=Math.min(16,waterRecoveryRadius+4);
                routeWaterRecovery(p,w);
            }else if(stage==Stage.PATROL){
                skipPatrolWaypoint("Patrol waypoint blocked; advancing to the next spiral point");
            }else if(stage==Stage.TO_SCAFFOLD){
                failScaffold(p,w,"Could not reach the scaffold staging position");
            }else{
                retryTicks=ticks+5;status="Blocked - recalculating path";
            }
            return;
        }
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
        }else if(stage==Stage.TO_SCAFFOLD){
            beginScaffold(p);
        }else if(stage==Stage.TO_CHEST){
            if(chest!=null&&p.getDistance(chest.x+.5D,chest.y+.5D,chest.z+.5D)>7.0D){
                status="Reached chest transit waypoint; continuing toward "+chest;
                routeChest(p,w,chest);
            }else{
                stage=Stage.OPEN;openAttempts=0;retryTicks=ticks;
                chestAimStableTicks=0;chestAimMissTicks=0;chestAimTicks=0;
            }
        }
        else if(stage==Stage.PATROL){
            if(patrolTransit){
                patrolTransit=false;
                status="Patrol transit point reached; continuing the same expanding leg";
                routePatrol(p,w);
            }else{
                patrolLeg++;patrolFailures=0;patrolWaypoint=null;
                stage=Stage.SEEK;scanTicks=GeodeConfig.scanIntervalTicks;
                status="Patrol point reached; scanning the newly loaded area";
            }
        }else if(stage==Stage.WATER_RECOVERY){
            if(waterResumeStage==Stage.TO_CHEST&&chest!=null){
                stage=Stage.TO_CHEST;routeChest(p,w,chest);
            }else if(waterResumeStage==Stage.TO_SCAFFOLD&&chest!=null){
                stage=Stage.TO_SCAFFOLD;routeScaffoldStaging(p,w);
            }else if(waterResumeStage==Stage.PATROL){
                stage=Stage.PATROL;routePatrol(p,w);
            }else{
                stage=Stage.SEEK;scanTicks=GeodeConfig.scanIntervalTicks;
                status="Escaped water; resuming chest search";
                findChest(p,w);
            }
        }
    }
    private void reRoute(EntityPlayerSP p,World w){
        if(stage==Stage.WATER_RECOVERY)routeWaterRecovery(p,w);
        else if(stage==Stage.TO_LAUNCH)routeLaunch(p,w);
        else if(stage==Stage.TO_GATE)routeGate(p,w);
        else if(stage==Stage.TO_CHEST&&chest!=null)routeChest(p,w,chest);
        else if(stage==Stage.TO_SCAFFOLD&&chest!=null)routeScaffoldStaging(p,w);
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
        ChestScaffoldPlan plan=ChestScaffoldPlan.forChest(c);
        if(plan!=null&&!c.equals(scaffoldedChest)&&!failedScaffoldChests.contains(c)){
            scaffoldPlan=plan;
            stage=Stage.TO_SCAFFOLD;
            status="Pathfinding to scaffold base "+plan.getStagingFeet();
            routeScaffoldStaging(p,w);
            return;
        }
        routeChestDirect(p,w,c);
    }

    private void routeScaffoldStaging(EntityPlayerSP p,World w){
        if(scaffoldPlan==null||chest==null){failScaffold(p,w,"Scaffold plan was lost");return;}
        route(p,w,Collections.singletonList(scaffoldPlan.getStagingFeet()),"chest scaffold staging");
    }

    private void routeChestDirect(EntityPlayerSP p,World w,BlockPos c){
        int y=MathHelper.floor_double(p.getEntityBoundingBox().minY);
        BlockPos waypoint=ChestRoutePlanner.nextWaypoint(p.posX,p.posZ,c,56,y);
        if(waypoint!=null){
            status="Routing toward distant chest via waypoint "+waypoint;
            route(p,w,box(waypoint.x-3,waypoint.x+3,y-4,y+4,waypoint.z-3,waypoint.z+3),
                    "chest "+c+" transit");
            return;
        }
        if(AIMED_CHEST.equals(c)){
            status="Precise approach to chest "+c+" at (99.0, 58.0, 47.5)";
            routeExact(p,w,PathNode.atWorld(99.0D,58.0D,47.5D),"chest "+c+" precision approach");
            return;
        }
        int approachRadius=c.equals(scaffoldedChest)?4:3;
        route(p,w,ChestApproachPlanner.candidates(c,approachRadius,3),"chest "+c);
    }
    private void routePatrol(EntityPlayerSP p,World w){
        int y=MathHelper.floor_double(p.getEntityBoundingBox().minY);
        if(patrolOrigin==null)patrolOrigin=new BlockPos(GATE.x,y,GATE.z);
        patrolWaypoint=PatrolPlanner.waypoint(patrolOrigin,patrolLeg,y);

        // Patrol legs grow without a configured scan-radius boundary. Long legs are
        // split into local A* segments so the 128-block collision snapshot never clips
        // either endpoint and the player can keep loading chunks while travelling.
        BlockPos transit=ChestRoutePlanner.nextWaypoint(p.posX,p.posZ,patrolWaypoint,56,y);
        patrolTransit=transit!=null;
        BlockPos destination=patrolTransit?transit:patrolWaypoint;
        if(patrolTransit)status="Planning patrol leg "+patrolLeg+" via "+destination+" toward "+patrolWaypoint;
        else status="Planning expanding patrol leg "+patrolLeg+" toward "+patrolWaypoint;
        route(p,w,box(destination.x-2,destination.x+2,y-3,y+3,destination.z-2,destination.z+2),"Sewer patrol");
    }

    private void skipPatrolWaypoint(String reason){
        patrolLeg++;
        patrolFailures++;
        patrolWaypoint=null;
        patrolTransit=false;
        retryTicks=ticks+5;
        status=reason+" (leg "+patrolLeg+")";
        if(patrolFailures>=12){
            patrolFailures=0;
            retryTicks=ticks+100;
            status="12 patrol waypoints were unreachable; pausing briefly before continuing the unbounded spiral";
        }
    }
    private void beginWaterRecovery(EntityPlayerSP p,World w){
        waterResumeStage=stage;
        waterRecoveryRadius=8;
        stage=Stage.WATER_RECOVERY;
        status="Stuck in water; finding a dry escape route";
        routeWaterRecovery(p,w);
    }

    private void routeWaterRecovery(EntityPlayerSP p,World w){
        int x=MathHelper.floor_double(p.posX);
        int y=MathHelper.floor_double(p.getEntityBoundingBox().minY);
        int z=MathHelper.floor_double(p.posZ);
        List<BlockPos> dryCandidates=box(x-waterRecoveryRadius,x+waterRecoveryRadius,
                y-4,y+5,z-waterRecoveryRadius,z+waterRecoveryRadius);
        status="Searching for dry footing (radius "+waterRecoveryRadius+")";
        route(p,w,dryCandidates,"Water escape");
    }

    private void addGoalVariants(MinecraftPathWorld snap,List<PathNode> pathGoals,
                                 BlockPos pos,boolean requireDry){
        for(int half=0;half<=1;half++){
            int y2=pos.y*2+half;
            for(int lane=0;lane<PathNode.LANE_COUNT;lane++){
                PathNode candidate=new PathNode(pos.x,y2,pos.z,lane);
                if(!snap.canStand(candidate))continue;
                if(requireDry&&snap.liquid(candidate))continue;
                pathGoals.add(candidate);
            }
        }
    }

    private void route(EntityPlayerSP p,World w,List<BlockPos> rough,String why){
        route(p,w,rough,why,null);
    }

    private void routeExact(EntityPlayerSP p,World w,PathNode exactGoal,String why){
        route(p,w,Collections.singletonList(exactGoal.blockPos()),why,Collections.singletonList(exactGoal));
    }

    private void route(EntityPlayerSP p,World w,List<BlockPos> rough,String why,List<PathNode> exactGoals){
        if(future!=null)future.cancel(true);movement.stop();
        BlockPos start=playerPos(p);
        PathNode requestedStart=PathNode.atPlayer(p.posX,p.getEntityBoundingBox().minY,p.posZ);
        lastStart=start;
        List<PathNode> captureGoals=new ArrayList<PathNode>();
        if(exactGoals!=null)captureGoals.addAll(exactGoals);
        else for(BlockPos point:rough)captureGoals.add(PathNode.fromBlockPos(point));
        MinecraftPathWorld snap=MinecraftPathWorld.capture(w,p,requestedStart,captureGoals);
        PathNode resolvedStart=requestedStart;
        if(!snap.canStand(resolvedStart)){
            double nearest=Double.POSITIVE_INFINITY;
            for(int lane=0;lane<PathNode.LANE_COUNT;lane++){
                PathNode candidate=new PathNode(requestedStart.x,requestedStart.y2,requestedStart.z,lane);
                if(!snap.canStand(candidate))continue;
                double dx=candidate.worldX()-p.posX,dz=candidate.worldZ()-p.posZ;
                double distance=dx*dx+dz*dz;
                if(distance<nearest){nearest=distance;resolvedStart=candidate;}
            }
        }
        final PathNode pathStart=resolvedStart;
        List<BlockPos> goals=new ArrayList<BlockPos>();
        List<PathNode> pathGoals=new ArrayList<PathNode>();
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
                if(failedGateApproaches.contains(approach))continue;
                int before=pathGoals.size();
                addGoalVariants(snap,pathGoals,approach,false);
                if(pathGoals.size()>before)goals.add(approach);
            }
            if(pathGoals.isEmpty()){
                failedGateApproaches.clear();
                for(BlockPos approach:plan.approaches){
                    int before=pathGoals.size();
                    addGoalVariants(snap,pathGoals,approach,false);
                    if(pathGoals.size()>before)goals.add(approach);
                }
            }
            goalCandidates=pathGoals.size();
            gateLanding=null;gateDropSource=null;gateCrossed=false;
            previousGateFeet=Double.NaN;
        }else if(exactGoals!=null){
            for(PathNode goal:exactGoals){
                if(snap.canStand(goal)){
                    pathGoals.add(goal);
                    goals.add(goal.blockPos());
                }
            }
        }else{
            for(BlockPos g:rough){
                if("Sewer patrol".equals(why)){
                    long dx=(long)g.x-start.x,dz=(long)g.z-start.z;
                    // Never accept the player's current cell or a nearby cell as a
                    // patrol destination; that creates zero-distance patrol loops.
                    if(dx*dx+dz*dz<25L)continue;
                }
                int before=pathGoals.size();
                addGoalVariants(snap,pathGoals,g,"Water escape".equals(why));
                if(pathGoals.size()>before)goals.add(g);
            }
        }
        if(goals.isEmpty()&&"LaunchPad".equals(why)){
            for(int x=LAUNCH.x-1;x<=LAUNCH.x+1;x++)for(int y=LAUNCH.y-1;y<=LAUNCH.y+2;y++)
                for(int z=LAUNCH.z-1;z<=LAUNCH.z+1;z++){
                    BlockPos g=new BlockPos(x,y,z);
                    int before=pathGoals.size();
                    addGoalVariants(snap,pathGoals,g,false);
                    if(pathGoals.size()>before)goals.add(g);
                }
        }
        goalCandidates=pathGoals.size();
        if(pathGoals.isEmpty()){
            if(p.isInWater()&&!"Water escape".equals(why)){
                beginWaterRecovery(p,w);
                return;
            }else if("Water escape".equals(why)){
                waterRecoveryRadius=Math.min(16,waterRecoveryRadius+4);
                status="No dry route in loaded chunks; expanding water escape search";
                retryTicks=ticks+20;
            }else if("Sewer patrol".equals(why)){
                skipPatrolWaypoint("No loaded standable cells at this patrol waypoint");
            }else if("chest scaffold staging".equals(why)&&chest!=null){
                failScaffold(p,w,"Scaffold staging position is not reachable");
            }else if(why.startsWith("chest ")&&chest!=null){
                cooldown.put(chest,w.getTotalWorldTime()+200L);
                status="No safe approach cells for "+chest+"; skipping this chest";
                chest=null;stage=Stage.SEEK;scanTicks=GeodeConfig.scanIntervalTicks;
                retryTicks=ticks+5;
            }else{
                status="No standable goal cells for "+why+" near "+(rough.isEmpty()?"target":rough.get(0));
                retryTicks=ticks+40;
            }
            return;
        }
        if(!snap.canStand(pathStart)){
            if("Sewer patrol".equals(why)){
                skipPatrolWaypoint("Patrol start cell is not standable");
            }else if("chest scaffold staging".equals(why)&&chest!=null){
                failScaffold(p,w,"Current position is outside the scaffold path snapshot");
            }else{
                status="Start position is not standable in snapshot: "+start;
                retryTicks=ticks+20;
            }
            return;
        }
        purpose=why;status="Computing "+why+" path from "+start+" ("+goalCandidates+" goals)...";
        future=worker.submit(()->new Pathfinder().findNodes(snap,pathStart,pathGoals,GeodeConfig.maxExpandedNodes,GeodeConfig.maxFallBlocks));
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
                }else if(stage==Stage.PATROL){
                    status="Sewer patrol leg "+patrolLeg+" path ready ("+r.steps.size()+" steps) toward "+patrolWaypoint;
                }else if(stage==Stage.TO_SCAFFOLD){
                    status="Path to scaffold base ready ("+r.steps.size()+" steps)";
                }else{
                    status=purpose+" path ready ("+r.steps.size()+" steps)";
                }
                movement.setPath(r.steps,p);
            }else if(p.isInWater()&&stage!=Stage.WATER_RECOVERY){
                beginWaterRecovery(p,mc.theWorld);
            }else if(stage==Stage.PATROL){
                skipPatrolWaypoint("No path to patrol waypoint ("+r.expanded+" nodes)");
            }else if(stage==Stage.TO_SCAFFOLD&&chest!=null){
                failScaffold(p,mc.theWorld,"No path to scaffold staging position");
            }else if(stage==Stage.TO_CHEST&&chest!=null){
                cooldown.put(chest,mc.theWorld.getTotalWorldTime()+200L);
                status="No route to chest "+chest+" ("+r.expanded+" nodes); trying another chest";
                chest=null;stage=Stage.SEEK;scanTicks=GeodeConfig.scanIntervalTicks;
                retryTicks=ticks+5;
            }else if(stage==Stage.WATER_RECOVERY){
                waterRecoveryRadius=Math.min(16,waterRecoveryRadius+4);
                status="No dry escape path yet; expanding to radius "+waterRecoveryRadius;
                retryTicks=ticks+20;
            }else{
                status="No path to "+purpose+" ("+r.expanded+" nodes, "+goalCandidates+" goals, start "+lastStart+")";
                retryTicks=ticks+40;
            }
        }catch(Exception ex){
            if(stage==Stage.TO_SCAFFOLD&&chest!=null)failScaffold(p,mc.theWorld,"Scaffold path search error: "+ex.getClass().getSimpleName());
            else{status="Path search error: "+ex.getClass().getSimpleName();retryTicks=ticks+30;}
        }
    }
    private void beginScaffold(EntityPlayerSP p){
        movement.stop();
        if(scaffoldPlan==null||chest==null){failScaffold(p,mc.theWorld,"No scaffold plan is active");return;}
        BlockPos base=scaffoldPlan.getStagingFeet();
        double dx=p.posX-(base.x+.5D),dz=p.posZ-(base.z+.5D);
        if(dx*dx+dz*dz>0.8D*0.8D||!p.onGround){
            failScaffold(p,mc.theWorld,"Player did not arrive at the scaffold base safely");
            return;
        }
        int availableBlocks=countScaffoldBlocks(p);
        if(availableBlocks<scaffoldPlan.getPlacementCount()){
            failScaffold(p,mc.theWorld,"Need 7 solid blocks in the hotbar, but only found "+availableBlocks);
            return;
        }
        scaffoldOriginalSlot=p.inventory.currentItem;
        scaffoldOriginalPitch=p.rotationPitch;
        scaffoldStateSaved=true;
        scaffoldIndex=0;scaffoldTicks=0;scaffoldAttempts=0;
        scaffoldJumpIssued=false;scaffoldJumpKeyHeld=false;scaffoldAirborneSeen=false;
        scaffoldPlaceRequested=false;scaffoldPlaced=false;
        stage=Stage.BUILD_SCAFFOLD;
        status="Building 7-block scaffold at "+base;
    }

    private void tickScaffold(EntityPlayerSP p,World w){
        if(scaffoldPlan==null||chest==null){failScaffold(p,w,"Scaffold state is missing");return;}
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(),false);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindBack.getKeyCode(),false);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindLeft.getKeyCode(),false);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindRight.getKeyCode(),false);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(),false);
        if(scaffoldJumpKeyHeld){
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(),false);
            scaffoldJumpKeyHeld=false;
        }
        p.rotationPitch=84.0F;

        if(scaffoldIndex>=scaffoldPlan.getPlacementCount()){
            completeScaffold(p,w);
            return;
        }

        BlockPos target=scaffoldPlan.getPlacedBlock(scaffoldIndex);
        BlockPos support=scaffoldPlan.getSupportBlock(scaffoldIndex);
        net.minecraft.util.BlockPos targetMc=new net.minecraft.util.BlockPos(target.x,target.y,target.z);
        net.minecraft.util.BlockPos supportMc=new net.minecraft.util.BlockPos(support.x,support.y,support.z);
        scaffoldTicks++;

        if(scaffoldPlaceRequested&&!w.isAirBlock(targetMc))scaffoldPlaced=true;

        if(scaffoldPlaced){
            double feet=p.getEntityBoundingBox().minY;
            if(p.onGround&&feet>=target.y+0.75D){
                scaffoldIndex++;
                scaffoldTicks=0;scaffoldAttempts=0;
                scaffoldJumpIssued=false;scaffoldAirborneSeen=false;
                scaffoldPlaceRequested=false;scaffoldPlaced=false;
                status="Scaffold placement "+scaffoldIndex+"/7 confirmed";
                if(scaffoldIndex>=scaffoldPlan.getPlacementCount())completeScaffold(p,w);
            }
            return;
        }

        if(scaffoldPlaceRequested&&scaffoldTicks-scaffoldPlaceTick>8&&w.isAirBlock(targetMc)){
            scaffoldPlaceRequested=false;
        }

        if(!w.isAirBlock(targetMc)){
            failScaffold(p,w,"Scaffold target is occupied: "+target);
            return;
        }
        if(!w.isBlockLoaded(supportMc,false)||!w.isSideSolid(supportMc,EnumFacing.UP,false)){
            failScaffold(p,w,"No solid support beneath scaffold block "+target);
            return;
        }

        if(!p.onGround){
            scaffoldAirborneSeen=true;
            if(!scaffoldPlaceRequested&&p.getEntityBoundingBox().minY>=target.y+1.02D){
                ItemStack held=selectScaffoldBlock(p);
                if(held==null){
                    failScaffold(p,w,"No usable solid blocks in the hotbar (need 7 total)");
                    return;
                }
                boolean sent=mc.playerController.onPlayerRightClick(p,mc.theWorld,held,supportMc,EnumFacing.UP,
                        new Vec3(support.x+0.5D,support.y+1.0D,support.z+0.5D));
                if(sent){
                    p.swingItem();
                    scaffoldPlaceRequested=true;
                    scaffoldPlaceTick=scaffoldTicks;
                    status="Placing scaffold block "+(scaffoldIndex+1)+"/7 at "+target;
                }
            }
            if(scaffoldTicks>45&&!scaffoldPlaced){
                failScaffold(p,w,"Could not complete the jump for scaffold block "+(scaffoldIndex+1));
            }
            return;
        }

        if(scaffoldAirborneSeen&&!scaffoldPlaced&&!scaffoldPlaceRequested){
            scaffoldJumpIssued=false;
            scaffoldAirborneSeen=false;
            if(++scaffoldAttempts>=3){
                failScaffold(p,w,"Failed to place scaffold block at "+target);
                return;
            }
        }

        if(!scaffoldJumpIssued){
            if(selectScaffoldBlock(p)==null){
                failScaffold(p,w,"No usable solid blocks in the hotbar (need 7 total)");
                return;
            }
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(),true);
            scaffoldJumpKeyHeld=true;
            scaffoldJumpIssued=true;
            scaffoldTicks=0;
        }
        if(scaffoldTicks>60)failScaffold(p,w,"Scaffold jump timed out at "+target);
    }

    private boolean isScaffoldBlock(ItemStack stack){
        if(stack==null||stack.stackSize<=0||!(stack.getItem() instanceof ItemBlock))return false;
        Block block=((ItemBlock)stack.getItem()).getBlock();
        return block.getMaterial().isSolid()&&!(block instanceof BlockFalling);
    }

    private int countScaffoldBlocks(EntityPlayerSP p){
        int count=0;
        for(int slot=0;slot<9;slot++){
            ItemStack stack=p.inventory.mainInventory[slot];
            if(isScaffoldBlock(stack))count+=stack.stackSize;
        }
        return count;
    }

    private ItemStack selectScaffoldBlock(EntityPlayerSP p){
        for(int slot=0;slot<9;slot++){
            ItemStack stack=p.inventory.mainInventory[slot];
            if(!isScaffoldBlock(stack))continue;
            if(p.inventory.currentItem!=slot){
                p.inventory.currentItem=slot;
                mc.playerController.updateController();
            }
            return stack;
        }
        return null;
    }

    private void completeScaffold(EntityPlayerSP p,World w){
        if(chest==null||scaffoldPlan==null)return;
        restoreScaffoldState(p);
        scaffoldedChest=chest;
        BlockPos top=scaffoldPlan.getTopStandingPosition();
        status="Scaffold complete: 7 blocks placed; pathfinding from "+top+" to "+chest;
        scaffoldPlan=null;
        stage=Stage.TO_CHEST;
        routeChestDirect(p,w,chest);
    }

    private void failScaffold(EntityPlayerSP p,World w,String reason){
        restoreScaffoldState(p);
        if(chest!=null)failedScaffoldChests.add(chest);
        scaffoldPlan=null;scaffoldIndex=0;
        stage=Stage.TO_CHEST;
        status=reason+"; trying ordinary chest pathfinding";
        if(chest!=null&&w!=null)routeChestDirect(p,w,chest);
        else{stage=Stage.SEEK;scanTicks=GeodeConfig.scanIntervalTicks;}
    }

    private void restoreScaffoldState(EntityPlayerSP p){
        if(mc!=null&&mc.gameSettings!=null){
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(),false);
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(),false);
        }
        if(p!=null&&scaffoldStateSaved){
            if(scaffoldOriginalSlot>=0&&scaffoldOriginalSlot<9)p.inventory.currentItem=scaffoldOriginalSlot;
            p.rotationPitch=scaffoldOriginalPitch;
            if(mc!=null&&mc.playerController!=null)mc.playerController.updateController();
        }
        scaffoldOriginalSlot=-1;scaffoldStateSaved=false;
        scaffoldJumpIssued=false;scaffoldJumpKeyHeld=false;scaffoldAirborneSeen=false;
        scaffoldPlaceRequested=false;scaffoldPlaced=false;scaffoldTicks=0;scaffoldAttempts=0;
    }

    private void findChest(EntityPlayerSP p,World w){
        BlockPos best=findNearestLoadedChest(p,w);
        if(best!=null){
            emptyScans=0;chest=best;stage=Stage.TO_CHEST;
            status="Chest found: "+best;
            routeChest(p,w,best);
        }else if(++emptyScans>=5){
            emptyScans=0;stage=Stage.PATROL;patrolScanTicks=0;routePatrol(p,w);
        }else status="No eligible chest in loaded chunks; scanning again";
    }

    /**
     * Searches every chest tile entity in client-loaded chunks, without a horizontal
     * distance or Y restriction. A chest is considered a Sewer chest only when a
     * all loaded chest tile entities are eligible unless they are in the excluded area.
     */
    private BlockPos findNearestLoadedChest(EntityPlayerSP p,World w){
        BlockPos best=null;
        double bestDistance=Double.POSITIVE_INFINITY;
        long now=w.getTotalWorldTime();
        loadedChestTiles=0;excludedChestTiles=0;
        for(Object o:w.loadedTileEntityList)if(o instanceof TileEntityChest){
            loadedChestTiles++;
            net.minecraft.util.BlockPos pos=((TileEntityChest)o).getPos();
            if(isExcludedChest(pos.getX(),pos.getY(),pos.getZ())){
                excludedChestTiles++;
                continue;
            }
            BlockPos candidate=new BlockPos(pos.getX(),pos.getY(),pos.getZ());
            Long until=cooldown.get(candidate);
            if(until!=null&&until>now)continue;
            double distance=candidate.distanceSquared(p.posX,p.getEntityBoundingBox().minY,p.posZ);
            if(distance<bestDistance){bestDistance=distance;best=candidate;}
        }
        return best;
    }
    private boolean isExcludedChest(int x,int y,int z){
        return ChestExclusion.isExcluded(x,y,z);
    }

    private void openChest(EntityPlayerSP p,World w){
        if(chest==null){stage=Stage.SEEK;return;}
        net.minecraft.util.BlockPos bp=new net.minecraft.util.BlockPos(chest.x,chest.y,chest.z);
        if(!w.isBlockLoaded(bp,false)||(w.getBlockState(bp).getBlock()!=net.minecraft.init.Blocks.chest
                &&w.getBlockState(bp).getBlock()!=net.minecraft.init.Blocks.trapped_chest)){
            cooldown.put(chest,w.getTotalWorldTime()+100L);chest=null;stage=Stage.SEEK;return;
        }
        if(isContainerGuiOpen(p)){
            stage=Stage.LOOT;lootTimer.reset();lootSlotCursor=0;lootEmptyTicks=0;failedClicks=0;
            closeWindowId=currentContainerWindowId(p);closeAttempts=0;
            retryTicks=chestGuiOpenIntercepted?ticks:ticks+3;
            chestGuiOpenIntercepted=false;
            status="Chest container opened; closing server window";
            return;
        }
        if(p.getDistance(chest.x+.5D,chest.y+.5D,chest.z+.5D)>5D){stage=Stage.TO_CHEST;routeChest(p,w,chest);return;}
        tickChestOpen(p,bp);
    }

    /**
     * Opens every chest through Minecraft's normal crosshair right-click pipeline.
     * The old generic path sent a fixed UP-face click at the block centre without
     * proving that the chest was actually under the crosshair.
     */
    private void tickChestOpen(EntityPlayerSP p,net.minecraft.util.BlockPos bp){
        chestAimTicks++;
        if(openAttempts>=3&&ticks>=retryTicks){
            cooldown.put(chest,mc.theWorld.getTotalWorldTime()+200L);
            status="Chest did not open after 3 vanilla right-click attempts; skipping";
            chest=null;stage=Stage.SEEK;scanTicks=GeodeConfig.scanIntervalTicks;
            return;
        }
        if(chestAimTicks>120&&openAttempts==0){
            cooldown.put(chest,mc.theWorld.getTotalWorldTime()+200L);
            status="No clear crosshair line to chest "+chest+"; skipping";
            chest=null;stage=Stage.SEEK;scanTicks=GeodeConfig.scanIntervalTicks;
            return;
        }
        if(ticks<retryTicks)return;

        ChestAimTarget target=findChestAim(p,bp);
        if(target==null){
            chestAimStableTicks=0;
            if(++chestAimMissTicks>=10){
                chestAimMissTicks=0;
                status="Searching for a visible chest face";
            }
            return;
        }
        float yawDifference=MathHelper.wrapAngleTo180_float(target.yaw-p.rotationYaw);
        float pitchDifference=target.pitch-p.rotationPitch;
        p.rotationYaw+=MathHelper.clamp_float(yawDifference,-10.0F,10.0F);
        p.rotationPitch+=MathHelper.clamp_float(pitchDifference,-8.0F,8.0F);
        p.rotationYawHead=p.rotationYaw;
        if(Math.abs(yawDifference)>2.0F||Math.abs(pitchDifference)>2.0F){
            chestAimStableTicks=0;
            return;
        }
        if(++chestAimStableTicks<2)return;

        MovingObjectPosition hit=p.rayTrace(5.0D,1.0F);
        if(!isTargetChestHit(hit,bp)){
            chestAimStableTicks=0;
            if(++chestAimMissTicks>=10){
                chestAimMissTicks=0;
                status="Crosshair ray missed chest; adjusting aim";
            }
            return;
        }

        if(invokeVanillaRightClick(hit)){
            openAttempts++;
            retryTicks=ticks+40;
            status="Vanilla right-click sent; waiting for server window (attempt "+openAttempts+"/3)";
        }else{
            status="Could not invoke Minecraft's vanilla right-click method";
            retryTicks=ticks+20;
        }
        chestAimStableTicks=0;chestAimMissTicks=0;
    }

    private ChestAimTarget findChestAim(EntityPlayerSP p,net.minecraft.util.BlockPos bp){
        float savedYaw=p.rotationYaw,savedPitch=p.rotationPitch;
        // Samples lie just inside the chest's six visible faces. The ray trace decides
        // which face is actually reachable, including around walls and double chests.
        double[][] samples={
                {0.50D,0.20D,0.07D},{0.25D,0.45D,0.07D},{0.75D,0.45D,0.07D},
                {0.50D,0.20D,0.93D},{0.25D,0.45D,0.93D},{0.75D,0.45D,0.93D},
                {0.07D,0.20D,0.50D},{0.07D,0.45D,0.25D},{0.07D,0.45D,0.75D},
                {0.93D,0.20D,0.50D},{0.93D,0.45D,0.25D},{0.93D,0.45D,0.75D},
                {0.25D,0.86D,0.25D},{0.50D,0.86D,0.50D},{0.75D,0.86D,0.75D}
        };
        ChestAimTarget found=null;
        ChestAimTarget nearestFaceFallback=null;
        double nearestDistance=Double.POSITIVE_INFINITY;
        for(double[] sample:samples){
            double tx=bp.getX()+sample[0],ty=bp.getY()+sample[1],tz=bp.getZ()+sample[2];
            float yaw=ChestAimMath.yaw(p.posX,p.posZ,tx,tz);
            float pitch=ChestAimMath.pitch(p.posX,p.posY+p.getEyeHeight(),p.posZ,tx,ty,tz);
            double ddx=tx-p.posX,ddy=ty-(p.posY+p.getEyeHeight()),ddz=tz-p.posZ;
            double distance=ddx*ddx+ddy*ddy+ddz*ddz;
            if(distance<nearestDistance){
                nearestDistance=distance;
                nearestFaceFallback=new ChestAimTarget(yaw,pitch);
            }
            p.rotationYaw=yaw;p.rotationPitch=pitch;
            if(isTargetChestHit(p.rayTrace(5.0D,1.0F),bp)){
                found=new ChestAimTarget(yaw,pitch);
                break;
            }
        }
        p.rotationYaw=savedYaw;p.rotationPitch=savedPitch;
        // A blocked ray must not prevent aiming. The final ray check below still
        // prevents interaction until the crosshair actually hits this chest.
        return found!=null?found:nearestFaceFallback;
    }

    private boolean invokeVanillaRightClick(MovingObjectPosition hit){
        if(hit==null||hit.typeOfHit!=MovingObjectPosition.MovingObjectType.BLOCK
                ||mc.thePlayer==null||mc.theWorld==null||mc.playerController==null)return false;
        boolean sent=mc.playerController.onPlayerRightClick(mc.thePlayer,mc.theWorld,
                mc.thePlayer.getHeldItem(),hit.getBlockPos(),hit.sideHit,hit.hitVec);
        if(sent)mc.thePlayer.swingItem();
        return sent;
    }

    private boolean isTargetChestHit(MovingObjectPosition hit,net.minecraft.util.BlockPos bp){
        return hit!=null&&hit.typeOfHit==MovingObjectPosition.MovingObjectType.BLOCK
                &&bp.equals(hit.getBlockPos());
    }

    private static final class ChestAimTarget{
        final float yaw,pitch;
        ChestAimTarget(float yaw,float pitch){this.yaw=yaw;this.pitch=pitch;}
    }
    private boolean isContainerGuiOpen(EntityPlayerSP p){
        return mc.currentScreen instanceof GuiContainer
                ||(p!=null&&p.openContainer!=null&&p.inventoryContainer!=null
                &&p.openContainer!=p.inventoryContainer);
    }

    private int currentContainerWindowId(EntityPlayerSP p){
        if(p!=null&&p.openContainer!=null&&p.inventoryContainer!=null
                &&p.openContainer!=p.inventoryContainer)return p.openContainer.windowId;
        if(mc.currentScreen instanceof GuiContainer){
            return ((GuiContainer)mc.currentScreen).inventorySlots.windowId;
        }
        return closeWindowId;
    }

    /**
     * Use the exact vanilla 1.8.9 closeScreen path used by PitAutoEnchanter:
     * it sends C0D(windowId), clears the carried stack, resets the local container,
     * and removes the GUI. Retain the window id for bounded packet retries.
     */
    private boolean closeActiveContainer(EntityPlayerSP p){
        if(p==null)return false;
        boolean guiOpen=mc.currentScreen instanceof GuiContainer;
        boolean containerOpen=p.openContainer!=null&&p.inventoryContainer!=null
                &&p.openContainer!=p.inventoryContainer&&p.openContainer.windowId>0;
        if(!guiOpen&&!containerOpen)return false;

        if(containerOpen){
            closeWindowId=p.openContainer.windowId;
            // IMPORTANT: closeScreen() sends exactly one C0D packet and resets the local
            // player container. 1.8.9's server processCloseWindow closes its current
            // container without validating the packet window id, so never send a duplicate.
            p.closeScreen();
        }else if(guiOpen){
            // No active server window exists; remove only the orphaned local screen.
            mc.displayGuiScreen(null);
        }
        return true;
    }

    private void lootChest(EntityPlayerSP p){
        // One server close request per opened window. Never replay a stale C0D:
        // the 1.8.9 server handler closes its current container regardless of id.
        if(ticks<retryTicks)return;

        if(isContainerGuiOpen(p)){
            int activeId=currentContainerWindowId(p);
            if(activeId>0&&activeId!=closeWindowId){
                closeActiveContainer(p);
                closeAttempts++;
                retryTicks=ticks+4;
                status="Closed newly opened chest window "+activeId+" (single request)";
            }else{
                // A window id already received its one close request. Do not send it again.
                status="Waiting for chest container to close; no duplicate packet sent";
                retryTicks=ticks+10;
            }
            return;
        }

        finish(false);
        closeWindowId=-1;
        closeAttempts=0;
        chestGuiOpenIntercepted=false;
    }
    private void finish(boolean emptied){
        if(chest!=null&&mc.theWorld!=null)cooldown.put(chest,mc.theWorld.getTotalWorldTime()+1200L);
        chest=null;stage=Stage.SEEK;scanTicks=GeodeConfig.scanIntervalTicks;lootSlotCursor=0;lootEmptyTicks=0;lootTimer.reset();
        closeWindowId=-1;closeAttempts=0;chestGuiOpenIntercepted=false;
        status=emptied?"Chest emptied and GUI closed; searching for next":"Chest GUI closed; searching for next";
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
                gateCrossed=true;inSewer=true;
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
            stage=Stage.SEEK;inSewer=true;
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
