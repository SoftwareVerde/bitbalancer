package com.softwareverde.bitbalancer.proxy.rpc;

import com.softwareverde.bitcoin.block.header.difficulty.work.ChainWork;
import com.softwareverde.constable.list.mutable.MutableList;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigInteger;

public class ChainHeightTests {
    @Test
    public void higher_chain_height_should_be_better() {
        // Setup
        final ChainHeight smallerChainHeight = ChainHeight.UNKNOWN_CHAIN_HEIGHT;
        final ChainHeight largerChainHeight = new ChainHeight(1L, ChainWork.fromBigInteger(BigInteger.ONE));

        // Action
        final boolean largerIsBetterThanSmaller = largerChainHeight.isBetterThan(smallerChainHeight);
        final boolean smallerIsBetterThanLarger = smallerChainHeight.isBetterThan(largerChainHeight);

        // Assert
        Assert.assertTrue(largerIsBetterThanSmaller);
        Assert.assertFalse(smallerIsBetterThanLarger);
    }

    @Test
    public void chain_heights_should_sort_ascending() {
        // Setup
        final ChainHeight zeroChainHeight = ChainHeight.UNKNOWN_CHAIN_HEIGHT;
        final ChainHeight oneChainHeight = new ChainHeight(1L, ChainWork.fromBigInteger(BigInteger.ONE));
        final ChainHeight twoChainHeight = new ChainHeight(2L, ChainWork.fromBigInteger(BigInteger.valueOf(2L)));
        final ChainHeight threeChainHeight = new ChainHeight(3L, ChainWork.fromBigInteger(BigInteger.valueOf(3L)));
        final ChainHeight fiveChainHeight = new ChainHeight(5L, ChainWork.fromBigInteger(BigInteger.valueOf(5L)));
        final ChainHeight sevenChainHeight = new ChainHeight(7L, ChainWork.fromBigInteger(BigInteger.valueOf(7L)));

        final MutableList<ChainHeight> chainHeights = new MutableList<>();
        chainHeights.add(oneChainHeight);
        chainHeights.add(sevenChainHeight);
        chainHeights.add(zeroChainHeight);
        chainHeights.add(threeChainHeight);
        chainHeights.add(twoChainHeight);
        chainHeights.add(fiveChainHeight);

        // Action
        chainHeights.sort(ChainHeight.COMPARATOR);

        // Assert
        Assert.assertSame(chainHeights.get(0), zeroChainHeight);
        Assert.assertSame(chainHeights.get(1), oneChainHeight);
        Assert.assertSame(chainHeights.get(2), twoChainHeight);
        Assert.assertSame(chainHeights.get(3), threeChainHeight);
        Assert.assertSame(chainHeights.get(4), fiveChainHeight);
        Assert.assertSame(chainHeights.get(5), sevenChainHeight);
    }

    @Test
    public void lesser_height_should_not_be_better() {
        // Setup
        final ChainHeight chainHeight = new ChainHeight(667854L,        ChainWork.fromHexString("00000000000000000000000000000000000000000158CE7A2EA55FF4C8ACA055"));
        final ChainHeight bestChainHeight = new ChainHeight(667855L,    ChainWork.fromHexString("00000000000000000000000000000000000000000158CEB0A0DFAA0B3411D4DA"));

        // Action
        final boolean isBetterChainHeight = chainHeight.isBetterThan(bestChainHeight);

        // Assert
        Assert.assertFalse(isBetterChainHeight);
    }

    @Test
    public void chain_height_should_be_better_by_height_amounts_with_greater_chain_work() {
        // Setup
        final ChainHeight lesserChainHeight = new ChainHeight(667854L,  ChainWork.fromHexString("00000000000000000000000000000000000000000158CE7A2EA55FF4C8ACA055"));
        final ChainHeight bestChainHeight = new ChainHeight(667855L,    ChainWork.fromHexString("00000000000000000000000000000000000000000158CEB0A0DFAA0B3411D4DA")); // Better BlockHeight (+1), better ChainWork.

        // Action
        final boolean isBetterChainHeight = bestChainHeight.isBetterThan(lesserChainHeight); // byHeight is disabled...
        final boolean isBetterChainHeightByZero = bestChainHeight.isBetterThan(lesserChainHeight, 0); // byHeight is disabled...
        final boolean isBetterChainHeightByOne = bestChainHeight.isBetterThan(lesserChainHeight, 1);
        final boolean isBetterChainHeightByTwo = bestChainHeight.isBetterThan(lesserChainHeight, 2);

        // Assert
        Assert.assertTrue(isBetterChainHeight);
        Assert.assertTrue(isBetterChainHeightByZero);
        Assert.assertTrue(isBetterChainHeightByOne);
        Assert.assertFalse(isBetterChainHeightByTwo);
    }

