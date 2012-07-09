#!/bin/bash
BASEDIR=$(dirname "$0")
chmod 600 "${BASEDIR}"/etc/ssh/ssh_host*key
chmod 700 "${BASEDIR}/sbin/sshd"
tar --owner=root -cf "${BASEDIR}/../src/res/ssh.tar" --exclude=.DS_Store -C "${BASEDIR}" .
