package com.maddin.mapcomplete;

import org.openstreetmap.osmosis.core.pipeline.common.TaskManagerFactory;
import org.openstreetmap.osmosis.core.pipeline.common.TaskConfiguration;
import org.openstreetmap.osmosis.core.pipeline.common.TaskManager;
import org.openstreetmap.osmosis.core.pipeline.v0_6.SinkSourceManager;

class MapCompleteFactory extends TaskManagerFactory {
    MapCompleteFactory() {
        System.out.println("Hello from MapCompleteFactory.constructor()");
    }

    @Override
    protected TaskManager createTaskManagerImpl(TaskConfiguration taskConfig) {
        MapCompleteTask task = new MapCompleteTask();
        return new SinkSourceManager(taskConfig.getId(), task, taskConfig.getPipeArgs());
    }
}