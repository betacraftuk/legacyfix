package uk.betacraft.legacyfix.fml;

import com.google.common.eventbus.EventBus;
import cpw.mods.fml.common.DummyModContainer;
import cpw.mods.fml.common.ModMetadata;
import cpw.mods.fml.common.LoadController;
import uk.betacraft.legacyfix.Agent;

public class CpwModContainer extends DummyModContainer {
    public CpwModContainer() {
        this(new ModMetadata());
    }

    public CpwModContainer(ModMetadata md) {
        super(md = new ModMetadata());
        md.modId = "legacyfix";
        md.name = "LegacyFix";
        md.description = "A mod that fixes legacy Minecraft versions.";
        md.url = "https://github.com/betacraftuk/legacyfix";
        md.version = Agent.VERSION;
        md.authorList.add("devcody");
        md.authorList.add("Moresteck");
        md.logoFile = "/assets/legacyfix/icon-64x.png";
    }

    @Override
    public boolean registerBus(EventBus bus, LoadController controller) {
        return true;
    }
}
