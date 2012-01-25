#!/bin/sh

# remount r/w

mount /

# free space

rm /usr/local/standalone/firmware/*
rm /usr/standalone/firmware/*
mv /sbin/reboot /sbin/reboot_bak

# Fix the auto-boot

nvram auto-boot=1

# Start SSHD

/sbin/sshd

# Do the stuff original rc.boot did 

/usr/local/bin/restored_external
/usr/local/bin/restored_update
/usr/local/bin/restored
/usr/libexec/ramrod/ramrod

