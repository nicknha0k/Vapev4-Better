package gg.vape.module.utility.clutch;

import gg.vape.module.utility.Clutch;
import gg.vape.module.utility.clutch.BlockPathSearchStrategy;
import gg.vape.module.utility.clutch.BlockPlacementNode;
import gg.vape.utils.BlockUtil;
import gg.vape.utils.datas.BlockData;
import gg.vape.wrapper.impl.Block;
import gg.vape.wrapper.impl.World;
import java.util.Vector;

public class ClutchSolidBlockPathSearchStrategy
implements BlockPathSearchStrategy<BlockPlacementNode> {
    final World world;
    final Clutch clutch;

    @Override
    public int scorePath(Vector<BlockPlacementNode> path) {
        return this.clutch.computePathCost(this.world, path);
    }

    @Override
    public boolean isValidBlock(BlockData blockData) {
        Block block = this.world.getBlockByPos(blockData.D(), blockData.B(), blockData.G());
        return BlockUtil.f(block);
    }

    public ClutchSolidBlockPathSearchStrategy(Clutch clutch, World world) {
        this.clutch = clutch;
        this.world = world;
    }

    @Override
    public int getMaxDepth() {
        return 4;
    }
}

