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
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;
import java.util.*;
import java.util.concurrent.*;

public final class GeodeClient {
    public static final KeyBinding TOGGLE_KEY=new KeyBinding("Toggle Geode",Keyboard.KEY_G,"Geode");
    private static final BlockPos LAUNCH=new BlockPos(16,95,16);
    private static final BlockPos GATE=new BlockPos(47,66,75);
    private enum Stage { IDLE,TO_LAUNCH,WAIT_LAUNCH,TO_GATE,SEEK,PATROL,TO_CHEST,OPEN,LOOT }
    private final Minecraft mc=Minecraft.getMinecraft();
    private final MovementController movement=new MovementController();
    private final ExecutorService worker=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"Geode-AStar");t.setDaemon(true);return t;});
    private Future<PathResult> future;
    private Stage stage=Stage.IDLE;
    private boolean running;
    private String status="Stopped",purpose="route";
    private BlockPos chest;
    private int ticks,waitTicks,scanTicks,emptyScans,retryTicks,openAttempts,lootTicks,emptySlots,failedClicks,patrol;
    private double launchY,launchX,launchZ;
    private final Map<BlockPos,Long> cooldown=new HashMap<BlockPos,Long>();

    @SubscribeEvent public void onTick(TickEvent.ClientTickEvent e) {
        if(e.phase!=TickEvent.Phase.END)return;
        ticks++;
        while(TOGGLE_KEY.isPressed()) toggle();
        if(!running)return;
        EntityPlayerSP p=mc.thePlayer; World w=mc.theWorld;
        if(p==null||w==null){stop("Disconnected");return;}
        if(!GeodeConfig.isAllowed(mc.getCurrentServerData()==null?"":mc.getCurrentServerData().serverIP,mc.isSingleplayer())) {
            stop("Server is not allowlisted"); tell("Stopped: add the exact host to config/geode.cfg > security.allowedServers."); return;
        }
        collectResult(p);
        switch(stage) {
            case TO_LAUNCH: case TO_GATE: case TO_CHEST: case PATROL: tickRoute(p,w); break;
            case WAIT_LAUNCH:
                movement.stop(); waitTicks++;
                if(p.posY<launchY-5D || (sq(p.posX-launchX)+sq(p.posZ-launchZ)>64D) || waitTicks>200) {
                    stage=Stage.TO_GATE; status="Pathfinding through Sewer iron-bar opening"; routeGate(p,w);
                }
                break;
            case SEEK:
                movement.stop(); scanTicks++;
                if(scanTicks>=GeodeConfig.scanIntervalTicks){scanTicks=0;findChest(p,w);}
                break;
            case OPEN: movement.stop(); openChest(p,w); break;
            case LOOT: lootChest(p); break;
            default: movement.stop();
        }
    }

    @SubscribeEvent public void onChat(ClientChatReceivedEvent e) {
        if(running&&e.message!=null&&e.message.getUnformattedText().toLowerCase(Locale.ROOT).contains("a new treasure spawned somewhere")) {
            scanTicks=GeodeConfig.scanIntervalTicks;
            status="Treasure message received - scanning loaded chunks";
        }
    }
    @SubscribeEvent public void onHud(RenderGameOverlayEvent.Post e) {
        if(e.type!=RenderGameOverlayEvent.ElementType.ALL||mc.fontRendererObj==null)return;
        mc.fontRendererObj.drawStringWithShadow("Geode "+(running?"ON":"OFF")+" | "+stage,7,7,running?0x55FF55:0xAAAAAA);
        mc.fontRendererObj.drawStringWithShadow(status,7,18,0xFFFFFF);
        mc.fontRendererObj.drawStringWithShadow(chest==null?"Chest: scanning loaded chunks":"Chest: "+chest,7,29,0x55FFFF);
        mc.fontRendererObj.drawStringWithShadow("G: toggle",7,40,0xAAAAAA);
    }

    private void toggle(){if(running){stop("Stopped by user");tell("Geode stopped.");}else start();}
    private void start(){
        EntityPlayerSP p=mc.thePlayer; if(p==null||mc.theWorld==null){tell("Join the remake server first.");return;}
        if(!GeodeConfig.isAllowed(mc.getCurrentServerData()==null?"":mc.getCurrentServerData().serverIP,mc.isSingleplayer())) {
            tell("This host is blocked. Add its exact hostname to config/geode.cfg.");return;
        }
        running=true; stage=Stage.TO_LAUNCH; launchX=p.posX;launchY=p.posY;launchZ=p.posZ;
        cooldown.clear();chest=null;emptyScans=0;patrol=0;
        tell("Geode started. Pathfinding to (16, 95, 16).");routeLaunch(p,mc.theWorld);
    }
    private void stop(String why){running=false;stage=Stage.IDLE;status=why;movement.stop();if(future!=null)future.cancel(true);future=null;chest=null;}
    private void tickRoute(EntityPlayerSP p,World w){
        if(future!=null){movement.stop();return;}
        if(!movement.hasPath()){if(ticks>=retryTicks)reRoute(p,w);return;}
        movement.tick(p);
        if(movement.stuck()){movement.stop();retryTicks=ticks+5;status="Blocked - recalculating path";return;}
        if(movement.complete()){movement.stop();arrived(p,w);}
    }
    private void arrived(EntityPlayerSP p,World w){
        if(stage==Stage.TO_LAUNCH){launchX=p.posX;launchY=p.posY;launchZ=p.posZ;waitTicks=0;stage=Stage.WAIT_LAUNCH;status="At LaunchPad - waiting for launch";}
        else if(stage==Stage.TO_GATE){if(inSewer(p)){stage=Stage.SEEK;scanTicks=GeodeConfig.scanIntervalTicks;tell("Sewer reached. Searching for existing chests.");findChest(p,w);}else{retryTicks=ticks+10;status="Refining route to iron-bar gap";}}
        else if(stage==Stage.TO_CHEST){stage=Stage.OPEN;openAttempts=0;retryTicks=ticks;}
        else if(stage==Stage.PATROL){stage=Stage.SEEK;scanTicks=GeodeConfig.scanIntervalTicks;status="Patrol point reached - scanning again";}
    }
    private void reRoute(EntityPlayerSP p,World w){
        if(stage==Stage.TO_LAUNCH)routeLaunch(p,w);
        else if(stage==Stage.TO_GATE)routeGate(p,w);
        else if(stage==Stage.TO_CHEST&&chest!=null)routeChest(p,w,chest);
        else if(stage==Stage.PATROL)routePatrol(p,w);
    }
    private void routeLaunch(EntityPlayerSP p,World w){route(p,w,box(14,18,94,96,14,18),"LaunchPad");}
    private void routeGate(EntityPlayerSP p,World w){route(p,w,box(44,50,54,65,72,78),"Sewer opening");}
    private void routeChest(EntityPlayerSP p,World w,BlockPos c){
        List<BlockPos> g=Arrays.asList(c.add(1,0,0),c.add(-1,0,0),c.add(0,0,1),c.add(0,0,-1),
                c.add(1,-1,0),c.add(-1,-1,0),c.add(0,-1,1),c.add(0,-1,-1));
        route(p,w,g,"chest "+c);
    }
    private void routePatrol(EntityPlayerSP p,World w){
        int[][] ring={{0,-12},{8,-8},{12,0},{8,8},{0,12},{-8,8},{-12,0},{-8,-8}};
        int[] o=ring[patrol++%ring.length];int x=GATE.x+o[0],z=GATE.z+o[1],y=MathHelper.floor_double(p.getEntityBoundingBox().minY);
        route(p,w,box(x-2,x+2,y-3,y+3,z-2,z+2),"Sewer patrol");
    }
    private void route(EntityPlayerSP p,World w,List<BlockPos> rough,String why){
        if(future!=null)future.cancel(true);movement.stop();
        BlockPos start=playerPos(p);
        MinecraftPathWorld snap=MinecraftPathWorld.capture(w,p,start,rough);
        List<BlockPos> goals=new ArrayList<BlockPos>();
        for(BlockPos g:rough)if(snap.canStand(g))goals.add(g);
        if(goals.isEmpty()&&why.equals("Sewer opening")) {
            for(int x=GATE.x-5;x<=GATE.x+5;x++)for(int y=GATE.y-16;y<=GATE.y-1;y++)for(int z=GATE.z-5;z<=GATE.z+5;z++){
                BlockPos g=new BlockPos(x,y,z);if(snap.canStand(g))goals.add(g);
            }
        }
        if(goals.isEmpty()){status="Waiting for target chunks to load: "+why;retryTicks=ticks+40;return;}
        purpose=why;status="Computing "+why+" path...";
        future=worker.submit(()->new Pathfinder().find(snap,start,goals,GeodeConfig.maxExpandedNodes,GeodeConfig.maxFallBlocks));
    }
    private void collectResult(EntityPlayerSP p){
        if(future==null||!future.isDone())return;Future<PathResult> f=future;future=null;
        try{PathResult r=f.get();if(r.found()){movement.setPath(r.steps,p);status=purpose+" path ready ("+r.steps.size()+" steps)";}
            else{status="No path to "+purpose+"; retrying";retryTicks=ticks+30;}}
        catch(Exception ex){status="Path search error: "+ex.getClass().getSimpleName();retryTicks=ticks+30;}
    }
    private void findChest(EntityPlayerSP p,World w){
        BlockPos best=null;double bd=Double.POSITIVE_INFINITY;long now=w.getTotalWorldTime();
        for(Object o:w.loadedTileEntityList)if(o instanceof TileEntityChest){
            net.minecraft.util.BlockPos pos=((TileEntityChest)o).getPos();
            if(Math.abs(pos.getX()-GATE.x)>GeodeConfig.scanRadius||Math.abs(pos.getZ()-GATE.z)>GeodeConfig.scanRadius
                    ||pos.getY()<GATE.y-28||pos.getY()>GATE.y+8)continue;
            BlockPos c=new BlockPos(pos.getX(),pos.getY(),pos.getZ());
            Long until=cooldown.get(c);if(until!=null&&until>now)continue;
            double d=c.distanceSquared(p.posX,p.getEntityBoundingBox().minY,p.posZ);if(d<bd){bd=d;best=c;}
        }
        if(best!=null){chest=best;stage=Stage.TO_CHEST;status="Chest found: "+best;routeChest(p,w,best);}
        else if(++emptyScans>=5){emptyScans=0;stage=Stage.PATROL;routePatrol(p,w);}
        else status="No loaded Sewer chest; will scan again";
    }
    private void openChest(EntityPlayerSP p,World w){
        if(chest==null){stage=Stage.SEEK;return;}
        net.minecraft.util.BlockPos bp=new net.minecraft.util.BlockPos(chest.x,chest.y,chest.z);
        if(!w.isBlockLoaded(bp,false)||w.getBlockState(bp).getBlock()!=net.minecraft.init.Blocks.chest
                &&w.getBlockState(bp).getBlock()!=net.minecraft.init.Blocks.trapped_chest){
            cooldown.put(chest,w.getTotalWorldTime()+100L);chest=null;stage=Stage.SEEK;return;
        }
        if(mc.currentScreen instanceof GuiChest&&p.openContainer instanceof ContainerChest){stage=Stage.LOOT;lootTicks=0;emptySlots=0;failedClicks=0;return;}
        if(p.getDistance(chest.x+.5D,chest.y+.5D,chest.z+.5D)>5D){stage=Stage.TO_CHEST;routeChest(p,w,chest);return;}
        if(ticks<retryTicks)return;
        if(openAttempts>=3){cooldown.put(chest,w.getTotalWorldTime()+200L);chest=null;stage=Stage.SEEK;return;}
        openAttempts++;retryTicks=ticks+12;
        mc.playerController.onPlayerRightClick(p,w,p.getHeldItem(),bp,EnumFacing.UP,
                new Vec3(chest.x+.5D,chest.y+.5D,chest.z+.5D));
    }
    private void lootChest(EntityPlayerSP p){
        if(!(mc.currentScreen instanceof GuiChest)||!(p.openContainer instanceof ContainerChest)){finish(p);return;}
        if(++lootTicks<3)return;lootTicks=0;
        ContainerChest c=(ContainerChest)p.openContainer;int n=c.getLowerChestInventory().getSizeInventory();
        for(int i=0;i<n;i++){ItemStack s=c.getLowerChestInventory().getStackInSlot(i);if(s==null)continue;
            int before=s.stackSize;mc.playerController.windowClick(c.windowId,i,0,1,p);
            ItemStack after=c.getLowerChestInventory().getStackInSlot(i);
            failedClicks=after!=null&&after.stackSize>=before?failedClicks+1:0;
            if(failedClicks>=3){p.closeScreen();finish(p);return;}return;
        }
        if(++emptySlots>=2){p.closeScreen();finish(p);}
    }
    private void finish(EntityPlayerSP p){if(chest!=null&&mc.theWorld!=null)cooldown.put(chest,mc.theWorld.getTotalWorldTime()+1200L);chest=null;stage=Stage.SEEK;scanTicks=GeodeConfig.scanIntervalTicks;status="Chest cleared; searching for next";}
    private boolean inSewer(EntityPlayerSP p){double dx=p.posX-GATE.x-.5D,dz=p.posZ-GATE.z-.5D;return dx*dx+dz*dz<64D&&p.getEntityBoundingBox().minY<GATE.y-.2D&&p.onGround;}
    private List<BlockPos> box(int ax,int bx,int ay,int by,int az,int bz){List<BlockPos> l=new ArrayList<BlockPos>();for(int x=ax;x<=bx;x++)for(int y=ay;y<=by;y++)for(int z=az;z<=bz;z++)l.add(new BlockPos(x,y,z));return l;}
    private BlockPos playerPos(EntityPlayerSP p){return new BlockPos(MathHelper.floor_double(p.posX),MathHelper.floor_double(p.getEntityBoundingBox().minY),MathHelper.floor_double(p.posZ));}
    private void tell(String s){if(mc.thePlayer!=null)mc.thePlayer.addChatMessage(new ChatComponentText("§b[Geode] §f"+s));}
    private static double sq(double v){return v*v;}
}
