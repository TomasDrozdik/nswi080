// Standard library headers
#include <algorithm>
#include <memory>
#include <iostream>
#include <string>
#include <mutex>
#include <unordered_map>

#include <cstdlib> 

// Boost library headers
#include <boost/algorithm/string/join.hpp>
#include <boost/range/adaptor/transformed.hpp>

// Thrift headers
#include <thrift/protocol/TProtocol.h>
#include <thrift/protocol/TBinaryProtocol.h>
#include <thrift/protocol/TMultiplexedProtocol.h>
#include <thrift/transport/TSocket.h>
#include <thrift/transport/TTransportUtils.h>
#include <thrift/server/TServer.h>
#include <thrift/server/TThreadedServer.h>
#include <thrift/processor/TMultiplexedProcessor.h>
#include <thrift/TProcessor.h>
#include <thrift/Thrift.h>

// Generated headers
#include "gen-cpp/Login.h"
#include "gen-cpp/Reports.h"
#include "gen-cpp/Search.h"
#include "gen-cpp/Task_types.h" 

using namespace apache::thrift;
using namespace apache::thrift::transport;
using namespace apache::thrift::protocol;
using namespace apache::thrift::server;
using namespace std;

using boost::adaptors::transformed;
using boost::algorithm::join;

// Globals
mutex logins_mux{};
unordered_map<string, int32_t> logins{};

InvalidKeyException newInvalidKeyException(int32_t invalidKey, int32_t expectedKey) {
    InvalidKeyException e{};
    e.invalidKey = invalidKey;
    e.expectedKey = expectedKey;
    return e;
}

ProtocolException newProtocolException(string message) {
    ProtocolException e{};
    e.message = message;
    return e;
}

Item newItemA(int16_t fieldA, const vector<int16_t> &fieldB, int32_t fieldC) {
    ItemA iA{};
    iA.__set_fieldA(fieldA);
    iA.__set_fieldB(fieldB);
    iA.__set_fieldC(fieldC);
    Item i{};
    i.__set_itemA(iA);
    return i;
}

Item newItemB(const string &fieldA, const set<string> &fieldB, const vector<string> &fieldC) {
    ItemB iB{};
    iB.__set_fieldA(fieldA);
    iB.__set_fieldB(fieldB);
    iB.__set_fieldC(fieldC);
    Item i{};
    i.__set_itemB(iB);
    return i;
}

Item newItemC(bool fieldA) {
    ItemC iC{};
    iC.__set_fieldA(fieldA);
    Item i;
    i.__set_itemC(iC);
    return i;
}

Item newItemD(const string &fieldA) {
    ItemD iD{};
    iD.__set_fieldA(fieldA);
    Item i;
    i.__set_itemD(iD);
    return i;
}

ostream &operator<<(ostream &os, const set<string> str_set) {
    os << '{';
    for (const auto & str : str_set) {
        os << str << ";";
    }
    os << '}';
    return os;
}

ostream &operator<<(ostream &os, const Report &report) {
    os << "Report[" << &report << "]\n";
    for (const auto & record : report) {
        os << record.first << ": " << record.second << '\n';
    }
    return os;
}

class ItemGenerator {
    int pendingTimer{0};
    size_t prevTokenPos{0};
    const string query{};
    const int32_t limit{};
    Report report;

    string nextQueryToken() {
        size_t nextCommaPos = query.find(',', prevTokenPos);
        if (nextCommaPos == string::npos) {
            nextCommaPos = query.length();
        }
        string token = query.substr(prevTokenPos, nextCommaPos - prevTokenPos);
        prevTokenPos = std::min(nextCommaPos + 1, query.length());
        return token;
    }

