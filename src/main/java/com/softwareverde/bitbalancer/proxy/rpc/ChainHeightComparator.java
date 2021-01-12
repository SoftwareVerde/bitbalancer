package com.softwareverde.bitbalancer.proxy.rpc;

import com.softwareverde.bitcoin.block.header.difficulty.work.ChainWork;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;

import java.util.Comparator;

public class ChainHeightComparator implements Comparator<ChainHeight> {
    protected static final Comparator<ChainWork> CHAIN_WORK_COMPARATOR = new Comparator<ChainWork>() {
        @Override
        public int compare(final ChainWork chainWork0, final ChainWork chainWork1) {
            for (int i = 0; i < Sha256Hash.BYTE_COUNT; ++i) {
                final byte b0 = chainWork0.getByte(i);
                final byte b1 = chainWork1.getByte(i);

                final int byteCompare = ByteUtil.compare(b0, b1);
                if (byteCompare == 0) { continue; }

                return byteCompare;
            }

            return 0;
        }
    };

    @Override
    public int compare(final ChainHeight chainHeight0, final ChainHeight chainHeight1) {
        return this.compare(chainHeight0, chainHeight1, null);
    }

    public int compare(final ChainHeight chainHeight0, final ChainHeight chainHeight1, final Integer byHeight) {
        final ChainWork chainWork0 = chainHeight0.getChainWork();
        final ChainWork chainWork1 = chainHeight1.getChainWork();

        final Long blockHeight0 = chainHeight0.getBlockHeight();
        final Long blockHeight1 = chainHeight1.getBlockHeight();

        final boolean requireByHeight = ( (byHeight != null) && (byHeight > 0) );

        // If either ChainWorks are null then fallback to blockHeight.
        //  This can happen with nodes that do now expose their ChainWork (i.e. BCHD as of 20201209).
        if ( (chainWork0 != null) && (chainWork1 != null) ) {
            final int chainWorkComparison = CHAIN_WORK_COMPARATOR.compare(chainWork0, chainWork1);
            if (! requireByHeight) { // If both ChainWorks are provided (with no byHeight), then disregard blockHeight and only consider the best ChainWork.
                return chainWorkComparison;
            }

            final boolean chainWork0IsNotBetterThanChainWork1 = (chainWorkComparison < 0);
            if (chainWork0IsNotBetterThanChainWork1) { return -1; }
        }

        if (requireByHeight) {
            final int compareValue = blockHeight0.compareTo(blockHeight1 + byHeight);
            return (compareValue >= 0 ? 1 : -1); // ::compare with a byHeight cannot be equal unless the ChainWorks are equal.
        }

        // If both chainWorks are equal, then defer to the blockHeight (which are likely to be equal).
        return blockHeight0.compareTo(blockHeight1);
    }
}