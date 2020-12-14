package com.softwareverde.guvnor.proxy;

import com.softwareverde.bitcoin.util.StringUtil;
import com.softwareverde.bitcoin.util.Util;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.guvnor.proxy.rpc.RpcConfiguration;
import com.softwareverde.http.querystring.GetParameters;
import com.softwareverde.http.server.servlet.Servlet;
import com.softwareverde.http.server.servlet.request.Request;
import com.softwareverde.http.server.servlet.response.Response;
import com.softwareverde.logging.Logger;

public class NotifyEndpoint implements Servlet {
    public interface Context {
        RpcConfiguration getBestRpcConfiguration();
        List<RpcConfiguration> getRpcConfigurations();
        void relayNotification(Notification notification);
    }

    protected final Context _context;
    protected final NotificationType _notificationType;
    protected final Integer _requiredDataLength;

    public NotifyEndpoint(final NotificationType notificationType, final Context context) {
        this(notificationType, context, null);
    }

    public NotifyEndpoint(final NotificationType notificationType, final Context context, final Integer requiredDataLength) {
        _context = context;
        _notificationType = notificationType;
        _requiredDataLength = requiredDataLength;
    }

    @Override
    public Response onRequest(final Request request) {
        final GetParameters getParameters = request.getGetParameters();
        final String hostName = getParameters.get("host");

        final ByteArray postData;
        {
            final String postDataString = StringUtil.bytesToString(request.getRawPostData());
            postData = ByteArray.fromHexString(postDataString);

            if ( (_requiredDataLength != null) && (! Util.areEqual(_requiredDataLength, postData.getByteCount())) ) {
                Logger.debug("Received invalid " + _notificationType + " notification data from: " + hostName);

                final Response errorResponse = new Response();
                errorResponse.setCode(Response.Codes.BAD_REQUEST);
                errorResponse.setContent("Invalid data length. Required: " + _requiredDataLength);
                return errorResponse;
            }
        }

        RpcConfiguration registeredRpcConfiguration = null;
        for (final RpcConfiguration rpcConfiguration : _context.getRpcConfigurations()) {
            if (Util.areEqual(hostName, rpcConfiguration.getHost())) {
                registeredRpcConfiguration = rpcConfiguration;
                break;
            }
        }
        if (registeredRpcConfiguration == null) {
            Logger.debug("Received " + _notificationType + " notification from unknown host: " + hostName);

            final Response errorResponse = new Response();
            errorResponse.setCode(Response.Codes.SERVER_ERROR);
            errorResponse.setContent("Unregistered node connection.");
            return errorResponse;
        }

        final RpcConfiguration bestRpcConfiguration = _context.getBestRpcConfiguration();
        if ( (bestRpcConfiguration != null) && Util.areEqual(bestRpcConfiguration, registeredRpcConfiguration) ) {
            // If the notification if from the highest ChainHeight node, then relay the message.
            final Notification notification = new Notification(_notificationType, postData);
            _context.relayNotification(notification);
        }

        final Response response = new Response();
        response.setCode(Response.Codes.OK);
        response.setContent("OK");
        return response;
    }
}
