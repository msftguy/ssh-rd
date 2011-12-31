#!/bin/bash

./b2 link=static architecture=x86 address-model=32_64 macosx-version=10.7 macosx-version-min=10.6 --stagedir=./stage --with-iostreams --with-program_options stage 
