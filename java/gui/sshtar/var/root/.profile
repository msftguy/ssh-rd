# Set path to support running tools from /mnt1
export PATH=/usr/bin:/bin:/usr/sbin:/sbin:/mnt1/usr/bin:/mnt1/bin:/mnt1/usr/sbin:/mnt1/sbin

# and also some libs (not frameworks though :/ )
export DYLD_LIBRARY_PATH=/usr/lib:/mnt1/usr/lib

echo Use 'mount.sh' script to mount the partitions
echo Use 'reboot_bak' to reboot
echo "Use 'device_infos' to dump EMF keys (when imaging user volume)"
