package com.softwareverde.guvnor.proxy.rpc.connector;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockInflater;
import com.softwareverde.bitcoin.block.header.difficulty.work.ChainWork;
import com.softwareverde.bitcoin.block.validator.BlockValidator;
import com.softwareverde.bitcoin.server.module.node.rpc.NodeJsonRpcConnection;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.util.StringUtil;
import com.softwareverde.concurrent.pool.MainThreadPool;
import com.softwareverde.concurrent.pool.ThreadPool;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.guvnor.BitcoinNodeAddress;
import com.softwareverde.guvnor.proxy.Notification;
import com.softwareverde.guvnor.proxy.NotificationType;
import com.softwareverde.guvnor.proxy.rpc.ChainHeight;
import com.softwareverde.guvnor.proxy.rpc.NotificationCallback;
import com.softwareverde.guvnor.proxy.rpc.RpcCredentials;
import com.softwareverde.http.server.servlet.request.Request;
import com.softwareverde.http.server.servlet.response.Response;
import com.softwareverde.json.Json;
import com.softwareverde.logging.Logger;
import com.softwareverde.network.socket.JsonSocket;
import com.softwareverde.util.Util;
import com.softwareverde.util.type.time.SystemTime;

public class BitcoinVerdeRpcConnector implements BitcoinRpcConnector {
    public static final String IDENTIFIER = "VERDE";

    protected final SystemTime _systemTime;
    protected final ThreadPool _threadPool;
    protected final BitcoinNodeAddress _bitcoinNodeAddress;
    protected final RpcCredentials _rpcCredentials;

    protected JsonSocket _socketConnection = null;

    protected String _toString() {
        return (this.getHost() + ":" + this.getPort());
    }

    public BitcoinVerdeRpcConnector(final BitcoinNodeAddress bitcoinNodeAddress, final RpcCredentials rpcCredentials) {
        _systemTime = new SystemTime();
        _bitcoinNodeAddress = bitcoinNodeAddress;
        _rpcCredentials = rpcCredentials;

        _threadPool = new MainThreadPool(2, 5000L);
    }

    @Override
    public String getHost() {
        return _bitcoinNodeAddress.getHost();
    }

    @Override
    public Integer getPort() {
        return _bitcoinNodeAddress.getPort();
    }

    @Override
    public Response handleRequest(final Request request) {
        final String rawPostData = StringUtil.bytesToString(request.getRawPostData());
        final Json requestJson = Json.parse(rawPostData);

        final Integer requestId = requestJson.getInteger("id");
        final String query = requestJson.getString("method").toLowerCase();

        final String host = _bitcoinNodeAddress.getHost();
        final Integer port = _bitcoinNodeAddress.getPort();
        try (final NodeJsonRpcConnection nodeJsonRpcConnection = new NodeJsonRpcConnection(host, port, _threadPool)) {
            switch (query) {
                default: {
                    Logger.debug("Unsupported command: " + query);
                    final Response response = new Response();
                    response.setCode(Response.Codes.BAD_REQUEST);
                    response.setContent("Unsupported method: " + query);
                    return response;
                }
            }
        }
    }

    @Override
    public ChainHeight getChainHeight() {
        final String host = _bitcoinNodeAddress.getHost();
        final Integer port = _bitcoinNodeAddress.getPort();

        final Long blockHeight;
        try (final NodeJsonRpcConnection nodeJsonRpcConnection = new NodeJsonRpcConnection(host, port, _threadPool)) {
            final Json responseJson = nodeJsonRpcConnection.getBlockHeight();
            blockHeight = responseJson.getLong("blockHeight");
        }

        final ChainWork chainWork;
        try (final NodeJsonRpcConnection nodeJsonRpcConnection = new NodeJsonRpcConnection(host, port, _threadPool)) {
            final Json responseJson = nodeJsonRpcConnection.getBlockHeader(blockHeight);
            final Json blockJson = responseJson.get("block");
            final String chainWorkString = blockJson.getString("chainWork");
            chainWork = ChainWork.fromHexString(chainWorkString);
        }

        return new ChainHeight(blockHeight, chainWork);
    }

