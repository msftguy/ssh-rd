# Set path to support running tools from /mnt1
export PATH=/usr/bin:/bin:/usr/sbin:/sbin:/mnt1/usr/bin:/mnt1/bin:/mnt1/usr/sbin:/mnt1/sbin

# and also some libs (not frameworks though :/ )
export DYLD_LIBRARY_PATH=/usr/lib:/mnt1/usr/lib

echo Use 'part.sh' to partition and install.
