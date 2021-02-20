package game.data.entity;

import game.data.WorldManager;
import game.data.chunk.Chunk;
import game.data.coordinates.CoordinateDim2D;
import packets.DataTypeProvider;
import se.llbit.nbt.SpecificTag;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

public class EntityRegistry {

    Map<CoordinateDim2D, Set<Entity>> perChunk;
    Map<Integer, Entity> entities;

    WorldManager worldManager;

    public EntityRegistry(WorldManager manager) {
        this.worldManager = manager;
        this.perChunk = new ConcurrentHashMap<>();
        this.entities = new ConcurrentHashMap<>();
    }

    /**
     * Add a new entity.
     */
    public void addEntity(DataTypeProvider provider, Function<DataTypeProvider, Entity> parser) {
        Entity ent = parser.apply(provider);
        entities.put(ent.getId(), ent);

        ent.registerOnLocationChange((oldPos, newPos) -> {
            CoordinateDim2D oldChunk = oldPos == null ? null : oldPos.globalToDimChunk();
            CoordinateDim2D newChunk = newPos.globalToDimChunk();

            // if they're the same, just mark the chunk as unsaved
            if (oldPos == newPos) {
                markUnsaved(newChunk);
                return;
            }

            Set<Entity> entities = oldChunk == null ? null : perChunk.get(oldChunk);
            if (entities != null) {
                entities.remove(ent);

                if (entities.isEmpty()) {
                    perChunk.remove(oldChunk);
                }
            }

            Set<Entity> set = perChunk.computeIfAbsent(newChunk, (k) -> ConcurrentHashMap.newKeySet());
            set.add(ent);

            markUnsaved(newChunk);

        });
    }

    private void markUnsaved(CoordinateDim2D coord) {
        Chunk chunk = worldManager.getChunk(coord);
        if (chunk != null) {
            worldManager.touchChunk(chunk);
        }
    }

    /**
     * Delete all tile entities for a chunk, only done when the chunk is also unloaded. Note that this only related to
     * tile entities sent in the update-tile-entity packets, ones sent with the chunk will only be stored in the chunk.
     * @param location the position of the chunk for which we can delete tile entities.
     */
    public void unloadChunk(CoordinateDim2D location) {
        Set<Entity> entities = perChunk.remove(location);
        if (entities == null) { return; }

        for (Entity e : entities) {
            this.entities.remove(e.getId());
        }
    }


    public void addMetadata(DataTypeProvider provider) {
        Entity ent = entities.get(provider.readVarInt());

        if (ent != null) {
            ent.parseMetadata(provider);
        }
    }

    public void updatePositionRelative(DataTypeProvider provider) {
        Entity ent = entities.get(provider.readVarInt());

        if (ent != null) {
            ent.incrementPosition(provider.readShort(), provider.readShort(), provider.readShort());
        }
    }

    public void updatePositionAbsolute(DataTypeProvider provider) {
        Entity ent = entities.get(provider.readVarInt());

        if (ent != null) {
            ent.readPosition(provider);
        }
    }

    public List<SpecificTag> getEntitiesNbt(CoordinateDim2D location) {
        Set<Entity> entities = perChunk.get(location);

        if (entities == null) {
            return Collections.emptyList();
        }

        return entities.stream().map(Entity::toNbt).collect(Collectors.toList());
    }

    public void reset() {
        this.entities.clear();
        this.perChunk.clear();
    }

    public void addEquipment(DataTypeProvider provider) {
        int id = provider.readVarInt();
        Entity ent = entities.get(id);

        if (ent != null) {
            ent.addEquipment(provider);
        }
    }
}