    @Override
    public BlockTemplate getBlockTemplate() {
        final String host = _bitcoinNodeAddress.getHost();
        final Integer port = _bitcoinNodeAddress.getPort();

        final Json prototypeBlockJson;
        try (final NodeJsonRpcConnection nodeJsonRpcConnection = new NodeJsonRpcConnection(host, port, _threadPool)) {
            prototypeBlockJson = nodeJsonRpcConnection.getPrototypeBlock(true);
        }

        final BlockInflater blockInflater = new BlockInflater();
        final Block block = blockInflater.fromBytes(ByteArray.fromHexString(prototypeBlockJson.getString("block")));
        if (block == null) {
            Logger.warn("Error retrieving prototype block.");
            return null;
        }

        final long blockHeight;
        try (final NodeJsonRpcConnection blockHeightNodeJsonRpcConnection = new NodeJsonRpcConnection(host, port, _threadPool)) {
            final Json responseJson = blockHeightNodeJsonRpcConnection.getBlockHeight();
            blockHeight = (responseJson.getLong("blockHeight") + 1L);
        }

        final BlockTemplate blockTemplate = new BlockTemplate();

        blockTemplate.setBlockVersion(block.getVersion());
        blockTemplate.setDifficulty(block.getDifficulty());
        blockTemplate.setPreviousBlockHash(block.getPreviousBlockHash());
        blockTemplate.setMinimumBlockTime(block.getTimestamp());
        blockTemplate.setNonceRange(BlockTemplate.DEFAULT_NONCE_RANGE);

        blockTemplate.setBlockHeight(blockHeight);

        Transaction coinbaseTransaction = null;
        for (final Transaction transaction : block.getTransactions()) {
            if (coinbaseTransaction == null) {
                coinbaseTransaction = transaction;
                continue;
            }

            final Long fee = 0L; // Unsupported.
            final Integer signatureOperationCount = 0; // Unsupported.
            blockTemplate.addTransaction(transaction, fee, signatureOperationCount);
        }

        if (coinbaseTransaction != null) {
            blockTemplate.setCoinbaseAmount(coinbaseTransaction.getTotalOutputValue());
        }

        final long maxBlockByteCount = BlockInflater.MAX_BYTE_COUNT;
        final long maximumSignatureOperationCount = (maxBlockByteCount / BlockValidator.MIN_BYTES_PER_SIGNATURE_OPERATION);

        final Long now = _systemTime.getCurrentTimeInSeconds();
        blockTemplate.setCurrentTime(now);
        blockTemplate.setMaxSignatureOperationCount(maximumSignatureOperationCount);
        blockTemplate.setMaxBlockByteCount(maxBlockByteCount);

        blockTemplate.setTarget(block.getDifficulty().getBytes());

        final String longPollId = (block.getPreviousBlockHash() + "" + now).toLowerCase();
        blockTemplate.setLongPollId(longPollId);

        blockTemplate.setCoinbaseAuxFlags("");
        blockTemplate.addCapability("proposal");
        blockTemplate.addMutableField("time");
        blockTemplate.addMutableField("transactions");
        blockTemplate.addMutableField("prevblock");

        return blockTemplate;
    }

    @Override
    public Boolean validateBlockTemplate(final BlockTemplate blockTemplate) {
        final String host = _bitcoinNodeAddress.getHost();
        final Integer port = _bitcoinNodeAddress.getPort();

        final Block block = blockTemplate.toBlock();

        final Json responseJson;
        try (final NodeJsonRpcConnection nodeJsonRpcConnection = new NodeJsonRpcConnection(host, port, _threadPool)) {
            responseJson = nodeJsonRpcConnection.validatePrototypeBlock(block);
        }

        final Json validationResult = responseJson.get("blockValidation");
        return validationResult.getBoolean("isValid");
    }

    @Override
    public Boolean supportsNotifications() {
        return true;
    }

    @Override
    public Boolean supportsNotification(final NotificationType notificationType) {
        return (Util.areEqual(NotificationType.BLOCK_HASH, notificationType) || Util.areEqual(NotificationType.TRANSACTION_HASH, notificationType));
    }

    @Override
    public void subscribeToNotifications(final NotificationCallback notificationCallback) {
        if (_socketConnection != null) { return; }

        final String host = _bitcoinNodeAddress.getHost();
        final Integer port = _bitcoinNodeAddress.getPort();
        final NodeJsonRpcConnection nodeJsonRpcConnection = new NodeJsonRpcConnection(host, port, _threadPool);
        nodeJsonRpcConnection.upgradeToAnnouncementHook(new NodeJsonRpcConnection.AnnouncementHookCallback() {
            @Override
            public void onNewBlockHeader(final Json json) {
                final String blockHashString = json.getString("hash");
                final Sha256Hash blockHash = Sha256Hash.fromHexString(blockHashString);
                Logger.trace("Block: " + blockHash);

                final Notification notification = new Notification(NotificationType.BLOCK_HASH, blockHash);
                notificationCallback.onNewNotification(notification);
            }

            @Override
            public void onNewTransaction(final Json json) {
                final String transactionHashString = json.getString("hash");
                final Sha256Hash transactionHash = Sha256Hash.fromHexString(transactionHashString);
                Logger.trace("Transaction: " + transactionHash);

                final Notification notification = new Notification(NotificationType.TRANSACTION_HASH, transactionHash);
                notificationCallback.onNewNotification(notification);
            }
        });
        _socketConnection = nodeJsonRpcConnection.getJsonSocket();
    }

    @Override
    public void unsubscribeToNotifications() {
        _socketConnection.close();
    }

    @Override
    public String toString() {
        return _toString();
    }
}
