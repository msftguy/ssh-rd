BOOST=../_3rd/boost_1_48_0
BOOST_LIB_PATH=$(BOOST)/stage/lib
BOOST_LIBS=$(BOOST_LIB_PATH)/libboost_program_options.a $(BOOST_LIB_PATH)/libboost_iostreams.a
UNIVERSAL_FLAGS=-force_cpusubtype_ALL -mmacosx-version-min=10.6 -arch i386 -arch x86_64
FRAMEWORK_PATH=/Developer/SDKs/MacOSX10.7.sdk/System/Library/Frameworks
CFLAGS=-Iinclude -I.. -I../syringe/include -I../xpwn/includes -I../xpwn/dll -F$(FRAMEWORK_PATH) -I$(FRAMEWORK_PATH)/JavaVM.framework/Headers -DMUX_BUILD=1 $(UNIVERSAL_FLAGS) -fvisibility=hidden 
TOOL=fuzzy_patcher
DYLIB=jsyringeapi.jnilib
LIBS=bz2 system curl z usb-1.0 crypto stdc++
LIB_FILES=../syringe/syringe/libsyringe.a ../fuzzy_patcher/fuzzy_patcher.a ../xpwn/dmglib.a ../mux_redux/mux_redux.jnilib 
# builtin libusb not universal
LINK_FLAGS=-L/opt/local/lib/ -L. $(LD_FLAGS) 
SOURCES=jsyringeapi.c jsyringeapi2.c jsyringeapi3.c
$(DYLIB):
	gcc -c $(CFLAGS) $(SOURCES)
	libtool -dynamic -o $(DYLIB) $(LINK_FLAGS) $(patsubst %.c,%.o, $(SOURCES)) $(LIB_FILES) $(patsubst %,-l%,$(LIBS)) $(BOOST_LIBS)

otherDylib:
	cp ../mux_redux/mux_redux.jnilib .

all: $(DYLIB) otherDylib

clean:
	rm *.o *.jnilib
