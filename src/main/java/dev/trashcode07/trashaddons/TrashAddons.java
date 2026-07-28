package dev.trashcode07.trashaddons;

import dev.trashcode07.trashaddons.media.MediaUtil;
import dev.trashcode07.trashaddons.modules.mediahud.MediaHudModule;
import org.cobalt.addon.Addon;
import org.cobalt.module.ModuleManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TrashAddons implements Addon {

    public static final Logger logger = LoggerFactory.getLogger(TrashAddons.class);

    @Override
    public void onLoad() {
        logger.info("TrashAddons Loaded!");
        ModuleManager.INSTANCE.addModule(MediaHudModule.INSTANCE);
        MediaUtil.start();
    }

    @Override
    public void onUnload() {
        logger.info("TrashAddons Unloaded!");
        MediaUtil.stop();
    }

}
