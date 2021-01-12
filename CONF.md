BitBalancer Configuration File
==============================

The BitBalancer configuration file is a JSON file used to configure the service.

The following properties are available:

1. `port`

    The Bitcoin Core JSON-RPC port exposed by the BitBalancer service.
    The default value is `8332`.

2. `cacheTemplateDuration`

    The duration (in seconds) that a block template is allowed to be cached before a new one
    is generated.

3. `nodes`

    The array of configured nodes.
    When all nodes are in consensus, the first node within the array is used fulfill RPC-JSON
    requests.
    If that node does not support the RPC-JSON request, the next node is used, etc.

    1. `name`

        The node identifier used for logging and by the `blocknotify` hook (only required if that
        node does not support ZMQ notifications).

    2. `host`

        The hostname or ip of the node.

    3. `port`

        The JSON-RPC port for the node.

    4. `maxTimeoutMs`

        The maximum duration (in milliseconds) that the service will wait for an RPC-JSON
        response.

    5. `isSecure`

        Whether the node should use SSL for RPC calls.
        This should be false except for BCHD clients configured to use SSL/TLS.

    6. `rpcUsername`

        The RPC username credential for the node, if required.

    7. `rpcPassword`

        The RPC password credential for the node, if required.

    8. `zmqPorts`

        The BitBalancer will attempt to determine if ZMQ is enabled for the node automatically
        via the `getzmqnotifications` RPC call.  However, if the node does not support the
       `getzmqnotifications` RPC call then it can be specified it manually.

    9. `connector`

        The type of connector to use for the node.  The values available are `CORE`, `VERDE`,
        and `BCHD`; all nodes except Bitcoin Verde and BCHD use `CORE`.
        Bitcoin Verde and BCHD use `VERDE` and `BCHD`, respectively.

4. `zmqPorts`

    There are four types of ZMQ notifications: block, block hash, transaction, and transaction
    hash.  This configuration object sets the ports exposed by the BitBalancer for each of
    these types of notifications.  Omitting this property or setting it to `null` disables
   ZMQ.

    1. `block`

    The ZMQ port used to broadcast block notifications.

    2. `blockHash`

    The ZMQ port used to broadcast block hash notifications.

    3. `transaction`

    The ZMQ port used to broadcast transaction notifications.

    4. `transactionHash`

    The ZMQ port used to broadcast transaction hash notifications.
