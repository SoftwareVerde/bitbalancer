package com.softwareverde.guvnor.proxy.rpc.connector;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockInflater;
import com.softwareverde.bitcoin.block.header.difficulty.work.ChainWork;
import com.softwareverde.bitcoin.server.module.node.rpc.NodeJsonRpcConnection;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionDeflater;
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

public class BitcoinVerdeRpcConnector implements BitcoinRpcConnector {
    public static final String IDENTIFIER = "VERDE";

    protected static String blockToBlockTemplateJson(final Long blockHeight, final Block block) {
        final TransactionDeflater transactionDeflater = new TransactionDeflater();

        final Json transactionsJson = new Json(true);
        Transaction coinbaseTransaction = null;
        for (final Transaction transaction : block.getTransactions()) {
            if (coinbaseTransaction == null) { // bitcoin-cli getblocktemplate does not return a coinbase.
                coinbaseTransaction = transaction;
                continue;
            }

            final Json transactionJson = new Json();

            final ByteArray transactionData = transactionDeflater.toBytes(transaction);
            transactionJson.put("data", transactionData.toString().toLowerCase());

            transactionsJson.add(transactionJson);
        }

        final Json resultJson = new Json(false);
        resultJson.put("version", block.getVersion());
        resultJson.put("bits", block.getDifficulty().encode().toString().toLowerCase());
        resultJson.put("previousblockhash", block.getPreviousBlockHash().toString().toLowerCase());
        resultJson.put("mintime", block.getTimestamp());
        resultJson.put("coinbasevalue", block.getCoinbaseTransaction().getTotalOutputValue());
        resultJson.put("height", blockHeight);
        resultJson.put("transactions", transactionsJson);

        final Json json = new Json(false);
        json.put("error", null);
        json.put("result", resultJson);
        return json.toString();
    }

    protected final ThreadPool _threadPool;
    protected final BitcoinNodeAddress _bitcoinNodeAddress;
    protected final RpcCredentials _rpcCredentials;

    protected JsonSocket _socketConnection = null;

    protected String _toString() {
        return (this.getHost() + ":" + this.getPort());
    }

    public BitcoinVerdeRpcConnector(final BitcoinNodeAddress bitcoinNodeAddress, final RpcCredentials rpcCredentials) {
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
                case "getblocktemplate": {
                    final Json prototypeBlockJson = nodeJsonRpcConnection.getPrototypeBlock(true);
                    final BlockInflater blockInflater = new BlockInflater();
                    final Block block = blockInflater.fromBytes(ByteArray.fromHexString(prototypeBlockJson.getString("block")));
                    if (block == null) {
                        final Response response = new Response();
                        response.setCode(Response.Codes.SERVER_ERROR);
                        response.setContent("Error retrieving prototype block.");
                        return response;
                    }

                    final long blockHeight;
                    try (final NodeJsonRpcConnection blockHeightNodeJsonRpcConnection = new NodeJsonRpcConnection(host, port, _threadPool)) {
                        final Json responseJson = blockHeightNodeJsonRpcConnection.getBlockHeight();
                        blockHeight = (responseJson.getLong("blockHeight") + 1L);
                    }

                    final Response response = new Response();
                    response.setCode(Response.Codes.OK);
                    response.setContent(BitcoinVerdeRpcConnector.blockToBlockTemplateJson(blockHeight, block));
                    return response;
                }

                case "validateblocktemplate": {
                    final Json paramsJson = requestJson.get("params");
                    final String blockData = paramsJson.getString(0);
                    final BlockInflater blockInflater = new BlockInflater();
                    final Block block = blockInflater.fromBytes(ByteArray.fromHexString(blockData));
                    if (block == null) {
                        final Response response = new Response();
                        response.setCode(Response.Codes.BAD_REQUEST);
                        response.setContent("Unable to parse block data.");
                        return response;
                    }

                    final Json responseJson = nodeJsonRpcConnection.validatePrototypeBlock(block);
                    final Json validationResult = responseJson.get("blockValidation");
                    final Boolean isValid = validationResult.getBoolean("isValid");

                    /*
                        Example Invalid response:
                            error code: -1
                            error message:
                            invalid block: bad-txn-order
                     */
                    if (! isValid) {
                        final Response response = new Response();
                        response.setCode(Response.Codes.BAD_REQUEST);
                        response.setContent("Invalid block.");
                        return response;
                    }

                    final Response response = new Response();
                    response.setCode(Response.Codes.OK);
                    response.setContent(".");
                    return response;
                }

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
    public Boolean validateBlockTemplate(final Block blockTemplate) {
        final String host = _bitcoinNodeAddress.getHost();
        final Integer port = _bitcoinNodeAddress.getPort();

        // TODO: What about mempool synchronization?
        try (final NodeJsonRpcConnection nodeJsonRpcConnection = new NodeJsonRpcConnection(host, port, _threadPool)) {
            final Json responseJson = nodeJsonRpcConnection.validatePrototypeBlock(blockTemplate);
            final Json validationResult = responseJson.get("blockValidation");
            return validationResult.getBoolean("isValid");
        }
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
                Logger.debug("Block: " + blockHash);

                final Notification notification = new Notification(NotificationType.BLOCK_HASH, blockHash);
                notificationCallback.onNewNotification(notification);
            }

            @Override
            public void onNewTransaction(final Json json) {
                final String transactionHashString = json.getString("hash");
                final Sha256Hash transactionHash = Sha256Hash.fromHexString(transactionHashString);
                Logger.debug("Transaction: " + transactionHash);

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
