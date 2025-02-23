package com.maddin.mapcomplete;

import java.util.Map;
import java.util.ArrayList;
import java.util.Collection;

import org.openstreetmap.osmosis.core.domain.v0_6.Tag;
import org.openstreetmap.osmosis.core.domain.v0_6.Way;
import org.openstreetmap.osmosis.core.domain.v0_6.Node;
import org.openstreetmap.osmosis.core.domain.v0_6.Entity;
import org.openstreetmap.osmosis.core.task.v0_6.Sink;
import org.openstreetmap.osmosis.core.task.v0_6.SinkSource;
import org.openstreetmap.osmosis.core.container.v0_6.EntityContainer;

public class MapCompleteTask implements SinkSource {
    private Sink sink;

    MapCompleteTask() {
        System.out.println("Hello from MapCompleteTask.constructor()");
    }

    @Override
    public void initialize(Map<String, Object> metadata) {
        sink.initialize(metadata);
    }

    @Override
    public final void complete() {
        sink.complete();
        System.out.println("Hello from MapCompleteTask.complete()");
    }

    @Override
    public final void close() {
        sink.close();
        System.out.println("Hello from MapCompleteTask.close()");
    }

    @Override
    public final void process(EntityContainer entityContainer) {
        Entity entity = entityContainer.getEntity();
        if (shouldDropEntity(entity)) { return; }
        calculateHeight(entity);
        makeSuburbsUppercase(entity);
        dropUnnecessaryTags(entity);
        sink.process(entityContainer);
    }

    @Override
    public void setSink(Sink sink) {
        this.sink = sink;
    }

    public void calculateHeight(Entity entity) {
        Collection<Tag> entityTags = entity.getTags();

        boolean hasHeightTag = false;
        Float levels = null;

        String shelter = null;
        String hut = null;

        for (Tag tag : entityTags) {
            String name = tag.getKey();
            String value = null;
            Float valueF = null;

            try {
                value = tag.getValue();
                valueF = Float.parseFloat(value);
            } catch (Exception e) {}

            if ((name.equals("height")) && (valueF != null) && (valueF > 0)) {
                hasHeightTag = true;
                break;
            }
            if (name.equals("building:levels")) {
                levels = valueF;
            }
            if (name.equals("amenity") && (value != null)) {
                if (value.equals("shelter") && (shelter == null)) {
                    shelter = "yes";
                }
                if (value.equals("fast_food")) {
                    hut = "fast_food";
                }
            }
            if (name.equals("shelter_type") && (value != null)) {
                shelter = value;
            }
        }

        if (hasHeightTag) { return; }

        Float height = null;

        if (hut != null) {
            height = 3.8f;
        }
        if (levels != null) {
            height = 2.8f * levels;
        }
        if (shelter != null) {
            height = 2.8f;
        }

        if (height == null) { return; }

        entityTags.add(new Tag("height", String.valueOf(height)));
    }

    private void makeSuburbsUppercase(Entity entity) {
        Collection<Tag> entityTags = entity.getTags();

        boolean isCityPart = false;
        Tag tagName = null;
        for (Tag tag : entityTags) {
            String name = tag.getKey();
            if (name.equals("name")) { tagName = tag; }
            if (!name.equals("place")) { continue; }
            if (!tag.getValue().equals("suburb")) { continue; }
            isCityPart = true;
        }

        if (!isCityPart) { return; }
        if (tagName == null) { return; }
        entityTags.remove(tagName);
        entityTags.add(new Tag(tagName.getKey(), tagName.getValue().toUpperCase()));
    }

    private boolean shouldDropEntity(Entity entity) { return false; }

    private void dropUnnecessaryTags(Entity entity) {
        boolean isStreet = false;
        boolean isPlace = false;
        boolean isBuilding = false;
        Tag tagName = null;
        Collection<Tag> entityTags = entity.getTags();
        Collection<Tag> tagsToKeep = new ArrayList<Tag>();

        for (Tag tag : entityTags) {
            String name = tag.getKey();
            if (name.equals("highway")) {
                isStreet = true;
                tagsToKeep.add(tag);
            }
            if (name.equals("building") || name.equals("building:part")) {
                isBuilding = true;
                tagsToKeep.add(tag);
            }
            if (name.equals("addr:housenumber")) {
                tagsToKeep.add(tag);
            }
            if (name.equals("place")) {
                isPlace = true;
                tagsToKeep.add(tag);
            }
            if (name.equals("railway")) {
                tagsToKeep.add(tag);
            }
            if (name.equals("name")) {
                tagName = tag;
            }
            if (name.equals("height") || name.equals("min_height")) {
                tagsToKeep.add(tag);
            }
            if (name.equals("place") || name.equals("natural") || name.equals("waterway") || name.equals("landuse") || name.equals("leisure")) {
                tagsToKeep.add(tag);
            }
            if (name.equals("type")) {
                tagsToKeep.add(tag);
            }
        }

        if ((isStreet || isPlace) && tagName != null) {
            tagsToKeep.add(tagName);
        }
        entityTags.retainAll(tagsToKeep);
    }
}