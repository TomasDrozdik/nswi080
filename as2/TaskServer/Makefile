CXXFLAGS=-Igen-cpp -Wall -Wextra -std=c++17
all: server

# Generate source files from Thrift IDL
gen-cpp/Login.cpp gen-cpp/Search.cpp gen-cpp/Report.cpp gen-cpp/Task_types.cpp: ../Task-Interface/Task.thrift
	thrift --gen cpp $^

Login.o: gen-cpp/Login.cpp
	$(CXX) $(CXXFLAGS) -c $^

Search.o: gen-cpp/Search.cpp
	$(CXX) $(CXXFLAGS) -c $^

Reports.o: gen-cpp/Reports.cpp
	$(CXX) $(CXXFLAGS) -c $^

Task_types.o: gen-cpp/Task_types.cpp
	$(CXX) $(CXXFLAGS) -c $^

server.o: server.cpp gen-cpp/Login.cpp gen-cpp/Search.cpp gen-cpp/Reports.cpp gen-cpp/Task_types.cpp
	$(CXX) $(CXXFLAGS) -c $^

# Compile server from main source and generated sources
server: server.o Login.o Search.o Reports.o Task_types.o
	$(CXX) $(CXXFLAGS) -o $@ $^ -lthrift

clean:
	rm -f server *.o
	rm -rf gen-cpp


.PHONY: all clean
