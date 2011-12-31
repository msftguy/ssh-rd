#include <stdio.h>

#include <vector>
#include <utility>
#include <algorithm>
#include <string>
#include <iostream>
#include <sstream>

#include <boost/program_options.hpp>
namespace po = boost::program_options;

#include "patcher.h"

//static int g_fuzzLevel = 100;
//static bool g_verbose = false;

int main(int argc, char** argv) {
	
	init_diff_byte_rating();

	// Declare the supported options.
	po::options_description desc("Allowed options");
	desc.add_options()
		("help", "produce help message")
		("diff", "produce a difference file")
		("patch", "patch the original using the difference file")
		("delta", po::value<std::string>()->required(), "difference file")
		("orig", po::value<std::string>()->required(), "original file")
		("patched", po::value<std::string>()->required(), "patched file")
		("fuzz", po::value<int>()->default_value(100), "fuzzy matching level (0-100), default=100 (disabled)")		
		("verbose", "enable extra logging")
	;

	po::variables_map vm;
	try {
		po::store(po::parse_command_line(argc, argv, desc), vm);
		po::notify(vm);
	} catch(std::exception &ex) {
		std::cerr << "Argument exception : " << ex.what() << std::endl;
		std::cout << desc << std::endl;
		return 1;
	}

	if (vm.count("help") || (vm.count("diff") + vm.count("patch") == 0)) {
		std::cout << desc << std::endl;
		return 0;
	}

	if (vm.count("fuzz")) {
		g_patcher_fuzzLevel = vm["fuzz"].as<int>();
	}
	if (vm.count("verbose")) {
		g_patcher_verbose = true;
	}

	const char* orig = vm["orig"].as<std::string>().c_str();
	const char* patched = vm["patched"].as<std::string>().c_str();
	const char* delta = vm["delta"].as<std::string>().c_str();
	
    int result = 0;
	try {
		if (vm.count("diff")) {
			diffFiles(orig, patched, delta);
		} else if (vm.count("patch")) {
			result = patchFiles(orig, patched, delta);
		}
	} catch (std::exception &ex) {
		std::cerr << "Error: " << ex.what() << std::endl;
		exit(1);
	}
	return result;
}
