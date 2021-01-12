package com.softwareverde.bitbalancer.proxy;

import com.softwareverde.bitbalancer.proxy.node.selector.NodeSelector;
import com.softwareverde.bitbalancer.proxy.rpc.ChainHeight;
import com.softwareverde.bitbalancer.proxy.rpc.connector.BitcoinVerdeRpcConnector;
import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.MutableBlock;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.ImmutableBlockHeader;
import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.block.validator.difficulty.AsertDifficultyCalculator;
import com.softwareverde.bitcoin.block.validator.difficulty.AsertReferenceBlock;
import com.softwareverde.bitcoin.rpc.BlockTemplate;
import com.softwareverde.bitcoin.server.main.BitcoinConstants;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.type.time.SystemTime;

import java.util.concurrent.atomic.AtomicBoolean;

public class CachingBlockTemplateManager extends BlockTemplateManager {
    protected final SystemTime _systemTime = new SystemTime();

    protected Long _newBlockTemplatePeriodMs = 10000L;
    protected Thread _updateThread = null;

    protected final AtomicBoolean _getBlockTemplatePin = new AtomicBoolean(false);
    protected Thread _getBlockTemplateThread = null;

    protected BlockTemplate _cachedBlockTemplate = null;

    protected BlockHeader _currentHeadBlockHeader = null;
    protected Long _currentHeadBlockHeight = ChainHeight.UNKNOWN_CHAIN_HEIGHT.getBlockHeight();

    protected void _updateCachedBlockTemplateAsync() {
        synchronized (_getBlockTemplatePin) {
            _getBlockTemplatePin.set(true);
            _getBlockTemplatePin.notifyAll();
        }
    }

    protected BlockTemplate _generateEmptyBlock() {
        if ( (_currentHeadBlockHeader == null) || (_currentHeadBlockHeight == null) ) { return null; }
        final Long blockHeight = _currentHeadBlockHeight;

        final Difficulty difficulty;
        {
            final AsertDifficultyCalculator asertDifficultyCalculator = new AsertDifficultyCalculator();
            final AsertReferenceBlock asertReferenceBlock = BitcoinConstants.getAsertReferenceBlock();
            final Long previousBlockTimestamp = _currentHeadBlockHeader.getTimestamp();

            difficulty = asertDifficultyCalculator.computeAsertTarget(asertReferenceBlock, previousBlockTimestamp, blockHeight);
        }

        final MutableBlock block = new MutableBlock();
        block.setVersion(BlockHeader.VERSION);
        block.setDifficulty(difficulty);
        block.setPreviousBlockHash(_currentHeadBlockHeader.getHash());
        block.setTimestamp(_systemTime.getCurrentTimeInSeconds());
        block.setNonce(0L);

        return BitcoinVerdeRpcConnector.toBlockTemplate(block, blockHeight, _systemTime);
    }

    protected void _createUpdateThread() {
        _updateThread = new Thread(new Runnable() {
            @Override
            public void run() {
                final Thread thread = Thread.currentThread();
                try {
                    while (! thread.isInterrupted()) {
                        _updateCachedBlockTemplateAsync();

                        Thread.sleep(_newBlockTemplatePeriodMs);
                    }
                }
                catch (final Exception exception) { }
                finally {
                    synchronized (CachingBlockTemplateManager.this) {
                        if (_updateThread == thread) {
                            _updateThread = null;
                        }
                    }
                }
            }
        });
    }

    protected void _createGetBlockTemplateThread() {
        _getBlockTemplateThread = new Thread(new Runnable() {
            @Override
            public void run() {
                final Thread thread = Thread.currentThread();
                try {
                    while (! thread.isInterrupted()) {
                        _getBlockTemplatePin.set(false);
                        _cachedBlockTemplate = CachingBlockTemplateManager.super.getBlockTemplate();

                        final BlockTemplate blockTemplate = _cachedBlockTemplate;
                        if (blockTemplate != null) {
                            Logger.debug("Cached template has " + blockTemplate.getTransactionCount() + " transactions.");
                        }

                        synchronized (_getBlockTemplatePin) {
                            if (! _getBlockTemplatePin.get()) {
                                _getBlockTemplatePin.wait();
                            }
                        }
                    }
                }
                catch (final Exception exception) { }
                finally {
                    synchronized (CachingBlockTemplateManager.this) {
                        if (_getBlockTemplateThread == thread) {
                            _getBlockTemplateThread = null;
                        }
                    }
                }
            }
        });
    }

    protected void _stopUpdateThread() {
        try {
            _updateThread.interrupt();
            _updateThread.join(5000L);
        }
        catch (final Exception exception) {
            Logger.warn(exception);
        }
    }

    protected void _stopGetBlockTemplateThread() {
        try {
            _getBlockTemplateThread.interrupt();
            _getBlockTemplateThread.join(5000L);
        }
        catch (final Exception exception) {
            Logger.warn(exception);
        }
    }

    public CachingBlockTemplateManager(final NodeSelector nodeSelector) {
        super(nodeSelector);
    }

    protected void onNewBlock(final BlockHeader blockHeader, final ChainHeight chainHeight) {
        _currentHeadBlockHeader = blockHeader;
        _currentHeadBlockHeight = chainHeight.getBlockHeight();
        _cachedBlockTemplate = _generateEmptyBlock();

        final BlockTemplate blockTemplate = _cachedBlockTemplate;
        if (blockTemplate != null) {
            Logger.debug("Cached template has " + blockTemplate.getTransactionCount() + " transactions.");
        }

        _updateCachedBlockTemplateAsync();
    }

    @Override
    public synchronized BlockTemplate getBlockTemplate() {
        if (_cachedBlockTemplate == null) {
            _updateCachedBlockTemplateAsync();

            Logger.debug("Synchronously loading block template.");
            final BlockTemplate blockTemplate = CachingBlockTemplateManager.super.getBlockTemplate();
            if (blockTemplate != null) {
                final Block block = blockTemplate.toBlock();
                if (block != null) {
                    _currentHeadBlockHeader = new ImmutableBlockHeader(block); // Keep only the blockHeader...
                    _currentHeadBlockHeight = blockTemplate.getBlockHeight();
                }

                _cachedBlockTemplate = blockTemplate;
                Logger.debug("Cached template has " + blockTemplate.getTransactionCount() + " transactions.");
            }
            return blockTemplate;
        }

        return _cachedBlockTemplate;
    }

    public void updateBlockTemplate() {
        _updateCachedBlockTemplateAsync();
    }

    public void setBlockTemplatePollPeriod(final Long pollPeriodInMs) {
        _newBlockTemplatePeriodMs = pollPeriodInMs;
    }

    public synchronized void start() {
        if (_updateThread != null) {
            _stopUpdateThread();
        }
        if (_getBlockTemplateThread != null) {
            _stopGetBlockTemplateThread();
        }

        _createUpdateThread();
        _createGetBlockTemplateThread();
        _updateThread.start();
        _getBlockTemplateThread.start();
    }

    public void stop() {
        _stopGetBlockTemplateThread();
        _stopUpdateThread();
    }
}
