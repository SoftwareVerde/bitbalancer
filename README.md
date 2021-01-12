BitBalancer
===========

A drop-in Bitcoin Cash node reverse-proxy/load-balancer that provides increased reliability
and helps prevent accidental chain splits and orphaned blocks before hash power is applied
to a block template.

## Purpose

There is a risk that mined blocks could accidentally cause a chain split or be orphaned due
to incompatibilities between node implementations.

From a miner's perspective, even a small risk associated with these incompatibilities
can have a large impact on profitability.

This incompatibility risk increases the incentive for miners and pools to all run the same
implementation, which greatly reduces node-diversity within the Bitcoin Cash network.

This block template validation application aims to reduce the risk of miners creating an
invalid block to near-zero by validating the template block against other implementations
before any work is performed on the block template.

Benefits from completing this work include:

1. Reduce the risk of unintentionally mining a block that would cause a network split
2. Reduce the financial risk to miners of running different node implementations
3. Increase miner confidence in node-diversity
4. Alert developers of would-be incompatible blocks

## What Is It

BitBalance is a drop-in facade for Bitcoin Cash services that rely on Bitcoin Core RPC calls,
i.e. mining.

The application is intended to sit in front of multiple BCH nodes, aggregating (proxying)
requests made to them, and ensuring that any mining-related RPC calls are within consensus
of all connected nodes.

If a lack of consensus between the other nodes is found, a template matching the most peers
is used.

If a block is available that exceeds the highest ChainWork (up to `maxOrphanDepth` blocks),
then a node building upon that chain is always used.

## Supported Nodes

The BitBalancer can proxy for the following node implementations:

* Bitcoin Cash Node
* Bitcoin Unlimited
* Bitcoin Verde <sup>1</sup>
* BCHD <sup>2</sup>


> <sup>1</sup> Bitcoin Verde only supports the Bitcoin Core JSON-RPC calls required for mining.  Non-mining JSON-RPC requests will only be available if other node implementations are connected to the BitBalancer.

> <sup>2</sup> BCHD does not implement ZMQ notifications, therefore at least one other
> implementation must be connected to the BitBalancer in order to mine effectively.
> Additionally, BCHD does not support `validateblocktemplate` yet, and is not used to ensure
> consensus.

---

The BitBalancer should be able to proxy for the following node implementations, but support
is untested and unverified:

* Knuth
* Flowee The Hub

## How It Works

The BitBalancer connects to multiple node implementations, as configured.

All JSON-RPC (+ZMQ) requests are proxied to the "best" node; the "best" node is any node whose
tip has the most ChainWork--ties are decided by the order provided within the configuration.

Every `cacheTemplateDuration` seconds, the BitBalancer queries all connected nodes for a block
template, ensuring the template is valid by all node implementations.

When a new block is announced by a node, the BitBalancer immediately generates an empty
template and notifies subscribers via ZMQ.

The BitBalancer then requests new templates from each node, again ensuring the template is
valid across all implementations.

## System Requirements

* Java 11+ JRE
* 1 (or more) BCHN/BU/Verde mining nodes with JSON-RPC access


### Compile From Source

(Requires Java JDK 11+)

```
./scripts/make.sh
```


## Getting Started

### Installing Service on Debian/Ubuntu (systemd)

There is an installation script located within the bundle for Debian/Ubuntu systems running
systemd (Debian 8+).  The script will install the binaries at `/opt/bitbalancer`, and create
a user to run the service ("bitbalancer").

1. Extract the binaries to `/opt/bitbalancer`:

    a. Download the latest release from [github](https://github.com/softwareverde/bitbalancer/releases/latest) or run:

    ```
    curl -O -L $(curl -s https://api.github.com/repos/softwareverde/bitbalancer/releases/latest | sed -n 's/[ ]\{6\}"browser_download_url": "\(.*.tar.gz\)"/\1/p')
    ```

    b. Extract the files and move them to the installation directory:

   ```
   tar xzf bitbalancer-*.tar.gz
   rm bitbalancer-*.tar.gz
   sudo mv bitbalancer-* /opt/bitbalancer
   ```

2. Configure the service:

```
cd /opt/bitbalancer
vim conf/server.json
```

Add endpoints to the `nodes` array.

The available connector types are `CORE`, `VERDE`, and `BCHD`.
All nodes currently use the `CORE` connection type except Bitcoin Verde and BCHD nodes.

For more information on configuration, reference the [CONF.md](/CONF.md) readme.

3. Install the daemon:

```
cd /opt/bitbalancer/daemons/systemd
sudo ./install.sh
```

4. Start the service:

```
sudo systemctl start bitbalancer
```
