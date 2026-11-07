package dev.atlasdive.geode;

import dev.atlasdive.geode.client.GeodeClient;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

@Mod(modid = Geode.MODID, name = Geode.NAME, version = Geode.VERSION, clientSideOnly = true)
public final class Geode {
    public static final String MODID = "geode";
    public static final String NAME = "Geode";
    public static final String VERSION = "0.1.0";

    private final GeodeClient client = new GeodeClient();

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        GeodeConfig.load(event.getSuggestedConfigurationFile());
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        ClientRegistry.registerKeyBinding(GeodeClient.TOGGLE_KEY);
        MinecraftForge.EVENT_BUS.register(client);
        FMLCommonHandler.instance().bus().register(client);
    }
}
