#!/bin/bash

if [ "$EUID" -ne 0 ]; then
    echo "This script installs a daemon and must be ran as root."
    exit 1
fi

user='bitbalancer'
install_dir='/opt/bitbalancer'
service='bitbalancer'

cp *.service /etc/systemd/system/.

useradd -rs /bin/false "${user}"
chown -R "${user}":"${user}" "${install_dir}"

systemctl daemon-reload
systemctl enable bitbalancer

