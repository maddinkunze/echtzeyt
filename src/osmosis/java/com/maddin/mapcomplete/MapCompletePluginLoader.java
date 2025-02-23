package com.maddin.mapcomplete;

import org.openstreetmap.osmosis.core.pipeline.common.TaskManagerFactory;
import org.openstreetmap.osmosis.core.plugin.PluginLoader;

import java.util.HashMap;
import java.util.Map;

public class MapCompletePluginLoader implements PluginLoader {
    @Override
    public Map<String, TaskManagerFactory> loadTaskFactories() {
        MapCompleteFactory factory = new MapCompleteFactory();
        Map<String, TaskManagerFactory> map = new HashMap<>();

        map.put("map-complete", factory);
        map.put("mc", factory);

        return map;
    }
}