    @Test
    public void chain_height_should_be_better_by_height_amounts_with_greater_chain_work_v2() {
        // Setup
        final ChainHeight lesserChainHeight = new ChainHeight(667854L,  ChainWork.fromHexString("00000000000000000000000000000000000000000158CE7A2EA55FF4C8ACA055"));
        final ChainHeight bestChainHeight = new ChainHeight(667856L,    ChainWork.fromHexString("00000000000000000000000000000000000000000158CEB0A0DFAA0B3411D4DA"));

        // Action
        final boolean isBetterChainHeight = bestChainHeight.isBetterThan(lesserChainHeight); // byHeight is disabled...
        final boolean isBetterChainHeightByZero = bestChainHeight.isBetterThan(lesserChainHeight, 0); // byHeight is disabled...
        final boolean isBetterChainHeightByOne = bestChainHeight.isBetterThan(lesserChainHeight, 1);
        final boolean isBetterChainHeightByTwo = bestChainHeight.isBetterThan(lesserChainHeight, 2); // "Is the bestChainHeight two blocks ahead of our current chainHeight?" (assuming greater ChainWork)
        final boolean isBetterChainHeightByThree = bestChainHeight.isBetterThan(lesserChainHeight, 3);

        // Assert
        Assert.assertTrue(isBetterChainHeight);
        Assert.assertTrue(isBetterChainHeightByZero);
        Assert.assertTrue(isBetterChainHeightByOne);
        Assert.assertTrue(isBetterChainHeightByTwo); // "The bestChainHeight is 2 blocks ahead of the current chainHeight."
        Assert.assertFalse(isBetterChainHeightByThree); // "The bestChainHeight is not 3 blocks ahead of the current chainHeight."
    }

    @Test
    public void chain_height_should_be_better_by_height_amounts_with_null_chain_work() {
        // Setup
        final ChainHeight lesserChainHeight = new ChainHeight(667854L,  ChainWork.fromHexString("00000000000000000000000000000000000000000158CE7A2EA55FF4C8ACA055"));
        final ChainHeight bestChainHeight = new ChainHeight(667855L,    null);

        // Action
        final boolean isBetterChainHeight = bestChainHeight.isBetterThan(lesserChainHeight); // byHeight is disabled...
        final boolean isBetterChainHeightByZero = bestChainHeight.isBetterThan(lesserChainHeight, 0); // byHeight is disabled...
        final boolean isBetterChainHeightByOne = bestChainHeight.isBetterThan(lesserChainHeight, 1);
        final boolean isBetterChainHeightByTwo = bestChainHeight.isBetterThan(lesserChainHeight, 2);

        // Assert
        Assert.assertTrue(isBetterChainHeight);
        Assert.assertTrue(isBetterChainHeightByZero);
        Assert.assertTrue(isBetterChainHeightByOne);
        Assert.assertFalse(isBetterChainHeightByTwo);
    }

    @Test
    public void chain_height_should_be_not_better_by_height_amounts_with_lesser_chain_work_but_greater_height() {
        // Setup
        final ChainHeight lesserBlockHeightGreaterChainWork = new ChainHeight(667854L,  ChainWork.fromHexString("00000000000000000000000000000000000000000158CEB0A0DFAA0B3411D4DA"));
        final ChainHeight greaterBlockHeightLesserChainWork = new ChainHeight(667855L,  ChainWork.fromHexString("00000000000000000000000000000000000000000158CE7A2EA55FF4C8ACA055"));

        // Action
        final boolean isBetterChainHeight = greaterBlockHeightLesserChainWork.isBetterThan(lesserBlockHeightGreaterChainWork); // byHeight is disabled...
        final boolean isBetterChainHeightByZero = greaterBlockHeightLesserChainWork.isBetterThan(lesserBlockHeightGreaterChainWork, 0); // byHeight is disabled...
        final boolean isBetterChainHeightByOne = greaterBlockHeightLesserChainWork.isBetterThan(lesserBlockHeightGreaterChainWork, 1);
        final boolean isBetterChainHeightByTwo = greaterBlockHeightLesserChainWork.isBetterThan(lesserBlockHeightGreaterChainWork, 2);
        final boolean isBetterChainHeightByThree = greaterBlockHeightLesserChainWork.isBetterThan(lesserBlockHeightGreaterChainWork, 3);

        // Assert
        Assert.assertFalse(isBetterChainHeight);
        Assert.assertFalse(isBetterChainHeightByZero);
        Assert.assertFalse(isBetterChainHeightByOne);
        Assert.assertFalse(isBetterChainHeightByTwo);
        Assert.assertFalse(isBetterChainHeightByThree);
    }
}
