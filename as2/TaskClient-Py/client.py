import sys
import time

from thrift import Thrift
from thrift.transport import TSocket
from thrift.transport import TTransport
from thrift.protocol import TBinaryProtocol
from thrift.protocol import TMultiplexedProtocol

# Generated module
from Task import Login, Search, Reports
from Task.ttypes import InvalidKeyException, ProtocolException, FetchResult, FetchState

# Connection to server setup
socket = None
muxProtocol = None
transport = None

# All active client connections to the server
clients = {}

# One global report of items
report = {}


def setupTransport(serverURL:str, port:int):
        global socket
        global muxProtocol
        global transport

        # Connect to server by TCP socket
        socket = TSocket.TSocket(serverURL, port)
        ## Use buffering
        transport = TTransport.TBufferedTransport(socket)
        ## Use a binary protocol to serialize data
        muxProtocol = TBinaryProtocol.TBinaryProtocol(transport)


def getClient(name:str):
        global clients

        if name not in clients:
                # Create new client
                ## Use a multiplexed protocol to select a service by name
                protocol = TMultiplexedProtocol.TMultiplexedProtocol(muxProtocol, name)
                if name == "Login":
                        ## Proxy object
                        clients[name] = Login.Client(protocol)
                elif name == "Search":
                       clients[name] = Search.Client(protocol)
                elif name == "Reports":
                        clients[name] = Reports.Client(protocol)
                else:
                        raise Exception(f"Internal client error: Unknown client name: {name}")

        assert(clients[name] is not None)
        return clients[name]


def login(userName:str, key:int, attempt:int=0):
        if attempt > 2:
                raise Exception(f"Login: {attempt} unsuccessful")

        client = getClient("Login")
        try:
                print(f"Login: trying to log in with <{userName}:{key}>")
                client.logIn(userName, key)
        except InvalidKeyException as e:
                print(f"Login: {e}")
                return login(userName, e.expectedKey, attempt=attempt + 1)
        except ProtocolException as e:
                print(f"Login: {e}")
                return

        print(f"Login: SUCCESS")


def logout():
        client = getClient("Login")
        try:
                print(f"Logout: trying to log out")
                client.logOut()
        except ProtocolException as e:
                print(f"Logout: {e}")
                return

        print(f"Logout: SUCCESS")


def processItem(item):
        print(f"Fetch: received item {item}")

        global report
        itemA = item.itemA
        if itemA is not None:
                if itemA.fieldA is not None:
                        fieldKey = "fieldA"
                        fieldVal = str(itemA.fieldA)
                        if fieldKey in report:
                                report[fieldKey].add(fieldVal)
                        else:
                                report[fieldKey] = { fieldVal }
                if itemA.fieldB is not None:
                        fieldKey = "fieldB"
                        fieldVal = ','.join([str(x) for x in itemA.fieldB])
                        if fieldKey in report:
                                report[fieldKey].add(fieldVal)
                        else:
                                report[fieldKey] = { fieldVal }
                if itemA.fieldC is not None:
                        fieldKey = "fieldC"
                        fieldVal = str(itemA.fieldC)
                        if fieldKey in report:
                                report[fieldKey].add(fieldVal)
                        else:
                                report[fieldKey] = { fieldVal }

        itemB = item.itemB
        if itemB is not None:
                if itemB.fieldA is not None and itemB.fieldA != "":
                        fieldKey = "fieldA"
                        fieldVal = itemB.fieldA
                        if fieldKey in report:
                                report[fieldKey].add(fieldVal)
                        else:
                                report[fieldKey] = { fieldVal }
                if itemB.fieldB is not None:
                        fieldKey = "fieldB"
                        fieldVal = ','.join(sorted(itemB.fieldB))
                        if fieldKey in report:
                                report[fieldKey].add(fieldVal)
                        else:
                                report[fieldKey] = { fieldVal }
                if itemB.fieldC is not None:
                        fieldKey = "fieldC"
                        fieldVal = ','.join(itemB.fieldC)
                        if fieldKey in report:
                                report[fieldKey].add(fieldVal)
                        else:
                                report[fieldKey] = { fieldVal }

        itemC = item.itemC
        if itemC is not None:
                if itemC.fieldA is not None:
                        fieldKey = "fieldA"
                        fieldVal = str(itemC.fieldA).lower()
                        if fieldKey in report:
                                report[fieldKey].add(fieldVal)
                        else:
                                report[fieldKey] = { fieldVal }

        itemD = item.itemD
        if itemD is not None:
                if itemD.fieldA is not None:
                        fieldKey = "fieldA"
                        fieldVal = str(itemD.fieldA)
                        if fieldKey in report:
                                report[fieldKey].add(fieldVal)
                        else:
                                report[fieldKey] = { fieldVal }

        print(f"Fetch: updated report {report}")


def searchQuery(query:str, limit:int):
        client = getClient("Search")
        try:
                print(f"Search: begin search for {query} with limit {limit}")
                searchState = client.search(query, limit)
                searchState.supportMultipleItems = True
                while True:
                        print(f"Search: fetch new with state {searchState}")
                        fetchResult = client.fetch(searchState)
                        if fetchResult.state == FetchState.PENDING:
                                print(f"Search: fetch result is PENDING retrying...")
                                time.sleep(1)
                        elif fetchResult.state == FetchState.ENDED:
                                print(f"Search: ENDED")
                                return
                        elif fetchResult.state == FetchState.ITEMS:
                                assert(fetchResult.item)
                                processItem(fetchResult.item)
                        elif fetchResult.state == FetchState.MULTIPLE_ITEMS:
                                print(f"Search: MULTIPLE_ITEMS {fetchResult}")
                                assert(fetchResult.multipleItems)
                                for item in fetchResult.multipleItems:
                                        processItem(item)
                        else:
                                print(fetchResult)
                                assert(False)
                        searchState = fetchResult.nextSearchState
                        
        except ProtocolException as e:
                print(f"Search: {query} failed with exception {e}")


def saveReport():
        global report

        client = getClient("Reports")
        try:
                print(f"Report: trying to save report {report}")
                success = client.saveReport(report)
                print(f"Report: {'SUCCESS' if success else 'FAIL'}\n")
        except ProtocolException as e:
                print(f"Report: failed with exception {e}")


def main():
        if len(sys.argv) != 3:
                print(f"usage: ./client.py <userName> <query>")
                exit(1)

        # Parse CLI arguments
        userName = sys.argv[1]
        query = sys.argv[2]

        # Setup connection
        #setupTransport("lab.d3s.mff.cuni.cz", 5001)
        setupTransport("localhost", 5000)

        global transport
        ## Open the connection
        transport.open()

        login(userName, -1)

        searchQuery(query, len(query.split(',')))
        saveReport()

        logout()

        # Close the connection
        transport.close()

main()
