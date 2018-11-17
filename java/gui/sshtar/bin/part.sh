# Execute Derebus Stuff.
mount_hfs /dev/disk0s1s1 /mnt1 
mount_hfs /dev/disk0s1s2 /mnt2
# GPTFDISK STUFF :x
DataSize="$(df -B1 | sed -n -e 's/^.*\/dev\/disk0s1s2 //p'| sed -e 's/^[ \t]*//'|sed 's/ .*//')"
GUID="$((echo -e "i\n2\nq")  |  gptfdisk /dev/rdisk0s1 | sed -n -e 's/^.*Partition unique GUID: //p')"
LastSect="$((echo -e "i\n2\nq")  | gptfdisk /dev/rdisk0s1 | sed -n -e 's/^.*Last sector: //p' | sed 's/ .*//')"
Part2LastSect=$(($LastSect-64))
NewDataSize=$(($DataSize-524288))
(echo -e "d\n2\nn\n\n$Part2LastSect\n\nc\n2\nData\nx\na\n2\n48\n49\n\nc\n2\n$GUID\ns\n4\nm\nn\n3\n\n$LastSect\n\nw\nY\n")  | gptfdisk /dev/rdisk0s1
(sync;sync;sync;)
hfs_resize /mnt2 $NewDataSize
# Install Derebus ramdisk and change the nvram
nvram boot-partition=2
dd of=/dev/rdisk0s1s3 if=/ramdiskH_beta4.dmg bs=512k count=1
nvram auto-boot=true