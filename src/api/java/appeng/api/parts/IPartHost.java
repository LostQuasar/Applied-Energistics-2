/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2013 AlgorithmX2
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package appeng.api.parts;

import java.util.Set;

import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

import appeng.api.util.AEColor;
import appeng.api.util.AEPartLocation;
import appeng.api.util.DimensionalBlockPos;

/**
 * Implemented on AE's block entities.
 *
 * Do Not Implement
 */
public interface IPartHost extends ICustomCableConnection {

    /**
     * @return the facade container
     */
    IFacadeContainer getFacadeContainer();

    /**
     * Test if you can add a part to the specified side of the Part Host, {@link AEPartLocation}.UNKNOWN is used to
     * represent the cable in the middle.
     *
     * @param part to be added part
     * @param side part placed onto side
     *
     * @return returns false if the part cannot be added.
     */
    boolean canAddPart(ItemStack part, AEPartLocation side);

    /**
     * try to add a new part to the specified side, returns false if it failed to be added.
     *
     * @param is    new part
     * @param side  onto side
     * @param owner with owning player
     *
     * @return null if the item failed to add, the side it was placed on other wise ( may different for cables,
     *         {@link AEPartLocation}.UNKNOWN )
     */
    AEPartLocation addPart(ItemStack is, AEPartLocation side, Player owner, InteractionHand hand);

    /**
     * Get part by side ( center is {@link AEPartLocation}.UNKNOWN )
     *
     * @param side side of part
     *
     * @return the part located on the specified side, or null if there is no part.
     */
    IPart getPart(AEPartLocation side);

    /**
     * Get part by side, this method cannot aquire the center part, you must use the other varient of getPart.
     *
     * @param side side of part
     *
     * @return the part located on the specified side, or null if there is no part.
     */
    IPart getPart(Direction side);

    /**
     * removes the part on the side, this doesn't drop it or anything, if you don't do something with it, its just
     * "gone" and its never coming back; think about it.
     *
     * if you want to drop the part you must request it prior to removing it.
     *
     * @param side           side of part
     * @param suppressUpdate - used if you need to replace a part's INSTANCE, without really removing it first.
     */
    void removePart(AEPartLocation side, boolean suppressUpdate);

    /**
     * something changed, might want to send a packet to clients to update state.
     */
    void markForUpdate();

    /**
     * @return the physical location of the part host in the universe.
     */
    DimensionalBlockPos getLocation();

    /**
     * @return the block entity for the host, this can either be an FMP block entity, or a AE block entity
     */
    BlockEntity getBlockEntity();

    /**
     * @return the color of the host type ( this is determined by the middle cable. ) if no cable is present, it returns
     *         {@link AEColor} .Transparent other wise it returns the color of the cable in the center.
     */
    AEColor getColor();

    /**
     * destroys the part container, for internal use.
     */
    void clearContainer();

    /**
     * Used to test for FMP microblock blocking internally.
     *
     * @return returns if microblocks are blocking this cable path.
     */
    boolean isBlocked(Direction side);

    /**
     * finds the part located at the position ( pos must be relative, not global )
     *
     * @param pos part position
     *
     * @return a new SelectedPart, this is never null.
     */
    SelectedPart selectPart(Vec3 pos);

    /**
     * can be used by parts to trigger the block entity or part to save.
     */
    void markForSave();

    void partChanged();

    /**
     * get the redstone state of host on this side, this value is cached internally.
     *
     * @param side side of part
     *
     * @return true of the part host is receiving redstone from an external source.
     */
    boolean hasRedstone(AEPartLocation side);

    /**
     * returns false if this block contains any parts or facades, true other wise.
     */
    boolean isEmpty();

    /**
     * @return a mutable list of flags you can adjust to track state.
     */
    Set<LayerFlags> getLayerFlags();

    /**
     * remove host from level...
     */
    void cleanup();

    /**
     * notify neighbors uf updated status.
     */
    void notifyNeighbors();

    /**
     * true if the block entity is in the world, other wise false.
     *
     * @return true if block entity is in world
     */
    boolean isInWorld();

}
