CFLAGS=-force_cpusubtype_ALL -mmacosx-version-min=10.6 -arch i386 -arch x86_64 \
	-F/Developer/SDKs/MacOSX10.7.sdk/System/Library/Frameworks -DMUX_BUILD=1 \
	-F/Developer/SDKs/MacOSX10.7.sdk/System/Library/PrivateFrameworks 
LD_FLAGS=-framework CoreFoundation -framework MobileDevice \
	-install_name @loader_path/$(LIB) 
TOOL=fuzzy_patcher
LIB=mux_redux.jnilib

$(LIB):
	gcc -dynamiclib -o $(LIB) $(CFLAGS) $(LD_FLAGS) itmd.c

all: $(LIB)

clean:
	rm *.o $(LIB)
