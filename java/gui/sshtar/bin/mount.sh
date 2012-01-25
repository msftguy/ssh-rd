#!/bin/sh

# Script to mount the volumes..

MOUNTS=$(mount)

while read LINE
do 
    set $LINE
    if [ $3 == "/mnt1" ]
    then 
        MNT1=$1
    else 
        if [ $3 == "/mnt2" ]
        then
            MNT2=$1
        fi
    fi
done <<< "$MOUNTS"

if [ -z $MNT1 ]
then
    if [ -b /dev/disk0s1s1 ]
    then # iOS5
        echo "Mounting /dev/disk0s1s1 on /mnt1 .."
        mount_hfs /dev/disk0s1s1 /mnt1
    else
        if [ -b /dev/disk0s1 ]
        then
            echo "Checking /dev/disk0s1 .."
            fsck_hfs /dev/disk0s1
            
            echo "Mounting /dev/disk0s1 on /mnt1 .."
            mount_hfs /dev/disk0s1 /mnt1
        else
            echo "Could not mount system volume; retry later or file a bug."
        fi
    fi
else
    echo "$MNT1 already mounted on /mnt1"
fi

if [ -z $MNT2 ]
then
    if [ -b /dev/disk0s1s2 ]
    then # iOS5
        echo "Mounting /dev/disk0s1s2 on /mnt2 .."
        mount_hfs /dev/disk0s1s2 /mnt2
    else
        if [ -b /dev/disk0s2s1 ]
        then # iOS 4
            echo "Mounting /dev/disk0s2s1 on /mnt2 .."
            mount_hfs /dev/disk0s2s1 /mnt2
        else 
            if [ -b /dev/disk0s2 ]
            then # iOS3 .. maybe?
                echo "Checking /dev/disk0s2 .."
                fsck_hfs /dev/disk0s2
            
                echo "Mounting /dev/disk0s2 on /mnt2 .."
                mount_hfs /dev/disk0s2 /mnt2
            else
                echo "Could not mount user data volume; retry later or file a bug."
            fi
        fi
    fi
else
    echo "$MNT2 already mounted on /mnt2"
fi