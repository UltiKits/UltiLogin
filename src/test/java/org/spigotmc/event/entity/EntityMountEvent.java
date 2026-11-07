package org.spigotmc.event.entity;

import org.bukkit.entity.Entity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.entity.EntityEvent;

/**
 * Test-only legacy API shape, verified against the official Paper 1.19.2/1.20.4 APIs.
 * Not a server implementation or runtime dependency; tests need no locally cached old Paper JAR.
 */
public class EntityMountEvent extends EntityEvent implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Entity mount;
    private boolean cancelled;

    public EntityMountEvent(Entity passenger, Entity mount) {
        super(passenger);
        this.mount = mount;
    }

    public Entity getMount() { return mount; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