    void updateReport(const Item &item) {
        if (item.__isset.itemA) {
            report["fieldA"].insert(to_string(item.itemA.fieldA));
            report["fieldB"].insert(join(
                item.itemA.fieldB | transformed([](uint16_t x) { return to_string(x); }), ","));
            report["fieldC"].insert(to_string(item.itemA.fieldC));
        } else if (item.__isset.itemB) {
            report["fieldA"].insert(item.itemB.fieldA);
            report["fieldB"].insert(boost::algorithm::join(item.itemB.fieldB, ","));
            if (item.itemB.__isset.fieldC) {
                report["fieldC"].insert(boost::algorithm::join(item.itemB.fieldC, ","));
            }
        } else if (item.__isset.itemC) {
            report["fieldA"].insert(item.itemC.fieldA ? "true" : "false");
        } else if (item.__isset.itemD) {
            report["fieldA"].insert(item.itemD.fieldA);
        } else {
            assert(false && "updateReport: Unknown item type");
        }
    }

public:
    ItemGenerator(string query, int32_t limit) : query(query), limit(limit) {}

    FetchResult fetchNext(const SearchState &prevState) {
        FetchResult next{};

        // Return end
        if (prevState.countEstimate == prevState.fetchedItems || prevState.fetchedItems == limit) {
            next.__set_state(FetchState::ENDED);
            return next;
        }

        // Emulate pending state
        if ((pendingTimer++ % 4) == 0) {
            next.__set_state(FetchState::PENDING);
            next.__set_nextSearchState(prevState);
            return next;
        }

        if (prevState.supportMultipleItems) {
            next.__set_state(FetchState::MULTIPLE_ITEMS);
            next.__set_multipleItems({});
        } else {
            next.__set_state(FetchState::ITEMS);
        }

        // Parse next part of query and return appropriate Item
        bool hitEnd{false};
        size_t newlyFetchedItems{};
        for (size_t newlyFetchedItems = prevState.fetchedItems;
             newlyFetchedItems < prevState.countEstimate &&
             (next.state == FetchState::ITEMS || next.state == FetchState::MULTIPLE_ITEMS) &&
             (pendingTimer % 4) != 0;
             ++newlyFetchedItems, ++pendingTimer) {

            string queryToken = nextQueryToken();
            Item item{};
            if (queryToken == "ItemA") {
                cout << "Sending ItemA" << endl;
                item = newItemA(rand() % 16, {rand() % 16, rand() % 16}, rand() % 32);
            } else if (queryToken == "ItemB") {
                cout << "Sending ItemB" << endl;
                item = newItemB("hello", {"s1", "s2"}, {"v1", "v1"});
                updateReport(item);
            } else if (queryToken == "ItemC") {
                cout << "Sending ItemC" << endl;
                item = newItemC(rand() % 2);
            } else if (queryToken == "ItemD") {
                cout << "Sending ItemD" << endl;
                item = newItemD("D");
            } else if (queryToken == "") {
                cout << "No more query token left" << endl;
                hitEnd = true;
                break;
            } else {
                cout << "Incorrect query token " << queryToken << " throw..." << endl;
                throw newProtocolException("Incorrect query token " + queryToken);
            }

            if (prevState.supportMultipleItems) {
                next.multipleItems.push_back(item);
            } else {
                next.__set_item(item);
            }
            updateReport(item);
        }

        // If we hit end and fetch no new items then return END, otherwise there are some new items
        // and next message will have ENDED flag
        if (hitEnd && newlyFetchedItems == prevState.fetchedItems) {
            next.__set_state(FetchState::ENDED);
        }

        SearchState newState{prevState};
        newState.fetchedItems += newlyFetchedItems;
        next.__set_nextSearchState(newState);

        return next;
    }

    bool matchesReport(const Report &otherReport) {
        cout << "\n\nmatchesReport This:" << report
             << "\nmatchesReport Incomming: " << otherReport << '\n';
        return report == otherReport;
    }
};

struct Session {
    mutex mux{};
    bool loggedIn{false};
    string userName{};
    unique_ptr<ItemGenerator> itemGenerator{};
};

class LoginHandler: public LoginIf{
    shared_ptr<Session> session;

public:
    LoginHandler(shared_ptr<Session> session) : session(session) {}

    void logIn(const string& userName, const int32_t key) override {
        scoped_lock lock{logins_mux, session->mux};

        if (session->loggedIn) {
            throw newProtocolException("Error: already logged in.");
        }

        auto login = logins.find(userName);
        if (login == logins.end()) {
            auto newKey = rand();
            logins[userName] = newKey;
            throw newInvalidKeyException(key, newKey);
        } else if (login->second != key) {
            throw newInvalidKeyException(key, login->second);
        }

        cout << "Login successful: username(" << userName << ") key(" << key << ')' << endl;
        session->loggedIn = true;
        session->userName = userName;
    }

