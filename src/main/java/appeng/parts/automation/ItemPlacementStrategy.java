package appeng.parts.automation;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.DirectionalPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import appeng.api.behaviors.PlacementStrategy;
import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.core.AEConfig;
import appeng.util.Platform;

public class ItemPlacementStrategy implements PlacementStrategy {
    private static final RandomSource RANDOM_OFFSET = RandomSource.create();
    private final ServerLevel level;
    private final BlockPos pos;
    private final Direction side;
    private final BlockEntity host;
    @Nullable
    private final UUID ownerUuid;
    private boolean blocked = false;

    public ItemPlacementStrategy(ServerLevel level, BlockPos pos, Direction side, BlockEntity host,
            @Nullable UUID owningPlayerId) {
        this.level = level;
        this.pos = pos;
        this.side = side;
        this.host = host;
        this.ownerUuid = owningPlayerId;
    }

    public void clearBlocked() {
        this.blocked = !level.getBlockState(pos).canBeReplaced();
    }

    public final long placeInWorld(AEKey what, long amount, Actionable type, boolean placeAsEntity) {
        if (this.blocked || !(what instanceof AEItemKey itemKey) || amount <= 0) {
            return 0;
        }

        var i = itemKey.getItem();

        var maxStorage = (int) Math.min(amount, i.getMaxStackSize());
        var is = itemKey.toStack(maxStorage);
        var worked = false;

        var side = this.side.getOpposite();

        final var placePos = pos;

        if (level.getBlockState(placePos).canBeReplaced()) {
            if (placeAsEntity) {
                final var sum = this.countEntitesAround(level, placePos);

                // Disable spawning once there is a certain amount of entities in an area.
                if (sum < AEConfig.instance().getFormationPlaneEntityLimit()) {
                    worked = true;

                    if (type == Actionable.MODULATE) {
                        is.setCount(maxStorage);
                        spawnItemEntity(level, host, side, is);
                    }
                }
            } else {
                final var player = Platform.getFakePlayer(level, ownerUuid);
                Platform.configurePlayer(player, side, host);

                maxStorage = is.getCount();
                worked = true;
                if (type == Actionable.MODULATE) {
                    // The side the plane is attached to will be considered the look direction
                    // in terms of placing an item
                    var lookDirection = side;
                    var context = new PlaneDirectionalPlaceContext(level, player, placePos,
                            lookDirection, is, lookDirection.getOpposite());

                    // In case the item does not look at the use context for the stack to use
                    player.setItemInHand(InteractionHand.MAIN_HAND, is);
                    try {
                        i.useOn(context);
                    } finally {
                        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    }
                    maxStorage = Math.max(0, maxStorage - is.getCount());

                } else {
                    maxStorage = 1;
                }

                // Seems to work without... Safe keeping
                // player.setHeldItem(hand, ItemStack.EMPTY);
            }
        }

        this.blocked = !level.getBlockState(placePos).canBeReplaced();

        if (worked) {
            return maxStorage;
        }

        return 0;
    }

