package net.minecraftforge.fml.common;

import com.google.common.eventbus.EventBus;

public class DummyModContainer {
    public DummyModContainer(ModMetadata md) {
    }

    public boolean registerBus(EventBus bus, LoadController controller) {
        return false;
    }
}
