#!/bin/bash

# This script can be used for nodes that do not support ZMQ and provide a blocknotify hook.
# Copy this script to the node(s) using blocknotify and configure the parameters below.

# Set to the ip/port of the guvnor server:
serverAddress='0.0.0.0'
serverPort=8332

# Set to the "name" property within the guvnor's server.json configuration for this node:
nodeName='node-00'

# (Optional)
# Set to the configured IP of the node; must match the node's host within the guvnor's server.json configuration for this node:
# nodeIp='0.0.0.0'

# Do not change.
blockHash="$@"

curl --data-urlencode "nodeName=${nodeName}" --data-urlencode "nodeHost=${nodeIp}" --data-urlencode "blockHash=${blockHash}" "http://${serverAddress}:${serverPort}/api/v1/publish/block/hash"