    void logOut() override {
        scoped_lock lock{logins_mux, session->mux};

        if (!session->loggedIn) {
            throw newProtocolException("Error: not logged in.");
        }

        logins.erase(session->userName);
        cout << "Logout user " << "" << endl;
        session->loggedIn = false;
    }
};

class SearchHandler: public SearchIf{
    shared_ptr<Session> session;
    
public:
    SearchHandler(shared_ptr<Session> session) : session(session) {}

    void search(SearchState& _return, const std::string& query, const int32_t limit) override {
        lock_guard<mutex> lock{session->mux};

        if (!session->loggedIn) {
            throw newProtocolException("Error search: not logged in.");
        }

        session->itemGenerator = make_unique<ItemGenerator>(query, limit);
        _return.countEstimate = count(query.begin(), query.end(), ',') + 1;
        _return.fetchedItems = 0;
    }

    void fetch(FetchResult& _return, const SearchState& state) override {
        lock_guard<mutex> lock{session->mux};

        if (!session->loggedIn) {
            throw newProtocolException("Error fetch: not logged in.");
        } else if (!session->itemGenerator) {
            throw newProtocolException("Error fetch: no search started yet.");
        }

        _return = session->itemGenerator->fetchNext(state);
    }
};

class ReportsHandler: public ReportsIf{
    shared_ptr<Session> session;

public:
    ReportsHandler(shared_ptr<Session> session) : session(session) {}

    bool saveReport(const Report& report) override {
        lock_guard<mutex> lock{session->mux};

        if (!session->loggedIn) {
            throw newProtocolException("Error fetch: not logged in.");
        } else if (!session->itemGenerator) {
            throw newProtocolException("Error fetch: no search started yet.");
        }

        return session->itemGenerator->matchesReport(report);
    }
};

// This factory creates a new handler for each conection
class PerConnectionLoginProcessorFactory: public TProcessorFactory{
public:
    // This metod is called for each connection
    virtual std::shared_ptr<TProcessor> getProcessor(const TConnectionInfo&) {
        auto session = make_shared<Session>();

        // Add the processor to a multiplexed processor
        // This allows extending this server by adding more services
        shared_ptr<TMultiplexedProcessor> muxProcessor(new TMultiplexedProcessor());

        {
            // Create login handler and its processor, register service in multiplexer
            shared_ptr<LoginHandler> handler(new LoginHandler(session));
            shared_ptr<TProcessor> processor(new LoginProcessor(handler));
            muxProcessor->registerProcessor("Login", processor);
        }

        {
            // Create search handler and its processor, register service in multiplexer
            shared_ptr<SearchHandler> handler(new SearchHandler(session));
            shared_ptr<TProcessor> processor(new SearchProcessor(handler));
            muxProcessor->registerProcessor("Search", processor);
        }

        {
            // Create reports handler and its processor, register service in multiplexer
            shared_ptr<ReportsHandler> handler(new ReportsHandler(session));
            shared_ptr<TProcessor> processor(new ReportsProcessor(handler));
            muxProcessor->registerProcessor("Reports", processor);
        }

        // Use the multiplexed processor
        return muxProcessor;
    }
};

int main(){
    // Initialize random generator with a seed
    srand(42);
    
    try{
        // Accept connections on a TCP socket
        shared_ptr<TServerTransport> serverTransport(new TServerSocket(5000));
        // Use buffering
        shared_ptr<TTransportFactory> transportFactory(new TBufferedTransportFactory());
        // Use a binary protocol to serialize data
        shared_ptr<TProtocolFactory> protocolFactory(new TBinaryProtocolFactory());
        // Use a processor factory to create a processor per connection
        shared_ptr<TProcessorFactory> processorFactory(new PerConnectionLoginProcessorFactory());

        // Start the server
        TThreadedServer server(processorFactory, serverTransport, transportFactory, protocolFactory);
        server.serve();
    }
    catch (TException& tx) {
        cout << "ERROR: " << tx.what() << endl;
    }

}
