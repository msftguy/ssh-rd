#!/bin/bash
BASEDIR=$(dirname "$0")
tar --owner=root -cvf "$BASEDIR/../src/res/ssh.tar" --exclude=.DS_Store -C "$BASEDIR" .
