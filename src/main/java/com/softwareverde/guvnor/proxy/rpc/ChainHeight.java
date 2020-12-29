package com.softwareverde.guvnor.proxy.rpc;

import com.softwareverde.bitcoin.block.header.difficulty.work.ChainWork;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.util.Util;

import java.util.Comparator;

public class ChainHeight implements Comparable<ChainHeight> {
    public static final ChainHeight UNKNOWN_CHAIN_HEIGHT = new ChainHeight(0L, null);

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

    public static final Comparator<ChainHeight> COMPARATOR = new Comparator<ChainHeight>() {
        @Override
        public int compare(final ChainHeight chainHeight0, final ChainHeight chainHeight1) {
            final ChainWork chainWork0 = chainHeight0.getChainWork();
            final ChainWork chainWork1 = chainHeight1.getChainWork();

            final Long blockHeight0 = chainHeight0.getBlockHeight();
            final Long blockHeight1 = chainHeight1.getBlockHeight();

            // If either ChainWorks are null then fallback to blockHeight.
            //  This can happen with nodes that do now expose their ChainWork (i.e. BCHD as of 20201209).
            if ( (chainWork0 != null) && (chainWork1 != null) ) {
                // If both ChainWorks are provided, then disregard blockHeight and only consider the best ChainWork.
                final int chainWorkComparison = CHAIN_WORK_COMPARATOR.compare(chainWork0, chainWork1);
                if (chainWorkComparison != 0) { return chainWorkComparison; }
            }

            // If both chainWorks are equal, then defer to the blockHeight (which are likely to be equal).
            return blockHeight0.compareTo(blockHeight1);
        }
    };

    protected final Long _blockHeight;
    protected final ChainWork _chainWork;

    public ChainHeight(final Long blockHeight, final ChainWork chainWork) {
        _blockHeight = blockHeight;
        _chainWork = chainWork;
    }

    public Long getBlockHeight() {
        return _blockHeight;
    }

    public ChainWork getChainWork() {
        return _chainWork;
    }

    public Boolean isBetterThan(final ChainHeight chainHeight) {
        return (COMPARATOR.compare(this, chainHeight) > 0);
    }

    @Override
    public boolean equals(final Object object) {
        if (! (object instanceof ChainHeight)) { return false; }
        final ChainHeight chainHeight = (ChainHeight) object;
        if (! Util.areEqual(_blockHeight, chainHeight.getBlockHeight())) { return false; }
        if (! Util.areEqual(_chainWork, chainHeight.getChainWork())) { return false; }

        return true;
    }

    @Override
    public int hashCode() {
        return ( Util.coalesce(_blockHeight, 0L).hashCode() + (_chainWork != null ? _chainWork.hashCode() : 0) );
    }

    @Override
    public int compareTo(final ChainHeight chainHeight) {
        return COMPARATOR.compare(this, chainHeight);
    }

    @Override
    public String toString() {
        return (_blockHeight + " / " + _chainWork);
    }
}
