package uk.betacraft.legacyfix.fml;

import com.google.common.eventbus.EventBus;
import net.minecraftforge.fml.common.DummyModContainer;
import net.minecraftforge.fml.common.LoadController;
import net.minecraftforge.fml.common.ModMetadata;
import uk.betacraft.legacyfix.Agent;

public class ForgeModContainer extends DummyModContainer {
    public ForgeModContainer() {
        this(new ModMetadata());
    }

    public ForgeModContainer(ModMetadata md) {
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
