package com.softwareverde.bitbalancer.proxy;

import com.softwareverde.bitbalancer.proxy.rpc.RpcConfiguration;
import com.softwareverde.bitcoin.rpc.RpcNotification;
import com.softwareverde.bitcoin.rpc.RpcNotificationType;
import com.softwareverde.bitcoin.util.Util;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.http.HttpMethod;
import com.softwareverde.http.querystring.PostParameters;
import com.softwareverde.http.server.servlet.Servlet;
import com.softwareverde.http.server.servlet.request.Request;
import com.softwareverde.http.server.servlet.response.Response;
import com.softwareverde.logging.Logger;

public class NotifyEndpoint implements Servlet {
    public interface Context {
        RpcConfiguration getBestRpcConfiguration();
        List<RpcConfiguration> getRpcConfigurations();
        void relayNotification(RpcNotification notification);
    }

    protected final Context _context;
    protected final RpcNotificationType _notificationType;
    protected final Integer _requiredDataLength;

    public NotifyEndpoint(final RpcNotificationType notificationType, final Context context) {
        this(notificationType, context, null);
    }

    public NotifyEndpoint(final RpcNotificationType notificationType, final Context context, final Integer requiredDataLength) {
        _context = context;
        _notificationType = notificationType;
        _requiredDataLength = requiredDataLength;
    }

    @Override
    public Response onRequest(final Request request) {
        if (! Util.areEqual(HttpMethod.POST, request.getMethod())) {
            final Response errorResponse = new Response();
            errorResponse.setCode(Response.Codes.BAD_REQUEST);
            errorResponse.setContent("Invalid method. Required: " + HttpMethod.POST);
            return errorResponse;
        }

        final PostParameters postParameters = request.getPostParameters();
        final String nodeHost = postParameters.get("nodeHost");
        final String nodeName = postParameters.get("nodeName");
        final String blockHash = postParameters.get("blockHash");

        final ByteArray postData;
        {
            postData = Util.coalesce(Sha256Hash.fromHexString(blockHash), new MutableByteArray(0));

            if ( (_requiredDataLength != null) && (! Util.areEqual(_requiredDataLength, postData.getByteCount())) ) {
                Logger.debug("Received invalid " + _notificationType + " notification data from: " + (nodeHost + " " + nodeName));

                final Response errorResponse = new Response();
                errorResponse.setCode(Response.Codes.BAD_REQUEST);
                errorResponse.setContent("Invalid data length for \"blockHash\". Required: " + _requiredDataLength);
                return errorResponse;
            }
        }

        RpcConfiguration registeredRpcConfiguration = null;
        for (final RpcConfiguration rpcConfiguration : _context.getRpcConfigurations()) {
            if (! nodeHost.isEmpty()) {
                if (Util.areEqual(nodeHost, rpcConfiguration.getHost())) {
                    registeredRpcConfiguration = rpcConfiguration;
                    break;
                }
            }
            else {
                if (Util.areEqual(nodeName, rpcConfiguration.getName())) {
                    registeredRpcConfiguration = rpcConfiguration;
                    break;
                }
            }
        }
        if (registeredRpcConfiguration == null) {
            Logger.debug("Received " + _notificationType + " notification from unknown host: " + (nodeHost + " " + nodeName));

            final Response errorResponse = new Response();
            errorResponse.setCode(Response.Codes.SERVER_ERROR);
            errorResponse.setContent("Unregistered node connection.");
            return errorResponse;
        }

        final RpcNotification notification = new RpcNotification(_notificationType, postData);
        _context.relayNotification(notification);

        final Response response = new Response();
        response.setCode(Response.Codes.OK);
        response.setContent("OK");
        return response;
    }
}
