package com.softwareverde.guvnor.proxy;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.address.AddressInflater;
import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.MutableBlock;
import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionInflater;
import com.softwareverde.bitcoin.util.Util;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.cryptography.secp256k1.key.PrivateKey;
import com.softwareverde.guvnor.proxy.rpc.RpcConfiguration;
import com.softwareverde.guvnor.proxy.rpc.connector.BitcoinRpcConnector;
import com.softwareverde.http.server.servlet.Servlet;
import com.softwareverde.http.server.servlet.request.Request;
import com.softwareverde.http.server.servlet.response.Response;
import com.softwareverde.json.Json;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.StringUtil;

public class RpcProxyHandler implements Servlet {
    public enum Method {
        GET_BLOCK_TEMPLATE("getblocktemplate");

        protected final String _value;
        public String getValue() { return _value; }

        Method(final String value) {
            _value = value;
        }

        public static Method fromString(final String value) {
            for (final Method method : Method.values()) {
                final String methodValue = method.getValue();
                if (methodValue.equalsIgnoreCase(value)) {
                    return method;
                }
            }

            return null;
        }
    }

    protected final NodeSelector _nodeSelector;

    protected Block _assembleBlockTemplate(final Json blockTemplateJson) {
        final AddressInflater addressInflater = new AddressInflater();
        final TransactionInflater transactionInflater = new TransactionInflater();

        final MutableBlock block = new MutableBlock();
        block.setVersion(blockTemplateJson.getLong("version"));
        block.setDifficulty(Difficulty.decode(ByteArray.fromHexString(blockTemplateJson.getString("bits"))));
        block.setPreviousBlockHash(Sha256Hash.fromHexString(blockTemplateJson.getString("previousblockhash")));
        block.setTimestamp(blockTemplateJson.getLong("mintime"));
        block.setNonce(0L);

        final Transaction coinbaseTransaction;
        { // Generate the Coinbase
            final Long coinbaseAmount = blockTemplateJson.getLong("coinbasevalue");
            final Long blockHeight = blockTemplateJson.getLong("height");

            final String coinbaseMessage = "0000000000000000000000000000000000000000000000000000000000000000"; // NOTE: CoinbaseMessage must be at least 11 bytes or the transaction will not satisfy the minimum transaction size (100 bytes).
            final PrivateKey privateKey = PrivateKey.fromHexString("0000000000000000000000000000000000000000000000000000000000000001");
            final Address address = addressInflater.fromPrivateKey(privateKey);
            coinbaseTransaction = transactionInflater.createCoinbaseTransaction(blockHeight, coinbaseMessage, address, coinbaseAmount);
        }
        block.addTransaction(coinbaseTransaction);

        final Json transactionsJson = blockTemplateJson.get("transactions");
        for (int i = 0; i < transactionsJson.length(); ++i) {
            final Json transactionJson = transactionsJson.get(i);
            final Transaction transaction = transactionInflater.fromBytes(ByteArray.fromHexString(transactionJson.getString("data")));
            block.addTransaction(transaction);
        }

        return block;
    }

    protected Response _onGetBlockTemplateRequest(final RpcConfiguration bestRpcConfiguration, final Request request) {
        final List<RpcConfiguration> rpcConfigurations = _nodeSelector.getNodes();
        final BitcoinRpcConnector bestBitcoinRpcConnector = bestRpcConfiguration.getBitcoinRpcConnector();
        final Response response = bestBitcoinRpcConnector.handleRequest(request);
        final Json responseJson = Json.parse(StringUtil.bytesToString(response.getContent()));

        final String errorString = responseJson.getString("error");
        if (! Util.isBlank(errorString)) {
            Logger.debug("Received error from " + bestRpcConfiguration + ": " + errorString);
            return response;
        }

        final Json resultJson = responseJson.get("result");
        final Block blockTemplate = _assembleBlockTemplate(resultJson);

        int validCount = 0;
        final int nodeCount = rpcConfigurations.getCount();
        for (final RpcConfiguration rpcConfiguration : rpcConfigurations) {
            final BitcoinRpcConnector bitcoinRpcConnector = rpcConfiguration.getBitcoinRpcConnector();
            final Boolean isValid = bitcoinRpcConnector.validateBlockTemplate(blockTemplate);
            if (isValid) {
                validCount += 1;
            }
        }

        final int invalidCount = (nodeCount - validCount);
        if (invalidCount > 0) {
            Logger.warn("Block template considered invalid by " + invalidCount + " nodes.");
        }
        else {
            Logger.info("Block template considered valid by all nodes.");
        }

        return response;
    }

    public RpcProxyHandler(final NodeSelector nodeSelector) {
        _nodeSelector = nodeSelector;
    }

    @Override
    public Response onRequest(final Request request) {
        final Method method;
        {
            final MutableByteArray rawPostData = MutableByteArray.wrap(request.getRawPostData());
            final Json requestJson = Json.parse(StringUtil.bytesToString(rawPostData.unwrap()));
            final String methodString = requestJson.getString("method");
            method = Method.fromString(methodString);
        }

        final RpcConfiguration rpcConfiguration = _nodeSelector.selectBestNode();
        if (rpcConfiguration == null) {
            final Response errorResponse = new Response();
            errorResponse.setCode(Response.Codes.SERVER_ERROR);
            errorResponse.setContent("No viable node connection found.");
            return errorResponse;
        }

        if (method == Method.GET_BLOCK_TEMPLATE) {
            return _onGetBlockTemplateRequest(rpcConfiguration, request);
        }

        final BitcoinRpcConnector bitcoinRpcConnector = rpcConfiguration.getBitcoinRpcConnector();
        return bitcoinRpcConnector.handleRequest(request);
    }
}