    private static void spawnItemEntity(Level level, BlockEntity te, Direction side, ItemStack is) {
        // The center of the block the plane is located in
        final var centerX = te.getBlockPos().getX() + .5;
        final double centerY = te.getBlockPos().getY();
        final var centerZ = te.getBlockPos().getZ() + .5;

        // Create an ItemEntity already at the position of the plane.
        // We don't know the final position, but need it for its size.
        Entity entity = new ItemEntity(level, centerX, centerY, centerZ, is.copy());

        // When spawning downwards, we have to take into account that it spawns it at
        // their "feet" and not center like x or z. So we move it up to be flush with
        // the plane
        final double additionalYOffset = side.getStepY() == -1 ? 1 - entity.getBbHeight() : 0;

        // Calculate the maximum spawn area so an entity hitbox will always be inside
        // the block.
        final double spawnAreaHeight = Math.max(0, 1 - entity.getBbHeight());
        final double spawnAreaWidth = Math.max(0, 1 - entity.getBbWidth());

        // Calculate the offsets to spawn it into the adjacent block, taking the sign
        // into account.
        // Spawn it 0.8 blocks away from the center pos when facing in this direction
        // Every other direction will select a position in a .5 block area around the
        // block center.
        final var offsetX = side.getStepX() == 0 //
                ? RANDOM_OFFSET.nextFloat() * spawnAreaWidth - spawnAreaWidth / 2
                : side.getStepX() * (.525 + entity.getBbWidth() / 2);
        final var offsetY = side.getStepY() == 0 //
                ? RANDOM_OFFSET.nextFloat() * spawnAreaHeight
                : side.getStepY() + additionalYOffset;
        final var offsetZ = side.getStepZ() == 0 //
                ? RANDOM_OFFSET.nextFloat() * spawnAreaWidth - spawnAreaWidth / 2
                : side.getStepZ() * (.525 + entity.getBbWidth() / 2);

        final var absoluteX = centerX + offsetX;
        final var absoluteY = centerY + offsetY;
        final var absoluteZ = centerZ + offsetZ;

        // Set to correct position and slow the motion down a bit
        entity.setPos(absoluteX, absoluteY, absoluteZ);
        entity.setDeltaMovement(side.getStepX() * .1, side.getStepY() * 0.1, side.getStepZ() * 0.1);

        // NOTE: Vanilla generally ignores the return-value of this method when spawning items into the world
        // Forge will return false when the embedded event is canceled, but the event canceller is responsible
        // for cleaning up the entity in that case, so we should always assume our spawning was successful,
        // and consume items...
        level.addFreshEntity(entity);
    }

    private int countEntitesAround(Level level, BlockPos pos) {
        final var t = new AABB(pos).inflate(8);
        final var list = level.getEntitiesOfClass(Entity.class, t);

        return list.size();
    }

    /**
     * A custom {@link DirectionalPlaceContext} which also accepts a player needed various blocks like seeds.
     * <p>
     * Also removed {@link DirectionalPlaceContext#replacingClickedOnBlock} as this can cause a
     * {@link StackOverflowError} for certain replaceable blocks.
     */
    private static class PlaneDirectionalPlaceContext extends BlockPlaceContext {
        private final Direction lookDirection;

        public PlaneDirectionalPlaceContext(Level level, Player player, BlockPos pos, Direction lookDirection,
                ItemStack itemStack, Direction facing) {
            super(level, player, InteractionHand.MAIN_HAND, itemStack,
                    new BlockHitResult(Vec3.atBottomCenterOf(pos), facing, pos, false));
            this.lookDirection = lookDirection;
        }

        @Override
        public BlockPos getClickedPos() {
            return this.getHitResult().getBlockPos();
        }

        @Override
        public boolean canPlace() {
            return getLevel().getBlockState(this.getClickedPos()).canBeReplaced(this);
        }

        @Override
        public Direction getNearestLookingDirection() {
            return Direction.DOWN;
        }

        @Override
        public Direction[] getNearestLookingDirections() {
            return switch (this.lookDirection) {
                default -> new Direction[] { Direction.DOWN, Direction.NORTH, Direction.EAST, Direction.SOUTH,
                        Direction.WEST, Direction.UP };
                case UP -> new Direction[] { Direction.DOWN, Direction.UP, Direction.NORTH, Direction.EAST,
                        Direction.SOUTH, Direction.WEST };
                case NORTH -> new Direction[] { Direction.DOWN, Direction.NORTH, Direction.EAST, Direction.WEST,
                        Direction.UP, Direction.SOUTH };
                case SOUTH -> new Direction[] { Direction.DOWN, Direction.SOUTH, Direction.EAST, Direction.WEST,
                        Direction.UP, Direction.NORTH };
                case WEST -> new Direction[] { Direction.DOWN, Direction.WEST, Direction.SOUTH, Direction.UP,
                        Direction.NORTH, Direction.EAST };
                case EAST -> new Direction[] { Direction.DOWN, Direction.EAST, Direction.SOUTH, Direction.UP,
                        Direction.NORTH, Direction.WEST };
            };
        }

        @Override
        public Direction getHorizontalDirection() {
            return this.lookDirection.getAxis() == Direction.Axis.Y ? Direction.NORTH : this.lookDirection;
        }

        @Override
        public boolean isSecondaryUseActive() {
            return false;
        }

        @Override
        public float getRotation() {
            return this.lookDirection.get2DDataValue() * 90;
        }
    }

}
