package com.softwareverde.bitbalancer.proxy.rpc;

import com.softwareverde.bitcoin.block.header.difficulty.work.ChainWork;
import com.softwareverde.util.Util;

public class ChainHeight implements Comparable<ChainHeight> {
    public static final ChainHeight UNKNOWN_CHAIN_HEIGHT = new ChainHeight(0L, null);

    public static final ChainHeightComparator COMPARATOR = new ChainHeightComparator();

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

    /**
     * Returns true if this object's ChainWork is greater than `chainHeight`s ChainWork.
     * If `byHeight` is provided, then this object's ChainWork must be greater than `chainHeight`s ChainWork and
     *  and this object's blockHeight must `byHeight` larger than `chainHeight`s blockHeight.
     * Non-positive numbers for byHeight disable byHeight.
     * If either ChainWork is not available then ChainWork is not considered.
     */
    public Boolean isBetterThan(final ChainHeight chainHeight, final Integer byHeight) {
        return (COMPARATOR.compare(this, chainHeight, byHeight) > 0);
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
